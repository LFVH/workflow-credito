# Credit Workflow

A practical study project about a credit application workflow, built to explore Java, Spring Boot, hexagonal architecture, relational persistence, testing, and messaging in a financial domain.

> **Current status:** Phases 1, 2, and 3 are complete. The application includes the domain and use cases, a GraphQL API, PostgreSQL persistence with JPA, and Flyway migrations. Phase 4 will implement the Outbox pattern and publish events with Kafka.

## Goal

The goal is to learn and practice, incrementally, how to build a backend for managing credit applications. This project is not presented as a production-ready solution; it is a learning environment for applying TDD, hexagonal architecture, GraphQL, PostgreSQL, database migrations, integration testing, and messaging.

## Project status

### Phase 1: pure domain with TDD

- `Proposta` validates the amount and term and maintains its current state.
- `StatusProposta` centralizes the allowed state transitions.
- Invalid transitions throw `TransicaoInvalidaException`.
- Rejection and cancellation require a reason.
- State changes are recorded as `MudancaDeStatus` in the proposal instance.
- Tests cover validations, allowed and invalid transitions, and final states.

### Phase 2: application and GraphQL API

- `PropostaService` implements creation, retrieval, listing, and state transitions.
- `PropostaRepository` defines the outbound persistence port.
- The GraphQL API exposes queries and mutations for proposals; GraphiQL is enabled.
- GraphQL error handling maps invalid business rules to `BAD_REQUEST`, missing proposals to `NOT_FOUND`, and concurrent modifications to a custom `CONFLITO` classification.

### Phase 3: PostgreSQL and persistence

- The JPA adapter implements `PropostaRepository` and persists proposals in PostgreSQL.
- Flyway applies SQL migrations with database constraints and indexes.
- GraphQL uses PostgreSQL persistence instead of in-memory storage.
- Integration tests verify persistence using PostgreSQL through Testcontainers; they are skipped when Docker is unavailable.

### Optimistic locking

- Two requests can read the same proposal and both decide a valid transition, for example an analyst approving it while the customer cancels it. Without protection, the last write silently overwrites the first one (a lost update).
- `Proposta` carries a `versao` (version) that is read with it. The JPA entity maps the `versao` column (Flyway migration `V3`) with `@Version`, so every update runs `UPDATE ... WHERE id = ? AND versao = ?` and increments the version. Zero affected rows means another operation changed the proposal first.
- The JPA adapter uses `saveAndFlush`, so the conflict is detected inside the adapter, and translates Spring's `ObjectOptimisticLockingFailureException` into the application's `PropostaDesatualizadaException`. The domain and the use cases never see JPA exceptions, and `PropostaService` did not change.
- GraphQL returns this error with the `CONFLITO` classification: the client should reload the proposal and decide again.
- Because the version is a wrapper type, a new proposal has a `null` version and Spring Data persists it with a single `INSERT`, instead of the `SELECT` + `INSERT` that `merge` requires for entities with assigned identifiers.
- The in-memory repository mirrors these rules (it stores copies and checks versions), so the race is reproduced deterministically in fast unit tests; the PostgreSQL behavior is covered by Testcontainers integration tests in CI.

### Keyset pagination

- `propostas(depoisDe: ID, limite: Int)` returns one page of proposals in creation order. `depoisDe` is the id of the last proposal of the previous page (the cursor); `limite` defaults to 20 and must be between 1 and 100. Both arguments are optional, so existing clients keep working, but they now receive the first 20 proposals instead of the whole table.
- Pages use keyset (seek) pagination instead of `OFFSET`: `WHERE (criada_em, id) > (SELECT criada_em, id FROM proposta WHERE id = :depoisDe) ORDER BY criada_em, id LIMIT :limite`, backed by the `(criada_em, id)` index from Flyway migration `V4`. `OFFSET` makes PostgreSQL read and discard every row before the requested page; keyset seeks the cursor in the index, so a deep page costs the same as the first one, and rows inserted between requests do not shift the pages.
- The `id` breaks ties: `criada_em` defaults to `now()`, which is the start time of the transaction, so every proposal inserted in the same transaction gets the same timestamp. A cursor based on the timestamp alone would skip or repeat proposals.
- The JPA adapter uses two native queries (first page and next page) instead of one query with `:depoisDe IS NULL`, because the PostgreSQL driver cannot infer the type of a null parameter. An unknown cursor returns an empty page.
- Use case tests run against the in-memory repository, which keeps insertion order like the immutable `criada_em`, and cover the default and maximum limits, the cursor, and unknown cursors. Testcontainers tests cover the SQL, including the timestamp tie, in CI.

### REST API alongside GraphQL

- A second inbound adapter, `adapters/in/rest`, exposes the same use cases over REST with Spring MVC. The domain, `PropostaService`, and the GraphQL adapter did not change: each adapter only translates its protocol to the same application service.
- State transitions are actions, not a `PATCH` of the status field: `POST /api/propostas/{id}/envio-para-analise`, `/pedido-de-documentos`, `/aprovacao`, `/reprovacao`, and `/cancelamento` (the last two require a `motivo`). Each action keeps its intent and can be authorized on its own.
- Creating a proposal returns `201 Created` with a `Location` header. `GET /api/propostas` uses the same keyset pagination as GraphQL, through the `depoisDe` and `limite` query parameters.
- Errors follow RFC 9457 (`application/problem+json`): `404` for a missing proposal; `409` when the action is not allowed in the current state or another operation changed the proposal first, with two distinct problem types documented in [docs/problemas.md](docs/problemas.md); `422` for well-formed but invalid data; and `400` for malformed requests, which Spring Boot reports in the same format. Spring Boot's handler runs before the application's one, so a malformed UUID is never reported as invalid data.
- Money is returned as a JSON string in plain notation (`"1500.50"`, never `1E+3`) and accepted as a string or a number, always parsed as an exact `BigDecimal`. The GraphQL endpoint reads requests with the same Jackson configuration, so GraphQL variables sent as JSON numbers also reach the `Decimal` scalar as exact decimals instead of `Double` values.
- REST adapter tests use `@WebMvcTest` and MockMvc with a Mockito mock of `PropostaService`, so they run without a database. OpenAPI documentation and Swagger UI are generated by springdoc-openapi.

### Contract aggregate

- A `Contrato` (credit contract) is generated from an approved proposal and has its own lifecycle and state machine.
- `Contrato` is a separate aggregate that references the proposal by id (`propostaId`), not by object. The JPA entity maps `proposta_id` as a plain UUID column, with no `@ManyToOne`, so loading a contract never loads a proposal, and changing a contract never changes one.
- The contract copies the approved amount and term, so what the customer signs cannot be changed by later updates elsewhere.
- `ContratoService.gerarPara` is idempotent: if the proposal already has a contract, the existing one is returned. The Kafka consumer planned for the Outbox phase relies on this, because messages can be delivered more than once. The `UNIQUE (proposta_id)` constraint in Flyway migration `V5` is the final guarantee of one contract per proposal.
- `TransicaoInvalidaException` now accepts any enum, so both state machines share it.
- The JPA adapter follows the same optimistic locking design as proposals (`@Version`, `saveAndFlush`, and translation to `ContratoDesatualizadoException`, exposed as `CONFLITO` in GraphQL).

### N+1 queries and batch loading

- GraphQL resolves `Proposta.contrato` for each proposal in a response. A naive `@SchemaMapping` resolver ran one SQL query per proposal: a page of 100 proposals cost 1 + 100 statements, visible with `logging.level.org.hibernate.SQL=DEBUG`.
- The field is now resolved with `@BatchMapping`, backed by a `DataLoader`: the contracts of the whole page are loaded with a single `SELECT ... WHERE proposta_id IN (...)`, so any page costs 2 statements. The page size is capped at 100, so the `IN` list never exceeds 100 ids.
- A GraphQL slice test fails if contracts are fetched one proposal at a time, and a Testcontainers test uses Hibernate statistics to assert that the batch lookup issues a single SQL statement.

### Phase 4: Outbox and Kafka

- Persist domain events in the same transaction as the proposal using the Outbox pattern.
- Publish Outbox events to a Kafka topic through a relay.
- Validate the flow between the transaction, event persistence, and publication.

## State machine

| Current state | Allowed next states |
| --- | --- |
| `RASCUNHO` | `EM_ANALISE`, `CANCELADA` |
| `EM_ANALISE` | `PENDENTE_DOCUMENTOS`, `APROVADA`, `REPROVADA`, `CANCELADA` |
| `PENDENTE_DOCUMENTOS` | `EM_ANALISE`, `CANCELADA` |
| `APROVADA` | None (final) |
| `REPROVADA` | None (final) |
| `CANCELADA` | None (final) |

Contract (`Contrato`), created from an approved proposal:

| Current state | Allowed next states |
| --- | --- |
| `GERADO` | `ASSINADO`, `CANCELADO` |
| `ASSINADO` | `ATIVO`, `CANCELADO` |
| `ATIVO` | `LIQUIDADO` |
| `LIQUIDADO` | None (final) |
| `CANCELADO` | None (final) |

An active contract cannot be cancelled: the money has already been disbursed, so it can only be settled (`LIQUIDADO`). Cancelling requires a reason.

## Architecture and structure

The domain core does not depend on Spring. The application depends on the `PropostaRepository` port; the adapters implement two inbound adapters, GraphQL and REST, and the JPA/PostgreSQL outbound adapter. Flyway manages schema evolution. The Outbox and Kafka relay are the next steps for the project.

```text
src/main/java/br/com/luiz/workflow/
|-- domain/
|   |-- Proposta.java                    # Aggregate entity and business rules
|   |-- StatusProposta.java              # States and allowed transitions
|   |-- Contrato.java                    # Contract aggregate, generated from an approved proposal
|   |-- StatusContrato.java              # Contract states and allowed transitions
|   |-- MudancaDeStatus.java             # State change record
|   `-- TransicaoInvalidaException.java
|-- application/
|   |-- PropostaService.java             # Use cases
|   |-- ContratoService.java             # Contract use cases (idempotent generation)
|   |-- PropostaNaoEncontradaException.java
|   |-- port/out/PropostaRepository.java  # Persistence port
|   `-- port/out/ContratoRepository.java  # Contract persistence port
|-- adapters/
|   |-- in/graphql/                      # GraphQL API and payloads
|   |-- in/rest/                         # REST API, request/response records, RFC 9457 errors
|   `-- out/persistence/                 # JPA adapter and entities
|-- config/BeansConfig.java              # Dependency wiring
`-- WorkflowApplication.java

src/main/resources/
|-- graphql/schema.graphqls              # API contract
|-- db/migration/                        # Flyway migrations
`-- application.properties               # GraphiQL and PostgreSQL connection

src/test/java/br/com/luiz/workflow/
|-- domain/                              # Business rule and transition tests
|-- application/                         # Use case tests
|-- adapters/in/rest/                    # REST adapter tests with MockMvc
`-- adapters/out/persistence/            # JPA/PostgreSQL integration tests
```

## Requirements

- Java 21
- Maven 3.6.3 or later
- PostgreSQL to run the application (`localhost:5432` by default, database `workflow_db`)

The port and credentials can be configured through the `DB_PORT`, `DB_USER` and `DB_PASSWORD` environment variables; the port defaults to `5432`, and the user and password default to `postgres`. Flyway migrations are applied when the application starts.

## Run

From the project root:

```bash
mvn spring-boot:run
```

Once the application is running, open:

- GraphiQL: `http://localhost:8080/graphiql`
- GraphQL endpoint: `http://localhost:8080/graphql`
- REST API: `http://localhost:8080/api/propostas`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

## GraphQL operations

Create a proposal:

```graphql
mutation {
  criarProposta(valor: 5000.00, prazoMeses: 12) {
    id
    valor
    prazoMeses
    status
    transicoesPossiveis
  }
}
```

List proposals (one page at a time, in creation order):

```graphql
query {
  propostas(limite: 20) {
    id
    valor
    status
    motivo
    transicoesPossiveis
  }
}
```

To fetch the next page, pass the `id` of the last proposal you received as `depoisDe`, for example `propostas(depoisDe: "00000000-0000-0000-0000-000000000000", limite: 20)`. Both arguments are optional: `limite` defaults to 20 and must be between 1 and 100. An empty list means there are no more proposals.

Submit a proposal for review (replace the UUID):

```graphql
mutation {
  enviarParaAnalise(id: "00000000-0000-0000-0000-000000000000") {
    id
    status
  }
}
```

Cancel a proposal (a reason is required):

```graphql
mutation {
  cancelarProposta(id: "00000000-0000-0000-0000-000000000000", motivo: "Customer withdrew the request") {
    id
    status
    motivo
  }
}
```

Other available mutations are `pedirDocumentos`, `aprovarProposta`, and `reprovarProposta`, which also requires `motivo`. Transitions follow the domain state machine, so requests that are invalid for the current state return a business rule error.

Generate the contract of an approved proposal (idempotent: calling it again returns the same contract):

```graphql
mutation {
  gerarContrato(propostaId: "00000000-0000-0000-0000-000000000000") {
    id
    status
    valor
    transicoesPossiveis
  }
}
```

List proposals with their contracts (contracts are batch-loaded with one SQL query per page):

```graphql
query {
  propostas(limite: 20) {
    id
    status
    contrato {
      status
    }
  }
}
```

Other contract mutations are `assinarContrato`, `ativarContrato`, `liquidarContrato`, and `cancelarContrato`, which requires `motivo`.

## REST operations

Create a proposal. The amount is sent and returned as a string:

```bash
curl -i -X POST http://localhost:8080/api/propostas \
  -H "Content-Type: application/json" \
  -d '{"valor": "1500.50", "prazoMeses": 12}'
```

The response is `201 Created`, with a `Location` header that points to the new proposal.

Submit it for review, then reject it with a reason (replace the UUID):

```bash
curl -X POST http://localhost:8080/api/propostas/00000000-0000-0000-0000-000000000000/envio-para-analise
curl -X POST http://localhost:8080/api/propostas/00000000-0000-0000-0000-000000000000/reprovacao \
  -H "Content-Type: application/json" \
  -d '{"motivo": "Insufficient income"}'
```

| Method and path | Use case |
| --- | --- |
| `POST /api/propostas` | Create a proposal |
| `GET /api/propostas/{id}` | Get a proposal |
| `GET /api/propostas?depoisDe={id}&limite={n}` | List proposals, one page at a time |
| `POST /api/propostas/{id}/envio-para-analise` | Submit for review |
| `POST /api/propostas/{id}/pedido-de-documentos` | Request documents |
| `POST /api/propostas/{id}/aprovacao` | Approve |
| `POST /api/propostas/{id}/reprovacao` | Reject (body: `motivo`) |
| `POST /api/propostas/{id}/cancelamento` | Cancel (body: `motivo`) |

## Tests

Run the test suite:

```bash
mvn test
```

Persistence integration tests use Testcontainers. In environments without Docker, this test class is skipped by its configuration; domain and application tests remain available.

REST adapter tests (`PropostaRestControllerTest`) use `@WebMvcTest`, MockMvc, and a Mockito mock of `PropostaService`. They check HTTP status codes, the `Location` header, money serialized as text, and RFC 9457 problem details, and they need neither a database nor Docker.

## Next steps

1. Implement the Outbox to store events in the same transaction that updates a proposal.
2. Create the relay that publishes persisted events to Kafka.
3. Test the flow and document the Outbox pattern's design decisions and guarantees.