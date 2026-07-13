# SecureChat API tests (Bruno)

A [Bruno](https://www.usebruno.com/) collection for exercising the backend directly, so you can
isolate whether a problem (e.g. "message not being sent") is on the **server** or the **client**.

## Setup

1. Install Bruno (desktop app) or the CLI: `npm i -g @usebruno/cli`.
2. Open this `APIs/` folder as a collection in Bruno.
3. Select the **Local** environment (top-right). It defines:
   - `baseUrl` = `http://localhost:8080`
   - `username` / `password` / `peerUsername` — edit these to the account you want to test.
   - `accessToken`, `refreshToken`, `userId`, `peerId`, `chatId`, `messageId` — filled in
     automatically by the pre/post scripts as you run requests.

## How the variable chaining works

Each request's **post-response script** saves what the next request needs, so you rarely type ids:

| Run this            | It saves                        |
|---------------------|---------------------------------|
| Login / Register    | `accessToken`, `refreshToken`, `userId` |
| Refresh Token       | new `accessToken`, `refreshToken` |
| Search Peer         | `peerId` (first match)          |
| Create Direct Chat  | `chatId`                        |
| List Chats          | `chatId` (first chat)           |
| Send Message        | `messageId`                     |

Every authenticated request uses `Authorization: Bearer {{accessToken}}`.

## Diagnosing "message not being sent"

Run these in order (Local env selected):

1. **Auth → Login**  → expect `200`, token stored.
2. **Chats → List Chats** (or **Create Direct Chat** after **Users → Search Peer**) → sets `chatId`.
3. **Messages → Send Message** → **expect `201`**.

Interpretation of step 3:

- **`201`** — the server send path is healthy. The message is persisted with a dummy ciphertext.
  So if the web app still fails to send, the problem is **client-side**: Signal encryption /
  establishing the session. Next, check **Signal → Get PreKey Bundle** for the peer:
  - `200` → peer is provisioned; the client should be able to encrypt.
  - `404` → the peer has **never published keys** → the client can't build a session, so it fails
    *before* it ever calls `/messages`. That user must open the web app once to provision.
- **`401`** — token expired → run **Auth → Refresh Token** (or **Login**) and retry.
- **`403`** — you're not a member of `{{chatId}}`.
- **`404`** — `{{chatId}}` is empty/wrong → run **List Chats** / **Create Direct Chat**.
- **`429`** — rate limited → wait and retry.

4. **Messages → Get History** → confirm the row is there.
5. **Messages → Delete Message** → removes the dummy row (`messageId` was saved for you).

## ⚠️ Note on the Signal key requests

`Signal → Upload Identity Key` / `Upload PreKeys` **overwrite the account's published keys**. Run
them only against a **throwaway account** (Register a fresh one first) — never against an account
that's logged into the web UI, or you'll desync that browser's end-to-end encryption keys.

The message endpoints (Send / History / Status / Delete) are safe: the server is a blind relay and
just stores/forwards opaque ciphertext.
