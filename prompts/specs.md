# ADB WebSocket relay — implementation specification (v1)

This document is **normative** for building interoperable relay servers (`adb-relay-python`, `adb-relay-go`, `adb-relay-node`, …). It describes only the **relay** layer: pairing two WebSocket clients and forwarding bytes. It also references the **mux** format that **dev bridge** and **device agent** put inside WebSocket **binary** payloads after the handshake; **relays MUST NOT parse or modify mux payloads**.

For product context (when to use VPN vs relay), see the human-oriented doc in **android-ide-extension**: `docs/remote-adb-bridge-protocol.md`.

---

## 1. Definitions

| Term | Meaning |
|------|--------|
| **Relay** | A server that accepts WebSocket connections, performs a one-message JSON **handshake**, pairs one `dev` client with one `device` client per **session token**, then forwards **binary** WebSocket messages between the pair. |
| **Client** | Either a **dev** peer or a **device** peer (bridge, IDE extension host, Android app, LAN agent, etc.). |
| **Session token** | A shared secret string, agreed out-of-band; used only for pairing at the relay. |
| **Mux frame** | Binary structure (magic `ADBM`) carried inside WebSocket binary payloads after handshake; opaque to the relay. |

---

## 2. Transport

- **URI scheme**: `ws://` or `wss://` (TLS recommended in production).
- **HTTP path**: relay MUST accept WebSocket upgrade on path **`/`** (single slash). Implementations MAY additionally accept other paths for deployment convenience; reference clients use the URL root (e.g. `ws://host:18765/`).
- **Subprotocol**: none required; clients MUST NOT assume a WebSocket subprotocol is negotiated.
- **Framing**: after handshake, clients send **WebSocket binary messages** only (see §4). The relay forwards each received binary message as **one** binary message to the peer (same payload bytes; FIN / fragmentation behavior follows the WebSocket library; peers MUST accept arbitrary binary message sizes up to implementation limits).

---

## 3. Roles

| `role` (handshake) | Typical process |
|--------------------|-----------------|
| `dev` | Muxes host `adb` TCP connections to one WebSocket toward the relay. |
| `device` | Demuxes WebSocket to TCP toward `adbd` (or equivalent). |

Exactly **one** `dev` socket and **one** `device` socket form a **pair** for a given pairing event.

---

## 4. Handshake (first application message)

### 4.1 Ordering

1. Client opens a WebSocket to the relay.
2. The **first** application-layer message from the client MUST be a **WebSocket text** frame whose payload is **UTF-8** JSON (single JSON object).
3. If the first message is **binary**, the relay MUST **fail** the connection (close WebSocket; implementation-defined close code, e.g. `1003` Unsupported Data or `1008` Policy Violation).
4. If the first message is text but not valid JSON / not a valid handshake object (§4.2), the relay MUST **fail** the connection.

### 4.2 JSON schema (v1)

Object fields:

| Field | JSON type | Required | Value |
|-------|-----------|----------|--------|
| `v` | number | yes | Must be exactly **1**. |
| `role` | string | yes | Exactly **`"dev"`** or **`"device"`**. |
| `token` | string | yes | Non-empty string. |

**Unknown JSON keys** on the handshake object: relay SHOULD ignore them (forward compatibility). **Invalid types** (e.g. `v` as string): MUST reject.

**Examples (normative shape):**

```json
{"v":1,"role":"dev","token":"s3cr3t"}
```

```json
{"v":1,"role":"device","token":"s3cr3t"}
```

### 4.3 After successful handshake

- From the client: **only** WebSocket **binary** messages are allowed for the mux byte stream (per product protocol). If the relay receives **text** after handshake, reference behavior is to **close** the connection; relays MAY mirror that.
- From the relay: forwards **binary** payloads **unchanged** (byte-for-byte equality of the WebSocket binary message payload) to the paired peer.

The relay does **not** interpret concatenated mux frames inside binary payloads.

---

## 5. Pairing algorithm (relay core)

The relay maintains two mappings (conceptually):

- `pending_dev[token] → pending_state`
- `pending_device[token] → pending_state`

Each `pending_state` holds at least: the WebSocket session, a **FIFO buffer** of binary payloads received **after** handshake **before** pairing completes.

### 5.1 Registration

When a client completes a valid handshake with (`role`, `token`):

1. If `role == "device"`:
   - If `pending_dev[token]` exists: **remove** it as `other`, **pair** `other` with this socket (order: `other` = dev side, `this` = device side for flushing below). Do **not** leave either entry in the pending maps.
   - Else: store this socket in `pending_device[token]`.
2. If `role == "dev"`:
   - If `pending_device[token]` exists: **remove** it as `other`, **pair** `this` with `other`.
   - Else: store this socket in `pending_dev[token]`.

### 5.2 Pairing completion (finalize)

When two sockets `A` and `B` are paired (one dev, one device):

1. **Flush buffers** (FIFO order):
   - Every binary payload buffered for `A` MUST be sent to `B` as binary WebSocket messages, in order.
   - Every binary payload buffered for `B` MUST be sent to `A` as binary WebSocket messages, in order.
2. **Enable forwarding**: for every subsequent binary message received on either socket, send the same payload to the opposite socket.
3. **Close coupling**: when one socket of the pair is closed (cleanly or not), the relay SHOULD **close** the peer socket (reference behavior: close peer once).

### 5.3 Concurrency

Pairing and map updates for a given relay instance MUST be **race-safe** (mutex, single-threaded event loop, or equivalent). Double registration for the same `token` and same `role` before a match (e.g. two `dev` with same token): **normative reference behavior** is **last registration wins** — the earlier pending waiter is replaced in the map and will **not** be paired unless it disconnects and is removed; implementers SHOULD document this limitation. Production deployments SHOULD use **unique high-entropy tokens per session** so this case does not occur.

### 5.4 Disconnect before pair

If a socket is still present in `pending_dev` or `pending_device` and the connection drops, the relay MUST **remove** that pending entry for the `(token, role)` if the stored session is still that socket.

---

## 6. Default networking (informative)

Reference **Node** relay listens on **all interfaces** (implicit `0.0.0.0`) with TCP port **18765** by default. The **JVM** reference relay uses the same default bind (`0.0.0.0`) so phones on LAN can open `ws://<PC-LAN-IP>:18765`. Use `127.0.0.1` only when every client is on the same host. Production often terminates TLS on a reverse proxy and forwards WebSocket to the relay.

Environment variables (reference CLI only, not protocol):

- `RELAY_PORT` — listen port.
- `RELAY_HOST` — bind address.

---

## 7. Mux format inside binary payloads (for bridge/agent authors)

Relays ignore this section; **dev** and **device** implementations MUST implement it to interoperate with **android-ide-extension** and **adb-relay-android**.

After handshake, each WebSocket **binary** message payload is a byte sequence consisting of **zero or more concatenated mux frames**. Receivers MUST parse sequentially; incomplete mux frames MUST be retained in a buffer until complete.

### 7.1 Mux frame layout (v1)

All multi-byte integers: **unsigned, big-endian**.

| Offset | Length | Field |
|--------|--------|--------|
| 0 | 4 | Magic **ADBM** — bytes `0x41 0x44 0x42 0x4D` |
| 4 | 1 | Mux format version — must be **1** |
| 5 | 1 | Message type (see §7.2) |
| 6 | 2 | Reserved — must be **0** |
| 8 | 4 | `channelId` |
| 12 | 4 | `payloadLength` = **N** |
| 16 | N | Payload (may be empty if N = 0) |

Total mux frame size = **16 + N** bytes.

### 7.2 Message types

| Value | Name | Allowed direction | Semantics |
|-------|------|-------------------|-----------|
| 0 | DATA | both | Payload is raw TCP bytes for `channelId`. |
| 1 | OPEN | dev → device | Open logical TCP channel; `channelId` chosen by dev, MUST be **non-zero**. Device opens TCP to `adbd` (or configured target) and associates it with `channelId`. |
| 2 | CLOSE | both | Tear down `channelId`; payload ignored. `channelId` MUST be **non-zero**. |

### 7.3 `channelId` rules (v1)

- **0** is reserved; MUST NOT be used for OPEN / DATA / CLOSE.
- Dev allocates increasing `channelId` (e.g. starting at 1).
- Device MUST NOT reassign an in-use `channelId`.

---

## 8. Reconnect and lifecycle (informative for full stack)

- **Relay**: stateless regarding mux; each new WebSocket pair requires a new handshake and new token agreement if the old session ended.
- **Device agent / app**: SHOULD retry WebSocket with backoff and re-send handshake; mux channels are scoped to one WebSocket pair.
- **Dev bridge (IDE)**: on relay drop, clears mux state and reconnects WebSocket with backoff; may keep local TCP listener behavior as in `docs/remote-adb-bridge-protocol.md`.

Implementations of **relay-only** servers do not need to implement reconnect logic beyond correct **close** handling.

---

## 9. Security (normative intent)

- Session **token** MUST be treated as a secret (high entropy; rotate / single-use for untrusted networks).
- Relay MUST NOT pair sockets that have not presented the **same** `token` in handshake.
- Use **`wss://`** and TLS termination appropriate to the deployment.

---

## 10. Compliance checklist (relay server)

Implementations claiming compatibility with this spec SHOULD verify:

- [ ] WebSocket upgrade on `/` (or documented equivalent).
- [ ] First client message: text JSON; reject binary first.
- [ ] Validate `v`, `role`, `token` per §4.2.
- [ ] Pair `dev` + `device` with identical `token`; buffer binary until paired; flush FIFO then bridge.
- [ ] Forward binary payloads unchanged; do not inspect mux.
- [ ] On disconnect: remove pending entry; if paired, close peer.
- [ ] Thread-safe pairing maps.

---

## 11. Reference code (informative)

| Component | Repository / path |
|-----------|-------------------|
| Relay (Node) | `android-ide-extension/tools/remote-adb-relay/relay.cjs` |
| Relay (JVM / Kotlin) | `adb-relay-jvm` (this repo) |
| Mux + dev bridge | `android-ide-extension/src/remoteAdb/` |
| LAN device agent | `android-ide-extension/tools/remote-adb-relay/agent.cjs` |
| On-device app | `adb-relay-android` |

**Spec version**: document **1** (`handshake.v` and mux format byte). Bump `v` / mux version only with a new spec revision.
