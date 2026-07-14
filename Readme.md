
# Project


- info: Doing project alone so much of my meetings and all are just claude chats

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
- Implement Signal key management: store public identity keys, signed pre-keys and one-time pre-keys, and serve pre-key bundles so peers can start end-to-end encrypted sessions. One-time pre-keys are consumed atomically (FOR UPDATE SKIP LOCKED) so no two sessions ever reuse the same key. Server stays a blind relay — public keys only, never verified or decrypted. (AI Help from Claude code)
- Implement the Chat domain: create DIRECT and GROUP chats, list/get chats, update group info, add/remove members, and set a disappearing-message timer. All authorization (membership + ADMIN role) is enforced in the service layer, returning 403 (never 404) so chat existence is never leaked. (AI Help from Claude code)

# Week 6
- Implement message handling with DTOs, entity definitions, and database migration for chat functionality, including message status and receipts.
- Implement the Message domain: send end-to-end encrypted messages (ciphertext stored/forwarded as an opaque blob, never decrypted), cursor-paginated history (newest first, by created_at + id), delivery/read receipts with a forward-only status rollup, and sender-only delete. Disappearing messages get an expires_at and are excluded from history once expired. (AI Help from Claude code)
- Implement the Task domain: in-chat collaboration boards with tasks (status, priority, assignee, due date, labels), a kanban board view grouped by status, list filtering by status/assignee, and a full activity log that automatically records every change (created, status/priority/title/description/due-date changes, and assignment). Any chat member can create and update tasks; only the creator or a chat admin can delete. All authorization is enforced in the service layer. (AI Help from Claude code)

# Week 7
- Implement realtime messaging over WebSocket (STOMP): clients connect to /ws authenticating with their JWT as a query parameter, then subscribe to per-chat topics. Messages sent over the socket are persisted and instantly fanned out to everyone in the chat; typing indicators and task-change events are also broadcast. Every WebSocket connection is authenticated at CONNECT (access token + active session) and every subscription is authorized — you can only subscribe to chats you belong to, or your own personal channel — with unauthorized attempts rejected via a STOMP ERROR frame. (AI Help from Claude code)
- Implement Redis-backed rate limiting (Bucket4j): per-endpoint token buckets that survive restarts and are shared across instances. Login (5/15min) and registration (3/hour) are limited per client IP to blunt brute-force and abuse; message send (60/min) and pre-key bundle fetch (10/hour) are limited per user, with a 200/min default for other authenticated endpoints. Exceeding a limit returns 429 with Retry-After and X-RateLimit-* headers. (AI Help from Claude code)
- Implement the disappearing-message cleanup job: a scheduled task (cron-driven) that hard-deletes messages past their expiry so ciphertext does not linger on disk. Receipts are removed automatically via the database cascade, and replies to a deleted message are safely nulled. History already hides expired messages, so this is the durable backstop. (AI Help from Claude code)
- Implement the identity-key fingerprint (safety number) endpoint: GET /api/users/{userId}/fingerprint returns the hex fingerprint of a user's public identity key so clients can compare it out-of-band and detect a man-in-the-middle. Also hardened error handling so malformed path/query parameters (e.g. a bad UUID or unknown enum) return 400 instead of 500 across the whole API. (AI Help from Claude code)
- Cleanup: removed unused build dependencies (the server-side Signal library, which the blind-relay server never calls, and MapStruct, which was never wired up) plus their orphan version properties. Smaller, clearer build with no behaviour change. (AI Help from Claude code)
- Refactor: extracted a single ChatAccessGuard that owns the "is this user a member / an admin of this chat?" rule, which was previously duplicated across the chat, message and task services and the WebSocket layer. The message, task and WebSocket components no longer inject the membership repository directly, and the authorization rule now lives in exactly one place so it can never drift. All 70 authorization smoke-test assertions still pass unchanged. (AI Help from Claude code)
- More cleanup: de-duplicated the "load user or 404" helper (now a single repository method) and the client-IP extraction (now a shared util), used across the services, auth controller and rate-limit filter. (AI Help from Claude code)
- Performance: eliminated N+1 queries — the chat-list screen now loads every chat's members in one batched query, and task labels load in batches (Hibernate @BatchSize) instead of one query per task. (AI Help from Claude code)
- Hardening: WebSocket message sends are now rate-limited too, sharing the same per-user bucket as the REST endpoint so the 60/min limit is unified across both transports; inbound WebSocket payloads are validated (reject missing chat id / empty ciphertext); and the JWT is dropped from the WebSocket session once the connection is authenticated so it can never leak into a logged message frame. (AI Help from Claude code)
- Testing: added the first real unit-test suite (JUnit 5 + Mockito) covering the authorization guard, the message delivery/receipt rules, chat-creation validation, and task creation with its activity log — including a regression test for the "task must set its creator" bug found earlier. (AI Help from Claude code)


Week 8: 
- feat: initialize frontend application with Angular setup
- feat: implement authentication service, guards, and token storage
- feat: implement login and registration components pages
- feat: implement chat functionality with user search and chat creation features
- feat: implement the client-side Signal key-setup (device provisioning) feature — the browser generates the identity key pair, registration id, a signed pre-key and a batch of one-time pre-keys with @privacyresearch/libsignal-protocol-typescript, stores the PRIVATE halves in IndexedDB (per user, never sent to the server), and publishes only the public halves to /api/signal. Real X3DH session establishment + Double Ratchet encrypt/decrypt are wired for the messaging feature; verified with an Alice→Bob round-trip. Added a Security page showing the safety number (identity fingerprint), registration id and remaining one-time pre-keys with replenish/reset, plus auto-provisioning on first sign-in.
- feat: implement the encrypted messaging view and live delivery — open a DIRECT chat at /chats/:id, decrypt history in ratchet order (cached once locally), and send by encrypting to the peer. Live delivery runs over WebSocket/STOMP (@stomp/stompjs): the client connects to /ws?token=<jwt>, subscribes to /topic/chat/{chatId} and renders peer messages the instant they arrive, with a Live/Connecting indicator, auto-reconnect + re-subscribe, and typing indicators. Sending stays on REST (returns the id so the sender can cache its own plaintext); the socket echo of our own message is de-duplicated by id. Group chats show an "encryption not available yet" notice; the server only ever sees opaque ciphertext. (AI Help)
- feat: add live delivery over WebSocket/STOMP — subscribe to /topic/chat/{chatId}, render peer messages instantly with auto-reconnect, plus typing indicators; REST send with id-based echo de-dup
- feat: enhance message handling for device provisioning and identity changes 
- Feat: added Kanban board integration and connection to backend
- Added new feature which will record every change (create, status/priority/title/description/due-date changes, assignment) automatically in the database, with service-layer authorization. On Every change, the activity log will be updated with the changes made to the task. This will help in tracking the changes made to the task and will also help in auditing the changes made to the task.

### Resources
- [Spring Boot starter](https://start.spring.io/)
- [libsignal (Signal Protocol)](https://github.com/signalapp/libsignal)



# Commits

Commit 1: Intial Repo setup to share with mentor and to start the project
Commit 2: Week 1 work done, added claude chat links where I would be discussing about project with Claude AI
Commit 3: Setup the project with signal library and other libraries that will be used in the project
Commit 4: Refactor project configuration and add application profiles for development and production (Claude Code)
Commit 5: Add JWT and security configuration classes with properties support
Commit 6: Add custom exception classes for error handling in the application
Commit 7: Implement JWT authentication with access and refresh token support, including user session management and error handling 
Commit 8: Implement user authentication and session management with JWT support, including user registration, login, and session handling
Commit 9: Implement Signal key management — identity keys, signed pre-keys, one-time pre-keys, and pre-key bundle distribution with atomic OTPK consumption
Commit 10: Implement the Chat domain — DIRECT/GROUP chats, membership and admin roles, member management, and disappearing-message timer, with service-layer authorization
Commit 11: Implement message handling with DTOs, entity definitions, and database migration for chat functionality, including message status and receipts.
Commit 12: Implement the Message domain — send encrypted messages, cursor-paginated history, delivery/read receipts, sender-only delete, and disappearing-message expiry
Commit 13: Implement task management domain with entities, DTOs, and database migration for in-chat collaboration, including task creation, updates, activity logging, and priority/status handling.
Commit 14: Implement the Task domain — in-chat tasks with status/priority/assignee/labels, kanban board, list filters, and an automatic activity log, with service-layer authorization
Commit 15: Implement realtime messaging over WebSocket/STOMP — JWT-authenticated handshake, per-subscription authorization, live message/typing/task broadcast
Commit 16: Implement Redis-backed per-endpoint rate limiting (Bucket4j) — per-IP login/register and per-user message/pre-key limits, 429 with Retry-After and X-RateLimit headers
Commit 17: Implement the disappearing-message cleanup job — scheduled hard-delete of expired messages with cascading receipt removal
Commit 18: Implement the identity-key fingerprint (safety number) endpoint, and return 400 (not 500) for malformed path/query parameters app-wide
Commit 19: Remove unused dependencies (signal-client-java, MapStruct) and orphan version properties
Commit 20: Extract ChatAccessGuard to centralize chat membership/admin authorization across services and the WebSocket layer
Commit 21: De-duplicate the load-user-or-404 and client-IP helpers into shared code
Commit 22: Fix N+1 queries in chat listing and task labels (batched member load + @BatchSize)
Commit 23: Rate-limit and validate WebSocket message sends (shared per-user bucket, payload validation) and stop the JWT leaking into logged frames
Commit 24: Add JUnit 5 + Mockito unit tests for authorization, message, chat and task service logic
Commit 25: feat: initialize frontend application with Angular setup
Commit 26: feat: implement authentication service, guards, and token storage
Commit 27: feat: implement login and registration components pages
Commit 28: feat: add user search endpoint (GET /api/users/search) for starting chats
Commit 29: feat: implement chat list and create-chat dialog (direct/group) in the frontend
Commit 30: feat: implement client-side Signal key-setup — browser keygen, IndexedDB private-key store, publish public keys to /api/signal, session + encrypt/decrypt helpers
Commit 30: feat: implement chat functionality with user search and chat creation features
Commit 31: feat: implement the encrypted messaging view (/chats/:id) — decrypt history in ratchet order with local plaintext caching, encrypt-and-send for direct chats; group chats flagged unsupported
Commit 32: feat: add live delivery over WebSocket/STOMP — subscribe to /topic/chat/{chatId}, render peer messages instantly with auto-reconnect, plus typing indicators; REST send with id-based echo de-dup
Commit 33: updated the connection service and implemented with new service which checks for all the chats
Commit 34: feat: enhance authentication flow with token refresh and user identity management
Commit 35: feat: improve identity provisioning and synchronization in SignalService
Commit 36: feat: enhance message handling for device provisioning and identity changes
Commit 37: Feat: added Kanban board integration and connection to backend
Commit 38: feat: Activity Log for Task domain — record every change (create, status/priority/title/description/due-date changes, assignment) automatically in the database, with service-layer authorization



# Signal Protocol Implementation Errors
- Commit 34: Implemented the token refresh and user identity management, but there is an error in the implementation of the Signal Protocol. The error is that the client is not able to establish a secure session with the server. The error is due to the fact that the client is not able to generate a valid pre-key bundle for the server. The pre-key bundle is generated using the client's identity key and signed pre-key, but the server is not able to verify the signature of the pre-key bundle. This is because the server does not have access to the client's identity key, which is stored in IndexedDB on the client side. 

``` Took more than 9 hours and span of 2 days to fix the issue. The solution was to implement a new endpoint on the server that allows the client to send its identity key to the server, so that the server can verify the signature of the pre-key bundle. The new endpoint is /api/signal/identity, which accepts a POST request with the client's identity key in the request body. The server then stores the identity key in its database and uses it to verify the signature of the pre-key bundle when establishing a secure session with the client.```

# General References

- [The Double Ratchet: Security Notions and Proofs" — Cohn-Gordon et al. (2016), formal security proof of the protocol](https://eprint.iacr.org/2018/1037)

- [A Formal Security Analysis of the Signal Messaging Protocol" — Cohn-Gordon et al. (IEEE EuroS&P 2017)](https://eprint.iacr.org/2016/1013)
