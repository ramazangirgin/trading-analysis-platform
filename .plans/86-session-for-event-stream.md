# Plan: Require a session for the live analysis event stream

- **Issue**: #86 (Auth: require a session for the live analysis event stream)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The live event stream of a run (`GET /api/analyses/{id}/events`, SSE) is tied to the session that
opened it. Without a session it answers 401, as every other `/api/**` route already does. A stream
that is open when its session ends (logout, idle timeout, or the user's password changed in another
session) is closed by the server. The run page notices the closed stream and goes to the login page
once, instead of showing a stream that never comes back. A logged-in user sees live events exactly
as before.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| backend (`artifact/backend`, `bff`) | `backend-java-package-structure.md`: the BFF `api` module holds controllers, delegate interfaces and DTOs only, no logic; delegate implementations and their helpers go in `bff.delegate.impl`, security config, filters and handlers in `bff.delegate.impl.config`. Package-private by default (Visibility). `backend-java-checkstyle.md` / `backend-java-formatting.md`: suppressions in place with `@SuppressWarnings("checkstyle:X")` and a reason. |
| frontend (`artifact/frontend`) | `frontend-folder-structure.md`: the composable stays in `features/analysis/composables/`, its backend call goes through `features/analysis/api.ts` (only `api.ts` imports the HTTP client), the 401 → login page handling stays in `app/main.ts` (`onSessionEnded`). Tests next to the module: `<module>.spec.ts` (Vitest). |
| docs | Root `README.md`, section "Login". |

## Design

**What exists.** `SecurityConfig` (`bff.delegate.impl.config`) already requires a session on
`/api/**`, so the stream answers 401 `unauthorized` without one. There is no test of it yet. Sessions
are tracked in the `SessionRegistry`, and `HttpSessionEventPublisher` publishes a
`SessionDestroyedEvent` when a session is invalidated (logout, `me` of a disabled user) or times out.
`AuthApiDelegateImpl.changePassword` expires the user's other sessions with
`SessionInformation.expireNow()`. That only marks them in the registry: the session is invalidated
on its next request, which an open stream never makes. `AnalysesApiDelegateImpl.streamEvents`
creates an `SseEmitter` with no timeout. Today it ends only when the run ends (`end` event), on a
client disconnect or on an error.

**Backend.**

- `AnalysesApiController.streamEvents` passes the `HttpServletRequest` to the delegate, like
  `AuthApiController` does (`streamEvents(String id, long afterSeq, HttpServletRequest http)`). The
  controller stays logic-free.
- A new package-private `SessionEventStreams` component in `bff.delegate.impl` keeps the open
  emitters per session ID (a concurrent map of sets):
  - `register(String sessionId, SseEmitter emitter)` adds it, and removes it again on the
    emitter's completion, timeout or error (the same callbacks that cancel the subscription today);
  - `close(String sessionId)` completes every emitter of that session (`emitter.complete()`, with no
    `end` event: the browser must not take it as the end of the run);
  - an `@EventListener` on `SessionDestroyedEvent` calls `close(event.getId())`. This covers logout,
    the idle timeout (`PLATFORM_SESSION_TIMEOUT`), and the `me` / `changePassword` paths that end the
    session.
- `AnalysesApiDelegateImpl.streamEvents` registers its emitter under
  `http.getSession(false).getId()`. The security chain guarantees a session there; when there is
  none (not reachable in practice), it registers nothing.
- `AuthApiDelegateImpl.changePassword` calls `SessionEventStreams.close(id)` for every other session
  it expires, so their streams close at once and not on their next request.
- Closing a stream after its session is gone ends in an `ASYNC` dispatch through the filter chain,
  which now finds no session. That dispatch is a continuation of a request that was authorized when
  it started, so `SecurityConfig` permits `DispatcherType.ASYNC`
  (`.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()`, first in `authorizeHttpRequests`),
  with a comment that says why. Without it the entry point would try to write a 401 onto a response
  that has already been committed. The integration test (WP1) proves the stream ends cleanly and
  that a new request without a session still gets 401.

**Frontend.**

- `useRunStream` (`features/analysis/composables/useRunStream.ts`): after the server closes the
  stream, `EventSource` reconnects with `Last-Event-ID`. A reconnect without a session gets 401, and
  `EventSource` then gives up for good (`readyState === EventSource.CLOSED`) without telling why. So
  in `onerror`, when the source is `CLOSED` and the run has not ended, the composable makes one
  ordinary API call through `analysisApi.getAnalysis(id)`, ignoring its result and errors. A 401
  there goes through the HTTP client's existing `onUnauthorized` handler, which `app/main.ts`
  already wires to the login page with `redirect` set to the run page. Any other answer leaves the
  page as it is today (stream disconnected, no retry loop). The source is closed and not reopened,
  so nothing retries forever.
- The lifecycle hook becomes `onScopeDispose` instead of `onBeforeUnmount`. It behaves the same in a
  component and also works in an `effectScope`, which the new spec uses.

No change to the event protocol (`docs/event-protocol.md`), the REST contract or `openapi.json`: the
route, its parameters and its 401 answer are unchanged in shape.

Ownership checks on the stream (another user's analysis) stay out, as the issue says (#91/#92).

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: Backend: tie open streams to their session

- **Status**: done
- **Depends on**: none
- **Files**:
  - `artifact/backend/bff/api/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/controller/api/AnalysesApiController.java`
  - `artifact/backend/bff/api/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/controller/api/AnalysesApiDelegate.java`
  - `artifact/backend/bff/impl/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/delegate/impl/SessionEventStreams.java` (new)
  - `artifact/backend/bff/impl/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/delegate/impl/AnalysesApiDelegateImpl.java`
  - `artifact/backend/bff/impl/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/delegate/impl/AuthApiDelegateImpl.java`
  - `artifact/backend/bff/impl/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/delegate/impl/config/SecurityConfig.java`
  - tests: `artifact/backend/bff/impl/src/test/java/.../bff/delegate/impl/SessionEventStreamsTest.java` (new),
    `artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/AnalysesApiIntegrationTest.java`
- **Steps**:
  - [x] Pass `HttpServletRequest` from the controller to `AnalysesApiDelegate.streamEvents`.
  - [x] Add `SessionEventStreams` (register, close, `SessionDestroyedEvent` listener, removal on the
        emitter's completion, timeout and error).
  - [x] Register the emitter in `AnalysesApiDelegateImpl.streamEvents` under the request's session ID.
  - [x] Close the streams of the sessions `changePassword` expires.
  - [x] Permit `DispatcherType.ASYNC` in `SecurityConfig`, with the reason in a comment and in the
        class Javadoc's list of rules.
- **Tests**:
  - `SessionEventStreamsTest` (unit): `close` completes every emitter of that session and none of
    another session; a completed, timed-out or failed emitter is removed; a `SessionDestroyedEvent`
    closes the session's emitters; closing an unknown session does nothing.
  - `AnalysesApiIntegrationTest`:
    - the stream without a session answers 401 `unauthorized` (the issue's first criterion);
    - a stream opened on a slow run (`SLOW_TICKER`) ends without an `end` event when the same session
      logs out (`POST /api/auth/logout`) while it is open. The reader returns before the run is over,
      and a new stream request with the old cookies answers 401 (the third criterion);
    - a stream open in another session of the same user ends when the password is changed
      (`POST /api/auth/password`) in this one, while this session's own stream stays open (here and
      not in `PasswordChangeIntegrationTest`, since this class has the slow fake runner);
    - the existing stream tests stay green unchanged (the second criterion).

### WP2: Frontend: go to the login page when the stream's session ended

- **Depends on**: none
- **Files**:
  - `artifact/frontend/src/features/analysis/composables/useRunStream.ts`
  - `artifact/frontend/src/features/analysis/composables/useRunStream.spec.ts` (new)
- **Steps**:
  - [ ] In `onerror`, when `source.readyState === EventSource.CLOSED` and the run has not ended,
        call `analysisApi.getAnalysis(id)` once and ignore its outcome; close the source.
  - [ ] Replace `onBeforeUnmount` with `onScopeDispose`.
  - [ ] Update the composable's doc comment: the server closes the stream when the session ends; the
        check that follows leads to the login page through the 401 handler.
- **Tests**: `useRunStream.spec.ts` (Vitest, `node` environment) with a fake `EventSource`
  (`vi.stubGlobal`) and `analysisApi` mocked (`vi.mock('../api')`), run in an `effectScope`:
  - messages update the view and `end` sets `ended` and closes the source (today's behaviour);
  - an error while the source reconnects (`CONNECTING`) makes no API call;
  - an error with the source `CLOSED` makes exactly one `getAnalysis` call, and a rejected call
    (`ApiError` 401) does not throw;
  - after `end`, an error makes no call;
  - stopping the scope closes the source.

### WP3: Docs

- **Depends on**: WP1, WP2
- **Files**: `README.md`
- **Steps**:
  - [ ] Section "Login", the backend list: the live event stream (`GET /api/analyses/{id}/events`)
        needs a session like every other `/api/**` route, and an open stream is closed when its
        session ends (logout, idle timeout, a password change in another session).
  - [ ] Same section, the UI list: when a run's live stream closes because the session ended, the
        run page goes to the login page and returns to the run after login.
- **Tests**: none (`mise run check` covers the formatting).

## Tests

- Backend unit: `SessionEventStreamsTest`.
- Backend integration: `AnalysesApiIntegrationTest` proves "opening the stream without a session
  returns 401" and "after logout, the open stream is closed", and the same for the other sessions
  of a password change. The existing stream tests prove "a logged-in
  user still sees live events exactly as before".
- Frontend: `useRunStream.spec.ts` proves the run page asks the backend once and so reaches the 401
  handler (login page) instead of retrying.
- e2e: the existing `analysis.e2e.ts` ("a new analysis runs live and ends with its decision") must
  stay green: it is the live-events-as-before check through a real browser. No new e2e test (see Out
  of scope).
- `mise run check` passes.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md`, section "Login" (backend list and UI list) | The stream needs a session; open streams close when the session ends; the run page then goes to the login page (WP3). |
| Text | `docs/event-protocol.md` | None: the runner ↔ backend protocol and the SSE event shapes do not change. |
| API | `openapi.json`, `artifact/frontend/src/shared/api/schema.d.ts` | None: the route and its answers keep their shape. |
| Screenshot | none | No page changes its look; the login page it leads to is already `docs/screenshots/login.png`. |

## Out of scope

- Ownership checks on the stream (only the analysis's owner or `…:read:all`): #91 / #92.
- An e2e test of the logout-while-streaming case: the replayed run lasts about nine seconds, and the
  e2e tests share one logged-in admin session that a logout would end for the tests after it. The
  backend integration test and the Vitest spec cover the case without that race.
- Closing streams when an admin disables a user: that comes with user management (#88), which can
  call `SessionEventStreams.close` for the sessions it ends.
- Authentication of runner callbacks and actuator endpoints (#78, "Out of scope / later").
