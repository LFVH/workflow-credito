# Workflow de Crédito

Projeto de estudo prático de uma esteira de crédito, desenvolvido para exercitar Java, Spring Boot, arquitetura hexagonal, persistência relacional, testes e mensageria em um domínio financeiro.

> **Estado atual:** fases 1, 2 e 3 concluídas. A aplicação já tem domínio e casos de uso, API GraphQL, persistência PostgreSQL com JPA e migrations Flyway. A fase 4 será a implementação do padrão Outbox e publicação de eventos com Kafka.

## Objetivo

O objetivo é aprender e praticar, de forma incremental, a construção de um backend para gerenciar propostas de crédito. O projeto não é apresentado como solução pronta para uso em produção: serve como laboratório para aplicar TDD, arquitetura hexagonal, GraphQL, PostgreSQL, migrations, testes de integração e mensageria.

## Estado do projeto

### Fase 1: domínio puro com TDD

- `Proposta` valida valor e prazo e mantém seu estado atual.
- `StatusProposta` centraliza a tabela de transições permitidas.
- Tentativas de transição inválidas lançam `TransicaoInvalidaException`.
- Reprovação exige um motivo.
- Mudanças de estado são registradas como `MudancaDeStatus` na instância da proposta.
- Testes cobrem validações, transições permitidas e inválidas e estados finais.

### Fase 2: aplicação e API GraphQL

- `PropostaService` implementa criação, consulta, listagem e transições.
- `PropostaRepository` define a porta de saída para persistência.
- A API GraphQL expõe consultas e mutações para propostas; GraphiQL está habilitado.
- Tratamento GraphQL converte regras inválidas em `BAD_REQUEST` e propostas inexistentes em `NOT_FOUND`.

### Fase 3: PostgreSQL e persistência

- Adaptador JPA implementa a porta `PropostaRepository` e persiste propostas no PostgreSQL.
- Flyway aplica migrations SQL com constraints e índices do banco.
- GraphQL trabalha com a persistência PostgreSQL, em vez do armazenamento em memória.
- Testes de integração verificam a persistência usando PostgreSQL via Testcontainers; eles são ignorados quando Docker não está disponível.

### Fase 4: Outbox e Kafka

- Persistir os eventos de domínio na mesma transação da proposta usando o padrão Outbox.
- Publicar os eventos da Outbox em um tópico Kafka por meio de um relay.
- Validar o fluxo entre transação, persistência do evento e publicação.

O modelo de `Contrato` e outras extensões do domínio podem ser explorados em etapas posteriores.

## Máquina de estados

| Estado atual | Próximos estados permitidos |
| --- | --- |
| `RASCUNHO` | `EM_ANALISE`, `CANCELADA` |
| `EM_ANALISE` | `PENDENTE_DOCUMENTOS`, `APROVADA`, `REPROVADA`, `CANCELADA` |
| `PENDENTE_DOCUMENTOS` | `EM_ANALISE`, `CANCELADA` |
| `APROVADA` | Nenhum (final) |
| `REPROVADA` | Nenhum (final) |
| `CANCELADA` | Nenhum (final) |

## Arquitetura e estrutura

O núcleo de domínio não depende do Spring. A aplicação depende da porta `PropostaRepository`; os adaptadores implementam a entrada GraphQL e a saída JPA/PostgreSQL. O Flyway gerencia a evolução do schema. A Outbox e o relay Kafka são o próximo passo do projeto.

```text
src/main/java/br/com/luiz/workflow/
|-- domain/
|   |-- Proposta.java                    # Entidade e regras do agregado
|   |-- StatusProposta.java              # Estados e transições permitidas
|   |-- MudancaDeStatus.java             # Registro de mudança de estado
|   `-- TransicaoInvalidaException.java
|-- application/
|   |-- PropostaService.java             # Casos de uso
|   |-- PropostaNaoEncontradaException.java
|   `-- port/out/PropostaRepository.java  # Porta de persistência
|-- adapters/
|   |-- in/graphql/                      # API GraphQL e payloads
|   `-- out/persistence/                 # Adaptador JPA e entidades
|-- config/BeansConfig.java              # Montagem das dependências
`-- WorkflowApplication.java

src/main/resources/
|-- graphql/schema.graphqls              # Contrato da API
|-- db/migration/                        # Migrations Flyway
`-- application.properties               # GraphiQL e conexão PostgreSQL

src/test/java/br/com/luiz/workflow/
|-- domain/                              # Testes de regras e transições
|-- application/                         # Testes dos casos de uso
`-- adapters/out/persistence/            # Testes de integração JPA/PostgreSQL
```

## Requisitos

- Java 21
- Maven 3.6.3 ou superior
- PostgreSQL para executar a aplicação (configurado em `localhost:5433`, banco `workflow_db`)

As credenciais podem ser configuradas pelas variáveis `DB_USER` e `DB_PASSWORD`; os valores padrão são `postgres` para ambas. As migrations do Flyway são aplicadas ao iniciar a aplicação.

## Executar

Na raiz do projeto:

```bash
mvn spring-boot:run
```

Com a aplicação iniciada, acesse:

- GraphiQL: `http://localhost:8080/graphiql`
- Endpoint GraphQL: `http://localhost:8080/graphql`

## Operações GraphQL

Criar uma proposta:

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

Consultar propostas:

```graphql
query {
  propostas {
    id
    valor
    status
    motivoReprovacao
    transicoesPossiveis
  }
}
```

Enviar uma proposta para análise (substitua o UUID):

```graphql
mutation {
  enviarParaAnalise(id: "00000000-0000-0000-0000-000000000000") {
    id
    status
  }
}
```

As outras mutações disponíveis são `pedirDocumentos`, `aprovarProposta`, `reprovarProposta` (recebe também `motivo`) e `cancelarProposta`. As transições seguem a tabela do domínio, então chamadas incompatíveis com o estado atual retornam erro de regra de negócio.

## Testes

Executar a suíte:

```bash
mvn test
```

Os testes de integração da persistência usam Testcontainers. Em ambientes sem Docker, essa classe é ignorada pela configuração do teste; os testes de domínio e aplicação continuam disponíveis.

## Próximos passos

1. Implementar a Outbox para gravar eventos na mesma transação que altera a proposta.
2. Criar o relay que publica os eventos persistidos em Kafka.
3. Testar o fluxo e documentar as decisões e garantias do padrão Outbox.