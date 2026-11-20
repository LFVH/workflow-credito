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

### Phase 4: Outbox and Kafka

- Persist domain events in the same transaction as the proposal using the Outbox pattern.
- Publish Outbox events to a Kafka topic through a relay.
- Validate the flow between the transaction, event persistence, and publication.

The `Contrato` model and other domain extensions may be explored in later stages.

## State machine

| Current state | Allowed next states |
| --- | --- |
| `RASCUNHO` | `EM_ANALISE`, `CANCELADA` |
| `EM_ANALISE` | `PENDENTE_DOCUMENTOS`, `APROVADA`, `REPROVADA`, `CANCELADA` |
| `PENDENTE_DOCUMENTOS` | `EM_ANALISE`, `CANCELADA` |
| `APROVADA` | None (final) |
| `REPROVADA` | None (final) |
| `CANCELADA` | None (final) |

## Architecture and structure

The domain core does not depend on Spring. The application depends on the `PropostaRepository` port; the adapters implement the GraphQL inbound adapter and the JPA/PostgreSQL outbound adapter. Flyway manages schema evolution. The Outbox and Kafka relay are the next steps for the project.

```text
src/main/java/br/com/luiz/workflow/
|-- domain/
|   |-- Proposta.java                    # Aggregate entity and business rules
|   |-- StatusProposta.java              # States and allowed transitions
|   |-- MudancaDeStatus.java             # State change record
|   `-- TransicaoInvalidaException.java
|-- application/
|   |-- PropostaService.java             # Use cases
|   |-- PropostaNaoEncontradaException.java
|   `-- port/out/PropostaRepository.java  # Persistence port
|-- adapters/
|   |-- in/graphql/                      # GraphQL API and payloads
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
`-- adapters/out/persistence/            # JPA/PostgreSQL integration tests
```

## Requirements

- Java 21
- Maven 3.6.3 or later
- PostgreSQL to run the application (configured at `localhost:5433`, database `workflow_db`)

Credentials can be configured through the `DB_USER` and `DB_PASSWORD` environment variables; both default to `postgres`. Flyway migrations are applied when the application starts.

## Run

From the project root:

```bash
mvn spring-boot:run
```

Once the application is running, open:

- GraphiQL: `http://localhost:8080/graphiql`
- GraphQL endpoint: `http://localhost:8080/graphql`

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

## Tests

Run the test suite:

```bash
mvn test
```

Persistence integration tests use Testcontainers. In environments without Docker, this test class is skipped by its configuration; domain and application tests remain available.

## Next steps

1. Implement the Outbox to store events in the same transaction that updates a proposal.
2. Create the relay that publishes persisted events to Kafka.
3. Test the flow and document the Outbox pattern's design decisions and guarantees.