# Custom direct chat

Android direct messages use the Render WebSocket endpoint exclusively. The app
does not read or write message content through Supabase PostgREST or Realtime.

## Data ownership

- Room is the device's message history and outgoing-message outbox.
- Render authenticates the Supabase access token, derives the deterministic
  conversation ID, and verifies friendship and block state.
- Supabase `chat_delivery_queue` is server-only temporary delivery storage.
- After the receiving device commits a message to Room, its acknowledgement
  causes the server to erase the queued text and image URL.
- Firebase Cloud Messaging carries only a generic wake-up notification. Message
  content arrives through the authenticated WebSocket.

The queue retains an idempotency tombstone until expiry and undelivered content
expires after 72 hours. This design intentionally provides no cloud history,
multi-device history synchronization, editing, delete-for-everyone, or read
receipts.

## Configuration

Android requires:

```properties
CUSTOM_CHAT_SERVER_URL=wss://campus-map-backend-fpz8.onrender.com/chat
```

Render requires `CUSTOM_CHAT_ENABLED=true`, the existing Supabase service-role
configuration, and Firebase service-account configuration for push. Chat access
is available to authenticated users; the server still enforces friendship,
blocks, connection limits, payload limits, and per-user command rate limits.

`CHAT_PILOT_USER_IDS` and `USE_CUSTOM_CHAT_SERVER` are obsolete and ignored.

## Operational behavior

Render may sleep on its free tier, so the first connection can be delayed.
Pending outgoing messages remain in Room and retry after reconnect. A recipient
that reconnects within 72 hours receives queued messages in batches of 100.

Run Android compilation and tests after client changes. Run `node --check` on
the backend JavaScript files after server changes. A real release still needs a
two-device test covering foreground, background notification, offline sender,
offline recipient, blocking, and account switching.
