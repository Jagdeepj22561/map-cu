const { WebSocketServer, WebSocket } = require("ws");
const { chatIdFor } = require("./chat-store");
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const TTL = 72 * 60 * 60 * 1000;
const rejected = message => Object.assign(new Error(message), { retryable: false });

function validateMessage(input, sender) {
  if (!UUID.test(input.id) || !UUID.test(input.recipientId) || input.recipientId === sender) throw rejected("Invalid recipient or ID");
  if (typeof input.content !== "string" || Buffer.byteLength(input.content) > 8000) throw rejected("Invalid content");
  if (input.imageUrl != null && (typeof input.imageUrl !== "string" || input.imageUrl.length > 2048 || !input.imageUrl.startsWith("https://"))) throw rejected("Invalid image URL");
  if (!input.content.trim() && !input.imageUrl) throw rejected("Empty message");
  // Do not resend old local outbox entries after the idempotency window ends.
  if (!Number.isSafeInteger(input.timestamp) || input.timestamp < Date.now() - TTL || input.timestamp > Date.now() + 300000) throw rejected("Message expired or device clock incorrect");
  return {
    id: input.id, chat_id: chatIdFor(sender, input.recipientId), sender_id: sender,
    recipient_id: input.recipientId, content: input.content, image_url: input.imageUrl || null,
    type: input.imageUrl ? "IMAGE" : "TEXT",
    expires_at: new Date(input.timestamp + TTL).toISOString(),
  };
}

function attachChatServer(server, { authenticate, store, notify = async () => {}, enabledUsers = new Set() }) {
  const wss = new WebSocketServer({ noServer: true, maxPayload: 16384, perMessageDeflate: false });
  const sessions = new Map();
  const rates = new Map();
  const send = (ws, body) => {
    if (ws.readyState !== WebSocket.OPEN) return;
    if (ws.bufferedAmount > 256 * 1024) return ws.close(1013, "Slow consumer");
    ws.send(JSON.stringify(body));
  };
  const deliver = (ws, row) => send(ws, { type: "message", message: row });
  const drain = async (ws) => {
    const rows = await store.pending(ws.userId);
    for (const row of rows) {
      // A block or removed friendship also suppresses queued delivery.
      if (await store.allowed(row.sender_id, row.recipient_id)) deliver(ws, row);
      else await store.ack(ws.userId, row.id);
    }
  };
  const upgrade = (req, socket, head) => {
    if (req.url !== "/chat" || wss.clients.size >= 200) return socket.destroy();
    wss.handleUpgrade(req, socket, head, ws => wss.emit("connection", ws));
  };
  server.on("upgrade", upgrade);
  wss.on("connection", ws => {
    ws.alive = true;
    const authTimeout = setTimeout(() => ws.close(1008, "Authentication required"), 10000);
    let expiryTimer;
    let queued = 0;
    let chain = Promise.resolve();
    ws.on("pong", () => { ws.alive = true; });
    ws.on("error", () => {});
    ws.on("message", raw => {
      if (++queued > 128) return ws.close(1008, "Too many pending commands");
      chain = chain.then(async () => {
      if (ws.readyState !== WebSocket.OPEN) { queued--; return; }
      let input;
      try {
        input = JSON.parse(raw.toString());
        if (!ws.userId) {
          if (input.type !== "auth" || typeof input.token !== "string") throw new Error("Authentication required");
          const user = await authenticate(input.token);
          if (!user || !enabledUsers.has(user.id)) throw new Error("Chat pilot not enabled for account");
          const peers = sessions.get(user.id) || new Set();
          if (peers.size >= 3 || ws.readyState !== WebSocket.OPEN) throw new Error("Connection limit");
          ws.userId = user.id;
          peers.add(ws);
          sessions.set(user.id, peers);
          clearTimeout(authTimeout);
          // Force verification again at token expiry (also caps session duration).
          expiryTimer = setTimeout(() => ws.close(1008, "Refresh session"), Math.min(3600000, user.expiresInMs));
          send(ws, { type: "ready" });
          await drain(ws);
        } else {
          let rate = rates.get(ws.userId);
          if (!rate || Date.now() > rate.until) { rate = { count: 0, until: Date.now() + 60000 }; rates.set(ws.userId, rate); }
          if (++rate.count > 240) return ws.close(1008, "Rate limit");
          if (input.type === "send") {
            const message = validateMessage(input, ws.userId);
            if (!enabledUsers.has(message.recipient_id)) throw rejected("Recipient is not in chat pilot");
            if (!await store.allowed(ws.userId, message.recipient_id)) throw rejected("Messaging not allowed");
            const row = await store.enqueue(message);
            // The sender only marks locally synced after durable acceptance.
            send(ws, { type: "accepted", id: row.id });
            if (!row.delivered_at) {
              const recipients = sessions.get(row.recipient_id);
              for (const peer of recipients || []) deliver(peer, row);
              // Generic push contains no chat content. The app retrieves the queue.
              // A socket can disappear before recipient ACK. Push even when connected
              // so this foreground/background race cannot suppress the notification.
              void notify(row).catch(() => console.warn("Chat push failed; message remains queued"));
            }
          } else if (input.type === "ack" && UUID.test(input.id)) {
            await store.ack(ws.userId, input.id);
            send(ws, { type: "acked", id: input.id });
          } else if (input.type === "sync") {
            await drain(ws);
            send(ws, { type: "synced" });
          } else throw new Error("Unknown command");
        }
      } catch (error) {
        send(ws, { type: "error", id: UUID.test(input?.id) ? input.id : null, retryable: error.retryable !== false,
          message: "Chat operation failed. Check connection, friendship and pilot access." });
        if (!ws.userId) ws.close(1008, "Authentication failed");
        // Never log tokens, payloads or provider error objects.
      } finally { queued--; }
      });
    });
    ws.on("close", () => {
      clearTimeout(authTimeout);
      clearTimeout(expiryTimer);
      const peers = sessions.get(ws.userId);
      peers?.delete(ws);
      if (peers?.size === 0) sessions.delete(ws.userId);
    });
  });
  const heartbeat = setInterval(() => {
    for (const ws of wss.clients) {
      if (!ws.alive) { ws.terminate(); continue; }
      ws.alive = false;
      ws.ping();
    }
    for (const [id, rate] of rates) if (Date.now() > rate.until) rates.delete(id);
  }, 30000);
  const cleanup = () => store.cleanup().catch(() => console.warn("Chat queue cleanup failed"));
  void cleanup();
  const janitor = setInterval(cleanup, 3600000);
  heartbeat.unref(); janitor.unref();
  return () => {
    clearInterval(heartbeat); clearInterval(janitor);
    server.off("upgrade", upgrade);
    for (const ws of wss.clients) ws.terminate();
    wss.close();
  };
}
module.exports = { attachChatServer, validateMessage };
