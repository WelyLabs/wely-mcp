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
| `listMyEvents` | `from`, `to` — ISO-8601 instants, `to` exclusive | the caller's events overlapping the range, soonest first, at most 50, each with the friends going |
| `createEvent` | `title`, `from`, `to?`, `location`, `allDay`, `description?` | creates an event on the caller's calendar and answers it as stored |
| `listMyFriends` | — | the caller's accepted friends, closest first, each with the handle to invite them by |
| `inviteToEvent` | `eventId`, `friends` (handles) | invites them, one outcome per friend |
| `joinEvent` | `eventId` | joins it, or asks the organizer when a surprise requires it |
| `listJoinRequests` | `eventId` | who asked to join an event the caller organizes |
| `answerJoinRequest` | `eventId`, `friend`, `accept` | accepts or declines one asker |

The three write/read tools chain: `listMyFriends` → `createEvent` → `inviteToEvent`, the event id
coming from the second and the handles from the first.

`createEvent` is the only tool that writes. Three decisions shape it:

- **Visibility is not a parameter.** The calendar defaults to FRIENDS, and a model should not be
  deciding a privacy setting from a sentence.
- **All-day events are normalised** to whole UTC days (00:00Z to 00:00Z the morning after) before
  the API sees them, so a model cannot produce a half-day "all-day" event or one off by a day.
- **Its annotations say what it is**: not read-only, not idempotent, which is what lets a client ask
  its user before running it. Calling it twice makes two events, and the description says so.

## Structure

The hexagonal layout of the other services, with MCP as one more inbound adapter:

```
com.calendar.mcp/
├── domain/
│   ├── models/EventSummary, Attendee  what an agent needs to discuss an event
│   ├── ports/EventCatalog             what the domain needs from the outside
│   ├── ports/UserDirectory            names, which the calendar never returns itself
│   └── services/MyEventsService       the range rule, soonest first, capped — a POJO
├── application/
│   ├── mcp/CalendarTools              @Tool methods: parse, delegate, format
│   ├── dtos/EventSummaryResponse      what the model receives
│   └── mappers/                       MapStruct, domain → tool answer
├── infrastructure/http/
│   ├── GatewayEventCatalog            the port, over the gateway's REST API
│   ├── GatewayUserDirectory           names a friend through wely-users
│   ├── BearerTokenRelay               carries the caller's token
│   └── dtos, mappers/                 the slice of the API this client reads
└── configuration/                     beans, security, gateway client
```

## Running it

```bash
./mvnw test                                        # 34 unit tests, no Docker needed
GATEWAY_URL=http://localhost:8081 \
MCP_PUBLIC_URL=http://localhost:8088 \
KEYCLOAK_INTERNAL_JWK_SET_URI=http://localhost:8080/realms/wely-local/protocol/openid-connect/certs \
KEYCLOAK_ISSUER_URI=http://localhost:8080/realms/wely-local \
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
- **One tool.** `listMyBirthdays` (the calendar keeps birthdays on a separate endpoint, excluded
  from `/me/subscribed`) and `searchEvents` are the natural next ones.
- **Handles, never user ids.** A friend travels as `userName#hashtag`, and `inviteToEvent` resolves
  it against the caller's own friends before sending anything — so a model cannot invite someone
  they are not friends with, because it has no way to name them. Inviting is also not participating:
  the guest still has to accept.
- **Naming a friend costs one call each.** `wely-users` has no batch profile endpoint, so names are
  resolved one id at a time, four at a time, after deduplicating across the answer and after the
  caps — 50 events, 100 friends. A friend the caller may not read (4xx) stays unnamed; a directory
  that is **down** (5xx) fails the answer rather than passing for "no name".
- **Identity has not been exercised against a live Keycloak.** The reactive route makes context
  propagation natural rather than something to bridge by hand, and the failure mode is loud
  (`MCP-TEC-003`, never an anonymous call) — but a real token has not been through it yet.
- **Audience is not validated yet.** The property is in place but empty: a real token carries **no
  `aud` claim at all** (checked 2026-10-23), even though wely-identity's realm export declares an
  audience mapper on `business-scope` — the live dev realm has drifted from the export. Enabling
  validation before the claim exists would answer 401 to every call. Order: fix the realm, watch the
  claim appear in a fresh token, then set `JWT_AUDIENCES`.
- **No Testcontainers**, but `McpApplicationTest` boots the whole context and speaks MCP to it over
  the deployed filter chain — which is what the 404 on `/mcp` needed, and what caught an ambiguous
  bean the unit tests could not see.
- **No Sonar job in CI**, unlike the other services: this repository has no Sonar project and no
  `SONAR_TOKEN`, and a job that cannot authenticate would fail every run.
- **The CD job needs `INFRA_REPO_TOKEN`** in this repository's secrets, as the other services have.
  Without it the image is still built and pushed to GHCR; only the GitOps tag update is skipped.
- **Only one write, and nothing destructive.** There is no tool to edit, cancel or delete an event:
  those need a confirmation design of their own, and getting one wrong costs more than a duplicate.
- **`createEvent` covers the plain case only.** No date poll (`candidateSlots`), no community, no
  surprise, no map coordinates — each is a second creation mode with its own validation, and a wide
  tool schema derails a model more often than it helps.
