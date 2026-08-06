# SecureChat — Enterprise Security Audit

**Target:** SecureChat E2E-encrypted messaging + collaboration backend (course B9IS129)
**Actual stack audited:** Spring Boot 4.1 / Java 25 backend, Angular 22 frontend, PostgreSQL + Flyway, Redis + Bucket4j, STOMP-over-WebSocket, Caddy + nginx reverse proxies, Docker Compose. *(The requested template assumed Node/Fastify/React; findings are mapped to the same OWASP/CWE/CVSS standards against the real code.)*
**Scope:** 108 Java source files, Angular SPA, all infrastructure (Docker, Compose, Caddy, nginx, CI/env), DB migrations, and configuration.
**Date:** 2026-08-04

---

## Executive Summary

This is a **well-architected, security-conscious codebase**. Authentication, authorization, session management, transport hardening, and the blind-relay confidentiality model are implemented correctly and consistently. The most dangerous vulnerability classes for this kind of app — IDOR/BOLA, authentication bypass, injection, secret leakage, missing authz on the WebSocket layer — were specifically probed and **not found**.

No **Critical** or **High** severity issues were confirmed by source review. The findings below are **Medium** and lower: primarily denial-of-service surface (unbounded input, targeted account lockout) and a client-side token-storage weakness that depends on a separate XSS to exploit (and the CSP is strict, making that hard). A large "Security Strengths" section documents the controls that were verified as correct, with evidence.

### Findings at a glance

| ID | Title | Severity | CVSS | Confidence |
|----|-------|----------|------|------------|
| M-1 | Auth tokens (incl. 7-day refresh token) stored in `localStorage` | Medium | 5.4 | Confirmed |
| M-2 | Unbounded request-body fields → memory/storage DoS | Medium | 5.3 | Confirmed |
| L-1 | Per-username account lockout enables targeted login DoS | Low | 4.3 | Confirmed |
| L-2 | Prod trusts broad RFC1918 ranges for `X-Forwarded-For` | Low | 3.7 | High Confidence |
| L-3 | One-time pre-key depletion by any authenticated user | Low | 3.5 | High Confidence |
| I-1 | WebSocket token validated only at CONNECT (no mid-session teardown) | Info | — | Confirmed |
| I-2 | `permitAll` for Swagger/api-docs paths that don't exist yet | Info | — | Confirmed |
| I-3 | State-mutating `GET` on pre-key-bundle (consumes an OTPK) | Info | — | Confirmed |
| I-4 | CSP allows `style-src 'unsafe-inline'` | Info | — | Confirmed |

---

## MEDIUM

### M-1 — Auth tokens (including the 7-day refresh token) stored in `localStorage`

- **Severity:** Medium · **CVSS v3.1:** 5.4 (`AV:N/AC:L/PR:N/UI:R/S:U/C:H/I:N/A:N`, conditional on XSS) · **CWE-522** (Insufficiently Protected Credentials), **CWE-1004** (Sensitive Cookie/Token without protection) · **OWASP A07:2021 – Identification & Auth Failures** · **CAPEC-593** (Session Hijacking)
- **Files:** `frontend/src/app/core/services/token-storage.service.ts:15-35`
- **Root cause:** Access token, **refresh token**, and the user object are persisted in `localStorage`:
  ```ts
  localStorage.setItem(ACCESS_TOKEN_KEY, auth.accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, auth.refreshToken);   // 7-day lifetime (application.yml:58)
  localStorage.setItem(USER_KEY, JSON.stringify(auth.user));
  ```
- **Technical explanation:** `localStorage` is readable by any JavaScript running on the origin. A single XSS foothold (or a malicious/ compromised npm dependency running at build time, or a malicious browser extension) can exfiltrate both tokens. The refresh token is the high-value target: it lives **7 days** (`refresh-expiration-ms: 604800000`) and, until it is next rotated, grants full re-authentication. Because the SPA also performs real E2E Signal crypto with private keys in IndexedDB, an in-page attacker who can read `localStorage` is already in a position to compromise the account end-to-end — but token theft specifically enables *offline*, *out-of-browser* impersonation against the API.
- **Attack preconditions:** An XSS execution primitive on the app origin (or supply-chain/extension code execution in the page).
- **Exploitation steps:** (1) land script on origin → (2) `fetch(attacker, {method:'POST', body: localStorage.getItem('refreshToken')})` → (3) attacker uses `/api/auth/refresh` from anywhere to mint fresh access tokens for up to 7 days.
- **Impact:** Full account takeover at the API layer (send/read ciphertext as the victim, manage sessions).
- **Likelihood:** Low–Medium. **Meaningfully mitigated** by a strict CSP (`script-src 'self' 'wasm-unsafe-eval'` — no `'unsafe-inline'`, no `'unsafe-eval'`; `nginx.conf:37,76`) and Angular's default contextual auto-escaping, which together make injecting executing script hard. This is why the finding is Medium, not High.
- **Business risk:** Impersonation of a user of a system whose entire value proposition is confidentiality.
- **Remediation:** Prefer storing the **refresh token in a `Secure; HttpOnly; SameSite=Strict` cookie** (out of reach of JS) and keeping only the short-lived (15-min) access token in memory (a JS variable / in-memory store, *not* `localStorage`). If the bearer-header design must be kept, at minimum move the refresh token out of `localStorage` into memory so a page reload re-authenticates via the cookie. Keep the CSP strict.
- **Secure example (concept):**
  ```ts
  // Access token: in-memory only, lost on reload (re-obtained via refresh cookie).
  private accessToken: string | null = null;
  setAccess(t: string) { this.accessToken = t; }        // never localStorage
  // Refresh token: never touches JS — server sets Set-Cookie: refresh=...; HttpOnly; Secure; SameSite=Strict
  ```
- **References:** OWASP ASVS 3.5, OWASP "HTML5 Security" / "Session Management" Cheat Sheets.
- **Confidence:** Confirmed.

---

### M-2 — Unbounded request-body fields → memory / storage denial of service

- **Severity:** Medium · **CVSS v3.1:** 5.3 (`AV:N/AC:L/PR:L/UI:N/S:U/C:N/I:N/A:L`) · **CWE-770** (Allocation of Resources Without Limits), **CWE-400** (Uncontrolled Resource Consumption) · **OWASP API4:2023 – Unrestricted Resource Consumption** · **CAPEC-130** (Excessive Allocation)
- **Files & evidence:**
  - `domain/task/dto/CreateTaskRequest.java` — `String description` has **no `@Size`**; the column is `description TEXT` (`V5__create_tasks.sql:18`) → unbounded.
  - `CreateTaskRequest.labels` / `UpdateTaskRequest.labels` — `List<@Size(max=50) String>` caps each label but **not the number of labels** → a request with millions of labels.
  - `domain/signal/dto/IdentityKeyUploadRequest.java` — `byte[] publicKey` is `@NotNull` with **no `@Size`**.
  - `domain/signal/dto/PreKeyUploadRequest.java` — `List<PreKeyDto> preKeys` is `@NotEmpty` with **no maximum count**; `saveAll` persists them all.
  - No global HTTP request-body size limit is configured (only the WebSocket path is capped at 64 KB in `WebSocketConfig.java:29`; the REST `ciphertext` is correctly capped at 64 KB in the DTOs — these are the *good* examples the others should follow).
- **Root cause:** For JSON bodies, Spring Boot / Tomcat does not impose a small default size cap (`max-http-form-post-size` applies to form encoding, not JSON), so any field lacking a bean-validation bound is limited only by heap.
- **Attack path:** An authenticated user (min privilege: any registered account) POSTs a task with a multi-hundred-MB `description`, or a `preKeys` array with a huge element count, or a giant `publicKey`. Each request allocates/persists proportional memory; repeated requests (bounded only by the generic 200 req/min default limit) exhaust heap or bloat storage.
- **Impact:** Availability degradation / OOM; persistent storage growth.
- **Likelihood:** Medium (trivial to send; requires an account).
- **Remediation:** Add explicit bounds:
  ```java
  @Size(max = 5_000)  String description;                 // task
  @Size(max = 20) List<@Size(max = 50) String> labels;    // cap count too
  @NotNull @Size(max = 64) byte[] publicKey;              // identity key is fixed-size
  @NotEmpty @Size(max = 200) List<@Valid PreKeyDto> preKeys;
  ```
  Also set a global cap: `server.tomcat.max-swallow-size` and a `@ControllerAdvice`/`DataBinder` limit, or front the API with a body-size limit at nginx (`client_max_body_size 128k;` for `/api/`).
- **References:** OWASP API Security Top 10 (API4:2023), CWE-770.
- **Confidence:** Confirmed.

---

## LOW

### L-1 — Per-username account lockout enables targeted login DoS

- **Severity:** Low · **CVSS v3.1:** 4.3 (`AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:L`) · **CWE-645** (Overly Restrictive Account Lockout) · **OWASP A07:2021**
- **Files:** `domain/user/service/UserService.java:141-152` (`recordFailedAttempt` locks after `maxFailedLoginAttempts`), `application.yml:82-83` (5 attempts → 30-minute lock).
- **Root cause:** Lockout keys on the victim's account, so anyone who knows a username can burn 5 failed attempts and lock that account for 30 minutes. The per-IP login throttle is exactly 5 / 15 min (`application.yml:64-67`), which is sufficient to trigger one lockout per window; rotating IPs sustains it.
- **Impact:** A known user can be denied login for rolling 30-minute windows.
- **Likelihood:** Low (needs to know target usernames; usernames aren't enumerable via the API — see Strengths).
- **Remediation:** This is the classic lockout-vs-DoS tradeoff. Consider (a) exponential backoff / IP-scoped throttling instead of hard account lock, or (b) CAPTCHA after N failures, or (c) not locking, relying on the per-IP + per-account rate limits plus BCrypt cost. If keeping the lock, ensure a legitimate user can self-unlock (email/step-up), which is currently only time-based.
- **Confidence:** Confirmed (design tradeoff, intentional but worth documenting for a bank-grade bar).

### L-2 — Production trusts broad RFC1918 ranges for `X-Forwarded-For`

- **Severity:** Low · **CVSS v3.1:** 3.7 · **CWE-348** (Use of Less Trusted Source), **CWE-290** (Authentication Bypass by Spoofing) · **OWASP A05:2021**
- **Files:** `application-prod.yml` `trusted-proxies: [127.0.0.1/32, 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16]`; consumed by `security/ClientIpResolver.java:42-61`.
- **Root cause:** The resolver correctly ignores `X-Forwarded-For` unless the *direct peer* is a trusted proxy — a strong design. But prod trusts **all** private ranges, not just the nginx container's subnet. If the backend's `8080` (only `expose`d, not published — good) is ever reachable by *another* host inside `10.0.0.0/8` (e.g. a compromised sibling container, or a cloud VPC that numbers hosts in `10/8`), that host can spoof `X-Forwarded-For` to forge an arbitrary client IP and **collapse/bypass the per-IP login and register throttles**.
- **Impact:** Bypass of per-IP brute-force throttling *if* an attacker already has network position inside the trusted ranges.
- **Likelihood:** Low (requires internal network access; backend is not published to the host).
- **Remediation:** Narrow `trusted-proxies` to the reverse proxy's actual subnet (e.g. the Compose network's `/24`), not all of RFC1918. Bucket4j per-IP limits then can't be diluted by a lateral attacker.
- **Confidence:** High Confidence.

### L-3 — One-time pre-key (OTPK) depletion by any authenticated user

- **Severity:** Low · **CVSS v3.1:** 3.5 · **CWE-770** / **CWE-400** · **OWASP API4:2023**
- **Files:** `domain/signal/controller/SignalKeyController.java:67-70` → `SignalKeyService.getPreKeyBundle` (`:139-154`), rate-limited 10/hour/user (`RateLimitFilter.java:104`, `application.yml:76-79`).
- **Root cause:** Fetching any target user's bundle consumes one of their OTPKs (`consumeOneTimePreKey`). Any authenticated user may fetch any other user's bundle (correct by design — public key material), so a set of attacker accounts can drain a victim's uploaded OTPKs.
- **Impact:** Once OTPKs are exhausted, new sessions fall back to the signed pre-key only — a **downgrade in forward secrecy for new sessions**, not a break of existing sessions or confidentiality. The 10/hour/user cap and register throttle (3/hour/IP) bound the drain rate.
- **Remediation:** Acceptable as-is for the threat model; optionally alert/replenish when a user's OTPK count drops below a threshold (a `pre-key-count` endpoint already exists), and keep the fetch rate limit.
- **Confidence:** High Confidence.

---

## INFORMATIONAL / HARDENING

- **I-1 — WebSocket auth only at CONNECT.** `StompAuthChannelInterceptor` validates the token at CONNECT and authorizes each SUBSCRIBE, but does not re-validate per frame (documented at `StompAuthChannelInterceptor.java:39-41`). Revoking a session or locking a user stops *new* connections/subscriptions but does not tear down an already-open socket until it disconnects. For a "logout everywhere kills live sockets instantly" guarantee, add a periodic session re-check or disconnect sessions on revoke. Low impact given 15-min access tokens and per-SEND membership checks.
- **I-2 — Dead `permitAll` for Swagger/api-docs.** `SecurityConfig.java:78` permits `/swagger-ui/**`, `/api-docs/**` unauthenticated. No SpringDoc dependency is present (CLAUDE.md notes it's deferred), so these are inert today — but if SpringDoc is later added, API docs would be exposed **unauthenticated**. Remove these matchers until docs exist, or gate them behind auth.
- **I-3 — State-mutating GET.** `GET /api/signal/pre-key-bundle/{userId}` consumes an OTPK (non-idempotent GET). No CSRF risk (bearer-token auth, no ambient cookie), but it violates GET semantics and can be triggered by prefetch/crawlers. Consider `POST`. Informational.
- **I-4 — `style-src 'unsafe-inline'`.** Both the nginx CSP (`nginx.conf:37,76`) and the fact that Angular injects runtime `<style>` require `style-src 'unsafe-inline'`. Scripts are *not* inline-allowed, so this does not enable script XSS; it slightly widens CSS-injection surface only. Acceptable; revisit if Angular's nonce-based styles become viable.
- **I-5 — `.env` on disk.** A real `.env` exists locally (`-rw-------`, correctly `600`) and is `.gitignore`d and **not tracked** (verified via `git ls-files`). Good hygiene; keep it out of images (it is — Compose passes vars, doesn't COPY the file).

---

## SECURITY STRENGTHS (verified, with evidence)

These were actively probed and proven correct — not assumed.

**Authentication & sessions**
- **Asymmetric JWTs (RS256)** with the private key never leaving the signer; `parse()` pins `verifyWith(public)` **and** `requireIssuer` (`JwtTokenProvider.java:84-97`). No `alg:none`/HMAC-confusion path (JJWT `verifyWith` fixes the algorithm to the key type).
- **Token-type binding:** every token carries `typ`; the HTTP filter and STOMP interceptor reject a refresh token used as an access token (`JwtAuthenticationFilter.java:76`, `StompAuthChannelInterceptor.java:84`).
- **Server-side revocation on every request:** each token carries `sid`; the filter reloads the session and rejects if `!session.isActive()` (`JwtAuthenticationFilter.java:81-85`) — enabling instant logout / logout-everywhere despite stateless JWTs.
- **Refresh-token rotation with reuse detection:** the session stores the current refresh `jti`; presenting a superseded token revokes the whole session (`AuthService.java:107-112`). Single-active-session-per-user on login (`:164`).
- **BCrypt cost 12** (`SecurityConfig.java:55`).
- **Username/email enumeration prevented:** generic messages on login and registration, plus a **timing-equalizer** dummy BCrypt compare for non-existent users (`AuthService.java:67-71`, `UserService.wastePasswordCompare`), and id-only logging (`UserService.java:81`).
- **Tokens only accepted from the `Authorization` header** — never cookies or query strings, on both HTTP and the STOMP CONNECT frame (`JwtAuthenticationFilter.java:105-110`, `StompAuthChannelInterceptor.java:76-78`), so they don't leak into proxy/access logs.

**Authorization (no IDOR/BOLA/BFLA found)**
- Every controller derives identity from `@AuthenticationPrincipal` / STOMP principal and **never** trusts a body/query `userId` (`MessageController`, `ChatController`, `TaskController`, `SignalKeyController`, `WebSocketController`). No mass-assignment of ownership.
- Authorization is centralized in the service layer via `ChatAccessGuard` (`requireMember`/`requireAdmin`) and re-checked identically across REST and WebSocket. Failures throw `AccessDeniedException` → **403, never 404** (`GlobalExceptionHandler.java:56-60`), so resource existence never leaks.
- Object-level checks verified: message delete = sender only (`MessageService.java:183`); receipts forbidden on own messages (`:159`); reply-to must be same chat (`:86`); task delete = creator-or-admin (`TaskService.java:237-243`); assignee must be a chat member (`:246-251`); session revoke checks ownership (`AuthService.java:145`).
- **WebSocket authz is not assumed from subscription:** every `SEND` handler re-checks membership before broadcasting (`WebSocketController.java:75,84`, and `MessageService` for `chat.send`), closing the "subscribe-authorized but send-anywhere" gap.

**Confidentiality / blind-relay model**
- `ciphertext` stored as opaque `BYTEA` (`V4__create_messages.sql`), never decrypted, and **never logged** — all log lines use ids only (`MessageService.java:98`). The server holds no Signal private keys and verifies no signatures (correct blind-relay posture).

**Injection & input handling**
- No raw SQL / string-concatenated queries; Spring Data + JPQL throughout. The user search **escapes LIKE metacharacters** (`%`, `_`, `!`) so wildcards can't be injected (`UserService.java:101`).
- Bean-validation on all request DTOs; malformed UUID/enum path params → clean 400 (`GlobalExceptionHandler.java:49-53`).
- No dangerous frontend sinks: `grep` for `innerHTML` / `bypassSecurityTrust*` / `eval` / `[innerHTML]` across `frontend/src/app` returned **nothing**; Angular auto-escaping intact.

**Concurrency**
- OTPK issuance is race-safe: `FOR UPDATE SKIP LOCKED` guarantees two concurrent bundle fetches never hand out the same one-time key (`SignalKeyService.java:162-176`).
- Message fan-out registered as `afterCommit` so a rolled-back send is never broadcast (`MessageService.java:113-124`).

**Transport & HTTP hardening**
- API sets `default-src 'none'; frame-ancestors 'none'; base-uri 'none'` CSP, HSTS (1 yr, includeSubDomains), `frameOptions.deny`, `Referrer-Policy: no-referrer`, and a locked Permissions-Policy (`SecurityConfig.java:82-93`). nginx mirrors a strict SPA CSP with `nosniff`, `X-Frame-Options: DENY`, HSTS (`nginx.conf:37-43,76-81`).
- **Strict CORS allow-list**, never a wildcard, `allowCredentials(true)` bound to explicit origins (`SecurityConfig.java:99-113`); in prod the origin is a single configured value; same-origin nginx proxy means CORS isn't even needed at runtime.

**Config & infrastructure (contrast to the earlier H-3 pattern — this project does the opposite, correctly)**
- **Fail-closed prod secrets:** `ProdSecretsValidator` refuses to start with blank/default DB, Redis, or JWT secrets (`ProdSecretsValidator.java`); `JwtKeyConfig` refuses ephemeral keys outside dev (`:49-53`).
- **Datastores not published to the host:** prod `docker-compose.yml` gives Postgres/Redis **no host ports** (internal network only); backend/frontend use `expose`, only Caddy publishes 80/443. Compose secrets use fail-closed `${VAR:?...}`. Dev infra binds `127.0.0.1` only.
- `ddl-auto: validate` with Flyway owning schema; `open-in-view: false`; `show-sql: false` in prod; stack traces/messages/binding-errors never returned (`application.yml:20-46`).
- **Non-root container:** backend runs as an unprivileged `app` user (`project/Dockerfile:26-29`); multi-stage builds; `npm ci` from lockfile.
- Redis requires a password in every profile; rate-limit + lockout state kept there.
- Actuator exposes only `health,info`; only `/actuator/health` is public and `show-details: never` (`application.yml:92-100`, `SecurityConfig.java:77`).

---

## MANUAL PENETRATION-TESTING CHECKLIST

Items that source review cannot fully confirm and that warrant dynamic testing:

- [ ] **Authn bypass / JWT tampering:** attempt `alg:none`, HS256-signed-with-RSA-public-key, `kid` tricks, expired/nbf edge cases against `/api/**` and the STOMP CONNECT frame.
- [ ] **Refresh rotation & reuse:** confirm that replaying a rotated refresh token revokes the session; race two concurrent `/api/auth/refresh` calls with the same token (double-rotate).
- [ ] **Authorization matrix:** as user A, exercise every `{chatId, messageId, taskId, sessionId, userId}`-parameterized endpoint against B's objects; confirm uniform 403 (no 404 leak) — REST **and** WebSocket SEND to a non-member chat.
- [ ] **WebSocket:** subscribe to `/topic/user/{other}`, `/topic/chat/{foreign}`, `/topic/tasks/{foreign}`; fuzz STOMP frames; verify token-at-CONNECT-only behavior after a mid-session logout (I-1); flood `chat.send` to confirm the shared 60/min bucket and 64 KB cap.
- [ ] **Rate-limit bypass:** spoof `X-Forwarded-For` from inside/outside trusted ranges (L-2); confirm login (5/15min IP), register (3/hr IP), message-send (60/min user), pre-key-fetch (10/hr user) enforcement and Redis-outage failure mode.
- [ ] **DoS (M-2):** large `description`, million-element `labels`/`preKeys`, oversized `publicKey`; measure heap/latency; confirm any nginx/Tomcat body cap.
- [ ] **Account-lockout DoS (L-1):** lock a known username and measure recovery; test IP rotation.
- [ ] **OTPK depletion (L-3):** drain a victim's one-time pre-keys across multiple accounts; observe bundle fallback.
- [ ] **Token exfiltration (M-1):** validate CSP actually blocks inline/eval script injection attempts across all rendered surfaces; attempt to read `localStorage` via any reflected/stored input.
- [ ] **Infra:** confirm Postgres/Redis/backend are unreachable from the host and from a sibling container except via nginx; verify Caddy TLS config and HSTS preload behavior; check for header/response smuggling across Caddy→nginx→backend.
- [ ] **Cache/Redis:** attempt rate-limit key collision/poisoning; verify lockout key TTL auto-unlock path.
- [ ] **Dependencies:** run `npm audit` (frontend) and `./mvnw dependency-check` / OWASP DC (backend) for CVEs — not assessable from source alone.

---

*No Critical or High severity issues were confirmed by source review. Prioritize M-1 (move the refresh token out of `localStorage`) and M-2 (bound request-body fields) before production sign-off; L-1/L-2/L-3 are hardening.*
