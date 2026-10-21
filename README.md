# wely-mcp

A **Model Context Protocol** server that exposes the Wely calendar to LLM agents.

It is a client of the public API, exactly like `wely-web` and `wely-ios`: it owns no data, holds no
business rule, and talks to `wely-gateway` over HTTP. An agent connects to one MCP endpoint and gets
the calendar as a set of tools.

```
agent (Claude, …)  ──MCP/HTTP──▶  wely-mcp :8088  ──REST──▶  wely-gateway :8081  ──▶  wely-events
```

## Why a service of its own

MCP is an inbound adapter, the same kind of thing as a `@RestController`. Putting it in each business
service would give every service a second audience to serve and would force an agent to connect to
four servers. Here the whole system is one tool catalog, the business services are untouched, and the
experiment can be deleted by deleting one repository.

## Identity

**No tool takes a user id.** The caller is whoever the request's JWT says, and nothing else:

- this service validates the token as every other service does (`jwk-set-uri` inside the cluster,
  `issuer-uri` on the public URL);
- `BearerTokenRelay` puts that same token back on the call to the gateway, so the calendar answers
  for the caller and this service needs no identity of its own;
- with no token in context the exchange **fails** rather than calling the gateway anonymously.

A tool such as `listMyEvents(userId, …)` would be an IDOR, because the model fills the parameters:
it could ask for anyone's calendar. The absence of that parameter is the security control.

## Tools

| tool | parameters | what it answers |
|---|---|---|
| `listMyEvents` | `from`, `to` — ISO-8601 instants, `to` exclusive | the caller's events overlapping the range, soonest first, at most 50 |

## Structure

The hexagonal layout of the other services, with MCP as one more inbound adapter:

```
com.calendar.mcp/
├── domain/
│   ├── models/EventSummary            what an agent needs to discuss an event
│   ├── ports/EventCatalog             what the domain needs from the outside
│   └── services/MyEventsService       the range rule, soonest first, capped — a POJO
├── application/
│   ├── mcp/CalendarTools              @Tool methods: parse, delegate, format
│   ├── dtos/EventSummaryResponse      what the model receives
│   └── mappers/                       MapStruct, domain → tool answer
├── infrastructure/http/
│   ├── GatewayEventCatalog            the port, over the gateway's REST API
│   ├── BearerTokenRelay               carries the caller's token
│   └── dtos, mappers/                 the slice of the API this client reads
└── configuration/                     beans, security, gateway client
```

## Running it

```bash
./mvnw test                                        # 34 unit tests, no Docker needed
GATEWAY_URL=http://localhost:8081 \
JWK_SET_URI=http://localhost:8080/realms/wely-realm/protocol/openid-connect/certs \
ISSUER_URI=http://localhost:8080/realms/wely-realm \
  ./mvnw spring-boot:run
```

The log line to look for is `Registered tools: 1`. A `@Tool` method that is not reachable from the
`ToolCallbackProvider` bean is invisible to clients, and this line is how you notice.

Declaring it in a client, once the endpoint is reachable:

```bash
claude mcp add --transport http wely-calendar http://localhost:8088/mcp
```

## Two things worth knowing before changing this

**A `@Tool` method is blocking.** `MethodToolCallback.call()` returns a `String`; a returned `Mono`
would be serialized as an object, never subscribed. Spring AI runs the method inside
`Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`, so the single `block()` in
`CalendarTools` is on a thread meant for it — and everything underneath stays reactive. This is the
one place in the project where blocking is correct.

**Tool descriptions are the interface.** The model picks a tool from its description and fills the
parameters from theirs. A vague description is a bug, not a cosmetic issue.

## Known limits

- **The date range is applied in memory.** The calendar API has no range query, so this service reads
  `/events/me/subscribed` and filters. It is correct but wasteful; the fix belongs in `wely-events`,
  as a range query in the repository.
- **One tool.** `listMyFriends` and `searchEvents` are the natural next ones.
- **Identity propagation is verified by unit tests, not end to end.** `CalendarTools` reads the
  caller from `SecurityContextHolder` and writes it back into the Reactor context. Whether the HTTP
  request's security context reaches the tool invocation at runtime depends on Reactor context
  propagation, and that has not been exercised against a live Keycloak yet. The failure mode is
  loud — `MCP-TEC-003`, no anonymous call — but it has to be confirmed before anyone relies on it.
- **No CI, no Dockerfile, no GitOps manifests yet.** Running it locally is all that is wired.
- **Write tools are deliberately absent.** Creating or deleting events from an agent needs a
  confirmation design first.
