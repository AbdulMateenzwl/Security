
# SecureChat - Secure Communications & Collaboration System

**Module:** B9IS103 - Computer Systems Security 2026
**Assessment:** Secure Communications/Collaboration System Design and Deployment
**Repository:** publicly hosted git (see commit history below)
**Deployment:** _to be added - cloud deployment URL_

> **Note on working style:** this project was carried out individually, so most "meetings"
> and design discussions are recorded as Claude chat logs (linked below), retained as the
> AI-usage record required by the brief.

---

## 1. Introduction & System Overview

SecureChat is an end-to-end (E2E) encrypted messaging and collaboration application built
around the **Signal protocol** (X3DH key agreement + Double Ratchet). It lets two parties who
have **never met to exchange keys** communicate securely: each client publishes public key
material to the server, and any peer can fetch a pre-key bundle to establish an encrypted
session on demand.

The central security stance is that **the server is a blind relay**: it stores and forwards
Signal ciphertext (`byte[]`) only and never has access to message plaintext or to any private
key. All encryption/decryption happens in the client (an Angular single-page app); private keys
are generated in the browser and never leave it (stored in IndexedDB). This satisfies the
brief's "maliciously curious provider" model - even a fully compromised server or database
yields only ciphertext.

On top of secure messaging, the system provides in-chat **collaboration** features (tasks,
kanban board, activity log) so a team can coordinate work inside the same encrypted context.

**Technology stack:** Spring Boot 4.1 / Java 25 backend; Spring Security 7 + JJWT (RS256);
PostgreSQL + JPA + Flyway; Redis + Bucket4j (rate limiting); WebSocket/STOMP (realtime);
Angular frontend using `@privacyresearch/libsignal-protocol-typescript` for client-side crypto.

Section **6 (Security Design Rationale)** below gives the threat-driven *reasoning* behind each
security control (threat → decision → why → trade-off).

---

## 2. Running the Application

The whole system is containerized — frontend (nginx), backend (Spring Boot), PostgreSQL and
Redis — and orchestrated with a single Docker Compose file at the repository root. Only the
frontend (nginx) is published to the host; nginx serves the Angular app and reverse-proxies
`/api` and `/ws` to the backend, so the browser talks to a single origin and the backend,
database and Redis are never exposed outside the internal Docker network.

**Prerequisites:** Docker and Docker Compose v2+ (`docker compose`), plus `openssl` (used by the
secret-generation script).

### Option A — Docker (recommended, production-like)

```bash
# 1. Generate secrets: strong DB/Redis passwords + an RSA key pair for RS256 JWTs.
#    Writes a git-ignored .env (mode 600). Run once.
./scripts/gen-secrets.sh

# 2. Build the images and start the whole stack (backend runs the `prod` profile).
docker compose up -d --build

# 3. Check everything is healthy.
docker compose ps

# 4. Open the app.
open http://localhost            # or just browse to http://localhost
```

Flyway runs the database migrations automatically on first backend start. To change the
published port, set `WEB_PORT` in `.env` (e.g. `WEB_PORT=8081` → http://localhost:8081).

Useful commands:

```bash
docker compose logs -f backend   # follow backend logs
docker compose down              # stop the stack (keeps data)
docker compose down -v           # stop and delete the database volume
```

> **Note:** the backend defaults to the `prod` profile, which refuses to start with blank or
> default secrets. `gen-secrets.sh` satisfies this. For a relaxed local run with an ephemeral
> JWT key pair, set `SPRING_PROFILES_ACTIVE=dev` in `.env`.

### Option B — Local dev (hot reload, no containers for the app)

Run only the infrastructure in Docker and the app from source:

```bash
# Postgres + Redis only (from the project/ directory)
cd project && docker compose up -d

# Backend (needs JDK 25)
./mvnw spring-boot:run

# Frontend (needs Node 22), in another terminal
cd ../frontend && npm install && npm start   # http://localhost:4200
```

The Angular dev server proxies `/api` and `/ws` to the backend on `:8080` (see
`frontend/proxy.conf.json`).

---

## 3. Functional Requirements

| # | Requirement |
|---|-------------|
| F1 | Users can **register** and **log in** with a username and password; a JWT access/refresh token pair is issued on success. |
| F2 | Users can maintain **multiple concurrent sessions** (devices) and can **log out** a single session or **log out everywhere** (revoke all sessions). |
| F3 | Each client can **publish its Signal public key material** - identity key, a signed pre-key, and a batch of one-time pre-keys - to the server. |
| F4 | Any user can **fetch a peer's pre-key bundle** to start an E2E session with someone they have never exchanged keys with. One-time pre-keys are consumed atomically so no two sessions reuse the same key. |
| F5 | Users can exchange **end-to-end encrypted direct (1:1) messages**; the server stores/forwards only opaque ciphertext. |
| F6 | Users can retrieve **paginated message history** (newest-first, cursor-based) and see **delivery / read receipts**. |
| F7 | A message **sender can delete** their own message. |
| F8 | Chats support **disappearing messages** - an optional timer after which messages expire and are hard-deleted from the server. |
| F9 | Users can create **direct (1:1) chats** with another user. |
| F10 | Chats include a **collaboration board**: tasks with status, priority, assignee, due date and labels; a **kanban** view grouped by status; and **filtering** by status/assignee. |
| F11 | Every task change is automatically recorded in an **activity log** (create, status/priority/title/description/due-date changes, assignment). |
| F12 | Messages, typing indicators and task changes are delivered in **real time** over WebSocket/STOMP. |
| F13 | Users can **search** for other users to start a chat. |
| F14 | Users can view a peer's **safety number (identity-key fingerprint)** and are **warned when a peer's identity key changes** (possible device switch or MITM). |

---

## 4. Non-Functional Requirements

| # | Requirement |
|---|-------------|
| NF1 | **Performance** - realtime delivery with low latency; known N+1 query patterns eliminated (batched member loads, Hibernate `@BatchSize`). |
| NF2 | **Scalability** - stateless JWT authentication and Redis-backed shared state (rate limits, lockouts) so the app can run as multiple horizontally-scaled instances. |
| NF3 | **Reliability / Availability** - health-check endpoint, container health checks, and automatic WebSocket reconnect + re-subscribe on the client. |
| NF4 | **Maintainability** - domain-driven package layout (`domain/<area>/…`), a single centralised authorization guard, and a JUnit 5 + Mockito unit-test suite for the core rules. |
| NF5 | **Portability / Deployability** - 12-factor configuration via environment variables, Docker Compose for local infra, and separate `dev`/`prod` profiles for cloud deployment. |
| NF6 | **Usability** - a responsive Angular SPA with clear connection ("Live"/"Connecting") and security ("safety number changed") indicators. |
| NF7 | **Observability** - Spring Boot Actuator health endpoint and uniform structured error responses. |
| NF8 | **Interoperability / Standards** - built on published standards (Signal protocol, RS256 JWT, STOMP over WebSocket) rather than any proprietary scheme. |

---

## 5. Security Requirements

These are the explicit security requirements of the system. The *reasoning* for each is expanded
in §6 (Security Design Rationale) below.

**Confidentiality**
- SR1 - Message content must be **end-to-end encrypted**; the server must never see plaintext (blind relay).
- SR2 - **Private keys never leave the client** and are never transmitted to or stored on the server.
- SR3 - No message plaintext is persisted anywhere on the server; disappearing messages are hard-deleted after expiry.

**Integrity & Authenticity**
- SR4 - Message integrity and forward secrecy are provided by the **Double Ratchet**; pre-keys are **signed** and verified client-side.
- SR5 - Tokens are **RS256-signed**; verification uses only the public key.
- SR6 - Users can verify a peer's identity via a **safety number**, and are **alerted to identity-key changes** (MITM detection).

**Authentication**
- SR7 - Passwords are hashed with **BCrypt (cost 12)**, never stored reversibly.
- SR8 - A **server-side password policy** (length + complexity + common-password blocklist) is enforced regardless of client checks.
- SR9 - Repeated failed logins trigger **account lockout** (durable flag + Redis auto-unlock).
- SR10 - Access is **revocable**: every request re-validates its session, so logout / "logout everywhere" invalidate tokens immediately.
- SR11 - Refresh tokens are **rotated** on use, with **reuse detection** that revokes the session if a stale token is replayed.
- SR12 - Login must **not leak whether an account exists** - uniform error messages and equalised response timing (anti-enumeration).

**Authorization**
- SR13 - All object-level authorization (chat membership, admin role, sender-only actions) is enforced in the **service layer**, not just the controller.
- SR14 - Authorization failures return **403** and the check is centralised so it cannot drift or be forgotten on any entry point (REST or WebSocket).

**Availability / Abuse Resistance**
- SR15 - Sensitive endpoints are **rate-limited** (per-IP for login/register/refresh, per-user for messaging/pre-keys), with Redis-backed buckets.
- SR16 - The client IP used for per-IP limits must **not be spoofable** - `X-Forwarded-For` is trusted only from configured trusted proxies.

**Transport & Configuration**
- SR17 - Strict HTTP security headers on every response (**HSTS, CSP, Referrer-Policy, Permissions-Policy, frame-deny**).
- SR18 - **Strict CORS allow-list** - no wildcard origins.
- SR19 - Supporting stores must be hardened: **Redis requires authentication**, and datastores bind to loopback in the local deployment.
- SR20 - The **production profile fails fast** and refuses to start with default/insecure secrets (DB/Redis passwords, JWT keys).

**General**
- SR21 - **No security through obscurity** - only standard, published algorithms are relied upon.
- SR22 - Errors must not leak internal detail; malformed input returns **400**, not a stack trace or 500.

---

## 6. Security Design Rationale

This section explains the **reasoning** behind each security control: for each one, the *threat*
it addresses, the *design choice* made, *why* that choice over the alternatives, and its
*trade-offs*. It is a defence of the reasoning, not a code listing — the goal is to show
understanding of *why* the system is built the way it is.

### 6.0 Foundational principle — the server is a *blind relay*

**Threat:** a compromised server, a malicious operator, or a database breach exposing everyone's messages.

**Decision:** the server never sees plaintext. It stores and forwards Signal ciphertext (`byte[]`) only; all encryption/decryption happens in the client. No libsignal runs on the server.

**Why:** this collapses the trust boundary. Even with full database access an attacker gets only ciphertext, because the private keys never leave the client's IndexedDB. This is the same architectural stance as Signal itself — confidentiality does not depend on trusting the server.

**Trade-off:** the server cannot do anything that needs plaintext (server-side search, content moderation, previews). That is an accepted cost of true end-to-end encryption.

### 6.1 Authentication & session management

**6.1.1 RS256 JWTs bound to a server-side session**

**Threat:** stolen or forged tokens; inability to revoke access after logout or compromise.

**Decision:** access/refresh tokens are **RS256** (asymmetric) JWTs. Every token carries a session id (`sid`) and a type (`typ`). A `JwtAuthenticationFilter` re-validates the referenced `user_sessions` row on **every** request.

**Why:**
- **RS256 over HS256:** signing uses a private key; verification uses only the public key. A service that merely needs to *verify* tokens never holds the secret that can *mint* them.
- **Session binding:** a plain stateless JWT cannot be revoked before it expires. By checking a session row on each request, setting `user_sessions.revoked = true` invalidates the token **immediately** — which is what makes "log out" and "log out everywhere" actually work.

**Trade-off:** a DB/cache lookup per request instead of pure stateless verification. Accepted, because instant revocation is a hard requirement for a security product.

**6.1.2 Refresh-token rotation with reuse detection**

**Threat:** a stolen refresh token being replayed to mint fresh access tokens indefinitely.

**Decision:** each refresh **rotates** the token — a new access+refresh pair is issued and the session records the `jti` (unique id) of its *current* refresh token. Presenting an old, already-rotated refresh token is treated as **reuse**: the entire session is revoked.

**Why:** rotation shrinks the useful lifetime of any single refresh token. Reuse detection turns theft into a *detectable* event: if both the attacker and the legitimate user try to use the token, one presents a stale `jti`, and revoking the session locks *both* out — forcing a fresh, authenticated login. This is the OAuth 2.0 refresh-token-rotation pattern (RFC 9700 / BCP).

**Trade-off:** a legitimate client that loses a rotation response (e.g. a dropped network response) may be forced to re-authenticate. Correct fail-safe direction: on ambiguity, revoke.

**6.1.3 Account lockout — hybrid durable flag + Redis TTL**

**Threat:** online password brute-forcing.

**Decision:** after N failed logins the account is locked using **both** a durable `account_locked` column **and** a Redis key with a TTL for automatic unlock.

**Why:** Redis alone gives auto-expiry but loses the lock if the cache is flushed; a DB flag alone has no natural expiry. Combining them means the lock survives a cache restart *and* unlocks itself after the configured window without a scheduled job.

**Trade-off:** two sources of truth to keep consistent; mitigated by treating the durable flag as authoritative.

**6.1.4 Login timing-attack / account-enumeration resistance**

**Threat:** an attacker discovering *which usernames exist* by measuring response times (real user → a BCrypt comparison runs; unknown user → an early return, measurably faster).

**Decision:**
- The error message is identical for "no such user" and "wrong password" (`Invalid username or password`).
- For an unknown username, the code still performs a BCrypt comparison against a dummy hash (`wastePasswordCompare`) so the response takes the same time.

**Why:** identical messages defeat *content-based* enumeration; the equalising BCrypt call defeats *timing-based* enumeration. Both are needed — one without the other still leaks.

**Trade-off:** a wasted hash computation on unknown-user logins. Negligible, and intentional.

**6.1.5 Server-side password policy**

**Threat:** weak or trivially-guessable passwords; client-side validation being bypassed.

**Decision:** the server enforces 8–128 characters including at least one letter and one digit, and rejects a blocklist of the most-guessed passwords. Enforced in the service layer, **not** only in the browser.

**Why:** client-side checks are a UX convenience; anyone can call the API directly and skip them. The server is the only trustworthy enforcement point. The length cap (128) also bounds the BCrypt input.

**Trade-off:** deliberately modest rules to stay usable; a production system would add breach-corpus checks (e.g. HaveIBeenPwned k-anonymity).

**6.1.6 BCrypt password hashing (cost 12)**

**Threat:** offline cracking of hashes after a DB breach.

**Decision:** passwords are hashed with **BCrypt, work factor 12** — never stored or reversibly encrypted.

**Why:** BCrypt is deliberately slow and salted per-hash, so it resists both rainbow tables and GPU brute-forcing. Cost 12 is a common modern balance of security vs. login latency.

### 6.2 Rate limiting & abuse resistance

**6.2.1 Redis-backed per-endpoint limits (Bucket4j)**

**Threat:** credential stuffing, registration spam, message flooding.

**Decision:** per-endpoint token-bucket limits via Bucket4j, with state in Redis so limits hold across multiple app instances. Auth endpoints are keyed **per-IP**; authenticated endpoints (send message, fetch pre-keys) **per-user**. Rejected requests get `429` with `Retry-After` and `X-RateLimit-*` headers.

**Why:** the key choice matters. Login/register have no authenticated user yet, so per-IP is the only handle. Once authenticated, per-user is fairer and harder to evade than per-IP (NAT, shared IPs). Redis-backed state keeps limits correct behind a load balancer.

**Trade-off:** per-IP limiting can catch many legitimate users behind one NAT; accepted for the small set of unauthenticated endpoints.

**6.2.2 Trustworthy client-IP resolution (anti-spoofing)**

**Threat:** an attacker sending a forged `X-Forwarded-For` header to get a *fresh* rate-limit bucket per request and bypass per-IP throttling entirely.

**Decision:** `X-Forwarded-For` is honoured **only** when the direct socket peer is a configured *trusted proxy*. The default trusted-proxy list is empty, so by default the real socket address is always used.

**Why:** `X-Forwarded-For` is entirely attacker-controlled unless a proxy you trust set it. Blindly reading the header lets any client claim any IP — which *defeats* rate limiting rather than enforcing it. Trusting it only from known proxy IPs is the correct model, and defaulting to "trust nothing" is fail-safe.

**Trade-off:** behind a real load balancer (or the nginx container in this deployment) you must configure its IP, or every client appears to share the proxy's IP. This is a conscious, documented deployment step.

### 6.3 Transport & configuration hardening

**6.3.1 HTTP security headers**

**Threat:** clickjacking, MIME-based attacks, referrer leakage, and browser features being abused if any response is ever rendered.

**Decision:** on every response — `X-Frame-Options: DENY`, HSTS (1 year, includeSubDomains), a strict CSP (`default-src 'none'; frame-ancestors 'none'; base-uri 'none'`), `Referrer-Policy: no-referrer`, and a restrictive `Permissions-Policy`.

**Why:** this is a JSON API that never returns HTML, so it can afford the *strictest possible* browser policy — deny framing, forbid loading anything, send no referrer. Defence in depth: even if a response were somehow rendered, the browser is told to do nothing with it. HSTS forces HTTPS and defeats SSL-strip downgrade attacks.

**Trade-off:** none meaningful for a pure API.

**6.3.2 Strict CORS allow-list (no wildcards)**

**Threat:** malicious web origins making authenticated cross-origin calls.

**Decision:** CORS allows an explicit list of origins only; wildcards are intentionally unsupported. Credentials are allowed, which *requires* an exact-origin list. (In the Docker deployment nginx serves the app and API from the same origin, so cross-origin requests do not arise at all.)

**Why:** `Access-Control-Allow-Origin: *` cannot be combined with credentials, and would let any site talk to the API. An explicit allow-list is the only safe option when cookies/authorization are in play.

**6.3.3 Fail-fast production secrets validator**

**Threat:** a production deployment silently booting with development defaults — a default DB/Redis password, unauthenticated Redis, or the ephemeral dev JWT keypair.

**Decision:** under the `prod` profile only, a validator **refuses to start** if the DB password, Redis password, or JWT keys are missing or still set to known dev defaults.

**Why:** misconfiguration is one of the most common real-world breach causes. Failing loudly at startup is far safer than running insecurely and finding out later. Dev/test keep their convenient defaults; only prod is strict.

**Trade-off:** a deployment must supply real secrets before it will boot — which is the point (and why the deployment ships a `gen-secrets.sh` helper).

**6.3.4 Network exposure & Redis auth**

**Threat:** the database or cache being reachable from outside the host, or an unauthenticated Redis.

**Decision:** in the Docker deployment, Postgres and Redis are **not published to the host at all** — they are reachable only on the internal Docker network — and Redis requires a password in every environment. Redis holds rate-limit and account-lockout state, so it is security-relevant, not just a cache.

**Why:** reducing network attack surface (internal-only) and requiring auth on Redis prevents a trivially-reachable, unauthenticated data store — a classic exposure.

### 6.4 Authorization model

**Threat:** a user reading or acting on chats, messages, or tasks they don't belong to (IDOR / broken object-level authorization).

**Decision:** authorization is enforced in the **service layer**, not just at the controller. Access checks are centralised (a shared chat-access guard for membership/admin roles). Authorization failures return **403**, and object-level checks confirm the caller is a member before any read/write.

**Why:** enforcing authz in the service layer means every entry point (REST *and* WebSocket) goes through the same check — you can't forget it on one controller. Broken object-level authorization is the #1 item on the OWASP API Security Top 10, so this is deliberately central rather than scattered.

**Note on 403 vs 404:** returning 403 is a conscious choice for this project's threat model. (404 can hide the existence of a resource to reduce enumeration; 403 gives a clearer authorization signal. Either is defensible — the point is that the check *happens* server-side and cannot be bypassed by the client.)

### 6.5 End-to-end encryption & key integrity (client side)

**6.5.1 Private keys never leave the client**

**Threat:** server or network compromise exposing message-decryption keys.

**Decision:** the Angular client generates Signal identity/pre-keys in the browser and stores private keys in IndexedDB. Only *public* keys and pre-key bundles are published to the server.

**Why:** this is what makes the "blind relay" real — the material needed to decrypt never exists on the server. See §6.0.

**6.5.2 Safety-number (identity-key) change detection**

**Threat:** a man-in-the-middle silently substituting a peer's key, or an undetected device change, causing a user to encrypt to the wrong key.

**Decision:** when a peer's stored identity key changes, the client raises a **"⚠️ safety number changed"** banner in the conversation, warning that the peer may be on a new device *or* the connection could be compromised, and prompting out-of-band verification before trusting new messages.

**Why:** in the Signal model, a changed identity key is exactly the signal of a possible MITM. Surfacing it to the user — rather than silently accepting the new key — puts the trust decision where it belongs (the human), which is how Signal/WhatsApp handle "safety number changed".

**Trade-off:** legitimate device switches also trigger the warning; that is intentional — the user should confirm rather than have the app decide for them.

### 6.6 Error handling & information disclosure

**Threat:** stack traces or internal details leaking through error responses; malformed input causing 500s that reveal implementation details.

**Decision:** a global exception handler maps known exceptions to a uniform error response and never leaks internals. Malformed path/query parameters return **400**, not 500.

**Why:** consistent, minimal error responses give an attacker no implementation detail to work with, and distinguishing "bad request" (400) from "server error" (500) avoids advertising unhandled edge cases.

### 6.7 Summary — how the controls compose

The design layers defences so no single failure is catastrophic:

- **Confidentiality** rests on client-side E2E encryption (§6.0, §6.5), not on trusting the server.
- **Authentication** is revocable and rotation-based (§6.1), so stolen tokens have a short, detectable life.
- **Abuse resistance** (§6.2) throttles brute-force and spam, with IP handling that can't be spoofed.
- **Hardening** (§6.3) shrinks attack surface and prevents insecure deployments from ever starting.
- **Authorization** (§6.4) is centralised and server-enforced, closing IDOR gaps.
- **Key-integrity UX** (§6.5.2) puts MITM detection in the user's hands.

Each control addresses a concrete threat, and the failure of any one still leaves others standing — defence in depth rather than a single perimeter.

---

## 7. AI-Usage Declaration

_(Required by the module's Generative AI Assessment Scale.)_

- **Level of AI use:** this project was developed with **Claude / Claude Code used as a co-pilot
  throughout** (approximately Level 4–5 on the module scale: "AI task completion, human evaluation"
  through "AI as co-pilot"). The brief notes AI-generated content is expected to feature heavily in
  submissions; it does here, and is declared accordingly.
- **What AI was used for:** brainstorming and researching the cryptographic approach (Signal
  protocol, Double Ratchet), scaffolding the Spring Boot/Angular codebase, generating
  implementation code, debugging (notably the multi-day pre-key signature issue documented under
  _Signal Protocol Implementation Errors_ below), the security-hardening pass, and drafting
  documentation including parts of this report and the Security Design Rationale (§6).
- **Human evaluation & understanding:** all AI-generated content was reviewed, integrated,
  tested and is understood by the author, who can explain and defend the design decisions at the
  presentation/viva. The Security Design Rationale (§6) was written specifically to record that
  understanding (threat → decision → why → trade-off).
- **Retained logs:** LLM conversation logs are retained and linked under **Claude Chat Links**
  below, satisfying the requirement to retain prompts and logs.
- **Attribution in history:** commits and timeline entries that were AI-assisted are annotated
  (e.g. "(Claude Code)", "(AI Help)"). Credit is claimed for the author's own integration,
  evaluation and deployment of these resources, not for the generated resources themselves.

---

## 8. Contribution Declaration

This is a solo submission - a single author was responsible for all group-member contributions.

| Contributor | Role / Contribution |
|-------------|---------------------|
| _[Author name - fill in]_ (student no. _[fill in]_) | All original work: system design, requirements, integration, evaluation, testing and deployment of the application, and authorship of this report. |
| Claude / Claude Code (AI assistant) | External resource used as a co-pilot as declared in §7 above; not a group member. |

_External resources (AI, libraries, frameworks) are attributed as required; credit is limited to
the author's own work and to how these resources were integrated and deployed._

---

# Claude Chat Links

https://claude.ai/share/902eee61-39db-4475-953f-394d9d5fe881

# Timeline
## Week 1
what I did in the first week
- Searched for the appropriate algorithm for encryption
- Searched common type of attacks on encryption algorithms, how a encrypted data can be decrypted and how to prevent it
- Found out about the double ratchet algorithm and how it works, and how it is used in signal protocol

## Week 2
what I did in the second week
- Found out about the signal protocol and how it works, and how it is used in signal
- Go through the signal library and found there is a java version of the library, and how it is used in signal

### References
- [Signal Library Java - MVN Repository](https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-starter-aop/versions)
- [Signal Library Repo](https://github.com/signalapp/libsignal)

## Week 3
what I did in the third week
- Researched about the libraries that will be used in addition to signal library and how they will be integrated with project security
- Setup the project with the signal library and other libraries that will be used in the project

# Week 4
what I did in the fourth week
- Refactored project configuration and added application profiles for development and production (Claude Code)
- Add JWT and security configuration classes with properties support
- Add custom exception classes for error handling in the application
- Implement JWT authentication with access and refresh token support, including user session management and error handling (Little help fromn Claude code)

# Week 5
what I did in the fifth week
- Implement user authentication and session management with JWT support, including user registration, login, and session handling (AI Help from Claude code)
- Implement Signal key management: store public identity keys, signed pre-keys and one-time pre-keys, and serve pre-key bundles so peers can start end-to-end encrypted sessions. One-time pre-keys are consumed atomically (FOR UPDATE SKIP LOCKED) so no two sessions ever reuse the same key. Server stays a blind relay - public keys only, never verified or decrypted. (AI Help from Claude code)
- Implement the Chat domain: create direct (1:1) chats, list/get chats, delete a chat, and set a disappearing-message timer. All authorization (membership + ADMIN role) is enforced in the service layer, returning 403 (never 404) so chat existence is never leaked. (AI Help from Claude code)

# Week 6
- Implement message handling with DTOs, entity definitions, and database migration for chat functionality, including message status and receipts.
- Implement the Message domain: send end-to-end encrypted messages (ciphertext stored/forwarded as an opaque blob, never decrypted), cursor-paginated history (newest first, by created_at + id), delivery/read receipts with a forward-only status rollup, and sender-only delete. Disappearing messages get an expires_at and are excluded from history once expired. (AI Help from Claude code)
- Implement the Task domain: in-chat collaboration boards with tasks (status, priority, assignee, due date, labels), a kanban board view grouped by status, list filtering by status/assignee, and a full activity log that automatically records every change (created, status/priority/title/description/due-date changes, and assignment). Any chat member can create and update tasks; only the creator or a chat admin can delete. All authorization is enforced in the service layer. (AI Help from Claude code)

# Week 7
- Implement realtime messaging over WebSocket (STOMP): clients connect to /ws authenticating with their JWT as a query parameter, then subscribe to per-chat topics. Messages sent over the socket are persisted and instantly fanned out to everyone in the chat; typing indicators and task-change events are also broadcast. Every WebSocket connection is authenticated at CONNECT (access token + active session) and every subscription is authorized - you can only subscribe to chats you belong to, or your own personal channel - with unauthorized attempts rejected via a STOMP ERROR frame. (AI Help from Claude code)
- Implement Redis-backed rate limiting (Bucket4j): per-endpoint token buckets that survive restarts and are shared across instances. Login (5/15min) and registration (3/hour) are limited per client IP to blunt brute-force and abuse; message send (60/min) and pre-key bundle fetch (10/hour) are limited per user, with a 200/min default for other authenticated endpoints. Exceeding a limit returns 429 with Retry-After and X-RateLimit-* headers. (AI Help from Claude code)
- Implement the disappearing-message cleanup job: a scheduled task (cron-driven) that hard-deletes messages past their expiry so ciphertext does not linger on disk. Receipts are removed automatically via the database cascade, and replies to a deleted message are safely nulled. History already hides expired messages, so this is the durable backstop. (AI Help from Claude code)
- Implement the identity-key fingerprint (safety number) endpoint: GET /api/users/{userId}/fingerprint returns the hex fingerprint of a user's public identity key so clients can compare it out-of-band and detect a man-in-the-middle. Also hardened error handling so malformed path/query parameters (e.g. a bad UUID or unknown enum) return 400 instead of 500 across the whole API. (AI Help from Claude code)
- Cleanup: removed unused build dependencies (the server-side Signal library, which the blind-relay server never calls, and MapStruct, which was never wired up) plus their orphan version properties. Smaller, clearer build with no behaviour change. (AI Help from Claude code)
- Refactor: extracted a single ChatAccessGuard that owns the "is this user a member / an admin of this chat?" rule, which was previously duplicated across the chat, message and task services and the WebSocket layer. The message, task and WebSocket components no longer inject the membership repository directly, and the authorization rule now lives in exactly one place so it can never drift. All 70 authorization smoke-test assertions still pass unchanged. (AI Help from Claude code)
- More cleanup: de-duplicated the "load user or 404" helper (now a single repository method) and the client-IP extraction (now a shared util), used across the services, auth controller and rate-limit filter. (AI Help from Claude code)
- Performance: eliminated N+1 queries - the chat-list screen now loads every chat's members in one batched query, and task labels load in batches (Hibernate @BatchSize) instead of one query per task. (AI Help from Claude code)
- Hardening: WebSocket message sends are now rate-limited too, sharing the same per-user bucket as the REST endpoint so the 60/min limit is unified across both transports; inbound WebSocket payloads are validated (reject missing chat id / empty ciphertext); and the JWT is dropped from the WebSocket session once the connection is authenticated so it can never leak into a logged message frame. (AI Help from Claude code)
- Testing: added the first real unit-test suite (JUnit 5 + Mockito) covering the authorization guard, the message delivery/receipt rules, chat-creation validation, and task creation with its activity log - including a regression test for the "task must set its creator" bug found earlier. (AI Help from Claude code)


Week 8: 
- feat: initialize frontend application with Angular setup
- feat: implement authentication service, guards, and token storage
- feat: implement login and registration components pages
- feat: implement chat functionality with user search and chat creation features
- feat: implement the client-side Signal key-setup (device provisioning) feature - the browser generates the identity key pair, registration id, a signed pre-key and a batch of one-time pre-keys with @privacyresearch/libsignal-protocol-typescript, stores the PRIVATE halves in IndexedDB (per user, never sent to the server), and publishes only the public halves to /api/signal. Real X3DH session establishment + Double Ratchet encrypt/decrypt are wired for the messaging feature; verified with an Alice→Bob round-trip. Added a Security page showing the safety number (identity fingerprint), registration id and remaining one-time pre-keys with replenish/reset, plus auto-provisioning on first sign-in.
- feat: implement the encrypted messaging view and live delivery - open a DIRECT chat at /chats/:id, decrypt history in ratchet order (cached once locally), and send by encrypting to the peer. Live delivery runs over WebSocket/STOMP (@stomp/stompjs): the client connects to /ws?token=<jwt>, subscribes to /topic/chat/{chatId} and renders peer messages the instant they arrive, with a Live/Connecting indicator, auto-reconnect + re-subscribe, and typing indicators. Sending stays on REST (returns the id so the sender can cache its own plaintext); the socket echo of our own message is de-duplicated by id. The server only ever sees opaque ciphertext. (AI Help)
- feat: add live delivery over WebSocket/STOMP - subscribe to /topic/chat/{chatId}, render peer messages instantly with auto-reconnect, plus typing indicators; REST send with id-based echo de-dup
- feat: enhance message handling for device provisioning and identity changes 
- Feat: added Kanban board integration and connection to backend
- Added new feature which will record every change (create, status/priority/title/description/due-date changes, assignment) automatically in the database, with service-layer authorization. On Every change, the activity log will be updated with the changes made to the task. This will help in tracking the changes made to the task and will also help in auditing the changes made to the task.
- Added option for filters for tasks for status and assignee. This will help in filtering the tasks based on the status and assignee. This will help in finding the tasks easily and will also help in tracking the progress of the tasks.
- Added a "clear completed tasks" feature - a single action on the board that deletes all the completed (DONE) tasks in a chat at once, so the board can be tidied without deleting them one by one. The same delete authorization applies: a chat admin clears every completed task, while a regular member clears only the completed tasks they created. The DONE column shows a Clear button (with a confirm), and other members' boards refresh live over WebSocket.
- Added security hardening - token rotation, IP trust, headers, prod guards. This will help in securing the application and will also help in preventing the attacks on the application.
- Dockerized the application and added Docker deployment configuration and secret generation script. This will help in deploying the application easily and will also help in managing the secrets easily.

### Resources
- [Spring Boot starter](https://start.spring.io/)
- [libsignal (Signal Protocol)](https://github.com/signalapp/libsignal)



# Commits

- Commit 1: Intial Repo setup to share with mentor and to start the project
- Commit 2: Week 1 work done, added claude chat links where I would be discussing about project with Claude AI
- Commit 3: Setup the project with signal library and other libraries that will be used in the project
- Commit 4: Refactor project configuration and add application profiles for development and production (Claude Code)
- Commit 5: Add JWT and security configuration classes with properties support
- Commit 6: Add custom exception classes for error handling in the application
- Commit 7: Implement JWT authentication with access and refresh token support, including user session management and error handling 
- Commit 8: Implement user authentication and session management with JWT support, including user registration, login, and session handling
- Commit 9: Implement Signal key management - identity keys, signed pre-keys, one-time pre-keys, and pre-key bundle distribution with atomic OTPK consumption
- Commit 10: Implement the Chat domain - direct (1:1) chats, membership and admin roles, and a disappearing-message timer, with service-layer authorization
- Commit 11: Implement message handling with DTOs, entity definitions, and database migration for chat functionality, including message status and receipts.
- Commit 12: Implement the Message domain - send encrypted messages, cursor-paginated history, delivery/read receipts, sender-only delete, and disappearing-message expiry
- Commit 13: Implement task management domain with entities, DTOs, and database migration for in-chat collaboration, including task creation, updates, activity logging, and priority/status handling.
- Commit 14: Implement the Task domain - in-chat tasks with status/priority/assignee/labels, kanban board, list filters, and an automatic activity log, with service-layer authorization
- Commit 15: Implement realtime messaging over WebSocket/STOMP - JWT-authenticated handshake, per-subscription authorization, live message/typing/task broadcast
- Commit 16: Implement Redis-backed per-endpoint rate limiting (Bucket4j) - per-IP login/register and per-user message/pre-key limits, 429 with Retry-After and X-RateLimit headers
- Commit 17: Implement the disappearing-message cleanup job - scheduled hard-delete of expired messages with cascading receipt removal
- Commit 18: Implement the identity-key fingerprint (safety number) endpoint, and return 400 (not 500) for malformed path/query parameters app-wide
- Commit 19: Remove unused dependencies (signal-client-java, MapStruct) and orphan version properties
- Commit 20: Extract ChatAccessGuard to centralize chat membership/admin authorization across services and the WebSocket layer
- Commit 21: De-duplicate the load-user-or-404 and client-IP helpers into shared code
- Commit 22: Fix N+1 queries in chat listing and task labels (batched member load + @BatchSize)
- Commit 23: Rate-limit and validate WebSocket message sends (shared per-user bucket, payload validation) and stop the JWT leaking into logged frames
- Commit 24: Add JUnit 5 + Mockito unit tests for authorization, message, chat and task service logic
- Commit 25: feat: initialize frontend application with Angular setup
- Commit 26: feat: implement authentication service, guards, and token storage
- Commit 27: feat: implement login and registration components pages
- Commit 28: feat: add user search endpoint (GET /api/users/search) for starting chats
- Commit 29: feat: implement chat list and create-chat dialog (direct) in the frontend
- Commit 30: feat: implement client-side Signal key-setup - browser keygen, IndexedDB private-key store, publish public keys to /api/signal, session + encrypt/decrypt helpers
- Commit 30: feat: implement chat functionality with user search and chat creation features
- Commit 31: feat: implement the encrypted messaging view (/chats/:id) - decrypt history in ratchet order with local plaintext caching, encrypt-and-send for direct chats
- Commit 32: feat: add live delivery over WebSocket/STOMP - subscribe to /topic/chat/{chatId}, render peer messages instantly with auto-reconnect, plus typing indicators; REST send with id-based echo de-dup
- Commit 33: updated the connection service and implemented with new service which checks for all the chats
- Commit 34: feat: enhance authentication flow with token refresh and user identity management
- Commit 35: feat: improve identity provisioning and synchronization in SignalService
- Commit 36: feat: enhance message handling for device provisioning and identity changes
- Commit 37: Feat: added Kanban board integration and connection to backend
- Commit 38: feat: Activity Log for Task domain - record every change (create, status/priority/title/description/due-date changes, assignment) automatically in the database, with service-layer authorization
- Commit 39: Feat: Added filters for tasks for status and assignee
- Commit 40: feat: security hardening - token rotation, IP trust, headers, prod guards
- Commit 41: feat: add Docker deployment configuration and secret generation script
- Commit 42: fix: update Dockerfile healthcheck to use 127.0.0.1 instead of localhost for nginx
- Commit 43: feat: add functionality to clear completed tasks in chat
- Commit 44: https certification added for deployment
- Commit 45: removed unused group chat functions


# Signal Protocol Implementation Errors
- Commit 34: Implemented the token refresh and user identity management, but there is an error in the implementation of the Signal Protocol. The error is that the client is not able to establish a secure session with the server. The error is due to the fact that the client is not able to generate a valid pre-key bundle for the server. The pre-key bundle is generated using the client's identity key and signed pre-key, but the server is not able to verify the signature of the pre-key bundle. This is because the server does not have access to the client's identity key, which is stored in IndexedDB on the client side. 

``` Took more than 9 hours and span of 2 days to fix the issue. The solution was to implement a new endpoint on the server that allows the client to send its identity key to the server, so that the server can verify the signature of the pre-key bundle. The new endpoint is /api/signal/identity, which accepts a POST request with the client's identity key in the request body. The server then stores the identity key in its database and uses it to verify the signature of the pre-key bundle when establishing a secure session with the client.```

# General References

- [The Double Ratchet: Security Notions and Proofs" - Cohn-Gordon et al. (2016), formal security proof of the protocol](https://eprint.iacr.org/2018/1037)

- [A Formal Security Analysis of the Signal Messaging Protocol" - Cohn-Gordon et al. (IEEE EuroS&P 2017)](https://eprint.iacr.org/2016/1013)
