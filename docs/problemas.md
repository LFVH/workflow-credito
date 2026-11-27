# Problem types

The REST API reports errors as [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details (`application/problem+json`). The `type` member of each problem links to one of the sections below; `detail` explains the specific occurrence and `instance` is the request path.

## proposta-nao-encontrada

- **Status:** 404 Not Found
- **When:** no proposal exists with the given id.
- **Client action:** check the id. Repeating the request will not help.

## transicao-invalida

- **Status:** 409 Conflict
- **When:** the action is not allowed in the proposal's current state, for example approving a cancelled proposal. The same request would succeed if the proposal were in another state.
- **Client action:** reload the proposal and use `transicoesPossiveis` to show what is allowed.

## proposta-desatualizada

- **Status:** 409 Conflict
- **When:** another operation changed the proposal between this request's read and write (optimistic locking).
- **Client action:** reload the proposal and decide again. Never retry blindly a decision made on stale data.

## dados-invalidos

- **Status:** 422 Unprocessable Content
- **When:** the request is well formed but its data breaks a business rule: an amount that is not greater than zero or has more than two decimal places, a term outside 1 to 120 months, a missing reason to reject or cancel, or a page size outside 1 to 100.
- **Client action:** fix the data and show the message to the user. Repeating the same request will fail again.

## Malformed requests

Invalid JSON, an id that is not a UUID, a missing request body, an unsupported method or media type return `400`, `405`, or `415` with Spring Boot's default problem type, `about:blank`.

## servico-indisponivel

- **Status:** 503 Service Unavailable, sent by the API gateway (not by the application).
- **When:** the application is down or starting, did not answer within the gateway timeout, or the gateway's circuit breaker is open after repeated failures. Here `instance` is the gateway's fallback endpoint; the `requestId` member and the `X-Request-Id` header identify the original request.
- **Client action:** retry after the number of seconds in the `Retry-After` header. A timeout does not prove that the operation did not happen: before repeating a non-idempotent action (for example, approving or cancelling a proposal), read the resource again. Quote the `X-Request-Id` when reporting the problem.
