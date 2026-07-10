
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

# General References

- [The Double Ratchet: Security Notions and Proofs" — Cohn-Gordon et al. (2016), formal security proof of the protocol](https://eprint.iacr.org/2018/1037)

- [A Formal Security Analysis of the Signal Messaging Protocol" — Cohn-Gordon et al. (IEEE EuroS&P 2017)](https://eprint.iacr.org/2016/1013)
