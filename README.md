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

Because the tool returns a `Mono` the server subscribes inside the request's own chain, the caller's
token is still in the Reactor context when the gateway call needs it — no thread-local bridging.

### What a client discovers

An MCP client arriving without a token cannot sign in unless we tell it where to go:

- `GET /.well-known/oauth-protected-resource` answers the RFC 9728 document, without a token —
  it is what a client reads *because* it has none. It carries our issuer's address, no user data.
- a refused call answers `401` with `WWW-Authenticate: Bearer resource_metadata="…"`, pointing at
  that document.

Both are verified: discovery answers 200 and `/mcp` answers 401 with the challenge.

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

The protocol is streamable HTTP on `/mcp` — both are Spring AI's defaults, confirmed in
`McpServerProperties` rather than assumed.

## Three things worth knowing before changing this

**Reactive end to end, and it takes the right annotation to stay that way.** `@McpTool` methods may
return a `Mono`, which the async server subscribes itself. The older `@Tool` route cannot:
`MethodToolCallback.call()` returns a `String`, so a returned `Mono` is serialized as an object and
never subscribed — the tool "works" and answers `{}`. Spring AI runs a `@Tool` method inside
`Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`, which makes a `block()` there
legal but pointless when the annotated reactive route exists. There is no `block()` in this service.

**Do not add a `ToolCallbackProvider` bean.** The server's annotation scanner already publishes the
`@McpTool` methods of the `CalendarTools` bean. Declaring a `MethodToolCallbackProvider` as well
registers the same tool twice, the second time through the blocking path. `Registered tools: 1` in
the boot log is what tells you which route is live.

**Tool descriptions are the interface.** The model picks a tool from its description and fills the
parameters from theirs. A vague description is a bug, not a cosmetic issue.

## Known limits

- **The range is sent to the API and applied again here.** `wely-events` gained
  `from`/`to` on `/events/me/subscribed`, and this service passes them. The domain filters a second
  time on purpose: Spring ignores unknown query parameters, so an API version without the range
  would otherwise widen the answer with nothing to show it.
- **One tool.** `listMyFriends` and `searchEvents` are the natural next ones.
- **Identity has not been exercised against a live Keycloak.** The reactive route makes context
  propagation natural rather than something to bridge by hand, and the failure mode is loud
  (`MCP-TEC-003`, never an anonymous call) — but a real token has not been through it yet.
- **Audience is not validated.** The token's issuer and signature are, its `aud` is not. Set
  `spring.security.oauth2.resourceserver.jwt.audiences` once the Keycloak client has an audience
  mapper, so a token minted for another service cannot be replayed here.
- **No Sonar job in CI**, unlike the other services: this repository has no Sonar project and no
  `SONAR_TOKEN`, and a job that cannot authenticate would fail every run.
- **The CD job needs `INFRA_REPO_TOKEN`** in this repository's secrets, as the other services have.
  Without it the image is still built and pushed to GHCR; only the GitOps tag update is skipped.
- **Write tools are deliberately absent.** Creating or deleting events from an agent needs a
  confirmation design first.
