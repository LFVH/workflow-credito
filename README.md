# Workflow de Crédito

Serviço de propostas de crédito em evolução para um serviço de contratos. O projeto usa Java, Spring Boot e GraphQL para expor as operações de proposta, com regras de negócio concentradas no domínio.

> **Estado atual:** domínio e máquina de estados implementados com testes; casos de uso, porta de repositório e adaptador em memória integrados ao Spring Boot e GraphQL. PostgreSQL, contratos e testes com Testcontainers ainda fazem parte do objetivo, mas não estão implementados nesta versão.

## Objetivo

Construir um serviço de contratos com propostas e contratos, fluxo de estados explícito e arquitetura hexagonal, desenvolvendo as regras com TDD e validando a persistência com PostgreSQL e Testcontainers.

## Estado do projeto

### Fase 1: domínio puro com TDD

- `Proposta` valida valor e prazo e mantém seu estado atual.
- `StatusProposta` centraliza a tabela de transições permitidas.
- Tentativas de transição inválidas lançam `TransicaoInvalidaException`.
- Reprovação exige um motivo.
- Mudanças de estado são registradas como `MudancaDeStatus` na instância da proposta.
- Testes cobrem validações, transições permitidas e inválidas e estados finais.

### Fase 2: aplicação e API

- `PropostaService` implementa criação, consulta, listagem e transições.
- `PropostaRepository` define a porta de saída para persistência.
- `PropostaRepositoryEmMemoria` implementa essa porta com armazenamento em memória.
- Spring Boot configura o serviço e o adaptador em memória.
- A API GraphQL expõe consultas e mutações para propostas; GraphiQL está habilitado.
- Tratamento GraphQL converte regras inválidas em `BAD_REQUEST` e propostas inexistentes em `NOT_FOUND`.

### Ainda por implementar

- Adaptador de persistência PostgreSQL, mapeamento e configuração de banco.
- Testes de integração de persistência com Testcontainers.
- Modelo e fluxo de contratos, além do ciclo de vida atualmente coberto para propostas.
- Evolução da arquitetura hexagonal com os adaptadores necessários para esses novos recursos.

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

O núcleo de domínio não depende do Spring. A aplicação depende da porta `PropostaRepository`, enquanto os adaptadores implementam entrada GraphQL e saída em memória. Isso estabelece as fronteiras principais da arquitetura hexagonal; a persistência PostgreSQL ainda não está conectada.

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
|   `-- out/memory/                      # Repositório em memória
|-- config/BeansConfig.java              # Montagem das dependências
`-- WorkflowApplication.java

src/main/resources/
|-- graphql/schema.graphqls              # Contrato da API
`-- application.properties               # GraphiQL habilitado

src/test/java/br/com/luiz/workflow/
|-- domain/                              # Testes de regras e transições
`-- application/                         # Testes dos casos de uso
```

## Requisitos

- Java 21
- Maven 3.6.3 ou superior

Não é necessário iniciar PostgreSQL na versão atual: o repositório configurado é em memória. Os dados são perdidos quando a aplicação termina.

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

Na última validação da documentação, a suíte executou **57 testes sem falhas**: testes de domínio e testes dos casos de uso. Ainda não há testes de integração com PostgreSQL/Testcontainers.

## Histórico de implementação

Resumo dos commits existentes, em ordem cronológica:

| Commit | Entrega |
| --- | --- |
| `1373a17` `estrutura inicial` | Estrutura Maven e `.gitignore`. |
| `fb64b29` `working pom` | Ajustes iniciais do `pom.xml`. |
| `e05885b` `StatusProposta com tabela de transições` | Estados e destinos permitidos, acompanhados de testes. |
| `287286a` `exceção de transição inválida e evento MudancaDeStatus` | Exceção de transição e registro de mudança de estado. |
| `c92103a` `Proposta com validações, transições e eventos` | Entidade `Proposta`, validações e regras de transição. |
| `7f1e0eb` `nova bateria de tests cobrindo todos os casos de transicao de status de proposta` | Ampliação dos testes da máquina de estados. |
| `a18acd2` `casos de uso de proposta com repositório em memória` | Serviço de aplicação, porta de repositório, adaptador em memória e testes de serviço. |
| `bad6f53` `Spring Boot, GraphQL e GraphiQL com repositório em memória` | API GraphQL, schema, configuração Spring e GraphiQL. |

## Próximos passos

1. Criar o adaptador PostgreSQL atrás de `PropostaRepository`, sem mover regras de negócio para a infraestrutura.
2. Adicionar testes de integração com Testcontainers para validar persistência e consultas reais.
3. Definir o domínio de `Contrato` e como a aprovação de uma proposta se relaciona à criação do contrato.
4. Decidir se o histórico de mudanças de estado deve continuar sendo um registro em memória ou passar a ter persistência própria.