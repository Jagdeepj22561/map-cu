const test = require("node:test");
const assert = require("node:assert/strict");
const http = require("node:http");
const { once } = require("node:events");
const { randomUUID } = require("node:crypto");
const { WebSocket } = require("ws");
const { attachChatServer, validateMessage } = require("../chat-server");
const { chatIdFor } = require("../chat-store");
const A = "11111111-1111-4111-8111-111111111111";
const B = "22222222-2222-4222-8222-222222222222";

function packet(extra = {}) { return { type: "send", id: randomUUID(), recipientId: B, content: "hello", timestamp: Date.now(), ...extra }; }

async function fixture(t) {
  const rows = new Map();
  const pushes = [];
  let allowed = true;
  let fail = false;
  const store = {
    allowed: async () => allowed,
    enqueue: async row => {
      if (fail) throw new Error("Database unavailable");
      if (!rows.has(row.id)) rows.set(row.id, { ...row, created_at: new Date().toISOString() });
      return rows.get(row.id);
    },
    pending: async user => [...rows.values()].filter(row => row.recipient_id === user && !row.delivered_at),
    ack: async (user, id) => {
      const row = rows.get(id);
      if (row?.recipient_id === user) Object.assign(row, { delivered_at: new Date().toISOString(), content: "", image_url: null });
    },
    cleanup: async () => {},
  };
  const server = http.createServer();
  const close = attachChatServer(server, {
    store, notify: async row => pushes.push(row.id), enabledUsers: new Set([A, B]),
    authenticate: async token => [A, B].includes(token) ? { id: token, expiresInMs: 60000 } : null,
  });
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  t.after(async () => { close(); await new Promise(resolve => server.close(resolve)); });
  async function client(user) {
    const ws = new WebSocket(`ws://127.0.0.1:${server.address().port}/chat`);
    const messages = [];
    ws.on("message", data => messages.push(JSON.parse(data)));
    await once(ws, "open");
    const next = async type => {
      const end = Date.now() + 3000;
      while (Date.now() < end) {
        const index = messages.findIndex(message => message.type === type);
        if (index >= 0) return messages.splice(index, 1)[0];
        await new Promise(resolve => setTimeout(resolve, 5));
      }
      throw new Error(`Timed out waiting for ${type}: ${JSON.stringify(messages)}`);
    };
    if (user) { ws.send(JSON.stringify({ type: "auth", token: user })); await next("ready"); }
    return { ws, next, send: body => ws.send(JSON.stringify(body)), messages };
  }
  return { client, rows, pushes, setAllowed: value => { allowed = value; }, failWrites: () => { fail = true; } };
}

test("validates content, recipient, retry age and server-derived identity", () => {
  const input = packet({ sender_id: B, chat_id: randomUUID() });
  const row = validateMessage(input, A);
  assert.equal(row.sender_id, A);
  assert.equal(row.chat_id, chatIdFor(A, B));
  assert.equal(chatIdFor(A, B), chatIdFor(B, A));
  for (const bad of [{ recipientId: A }, { recipientId: "invalid" }, { content: "x".repeat(8001) },
    { content: "" }, { timestamp: Date.now() - 73 * 3600000 }, { imageUrl: "file:///secret" }]) {
    assert.throws(() => validateMessage(packet(bad), A));
  }
});

test("unauthenticated connections cannot send", async t => {
  const f = await fixture(t);
  const c = await f.client();
  c.send(packet());
  await c.next("error");
  assert.equal(f.rows.size, 0);
});

test("durable offline queue, reconnect delivery, recipient ACK and deduplication", async t => {
  const f = await fixture(t);
  const a = await f.client(A);
  const input = packet();
  a.send(input);
  assert.equal((await a.next("accepted")).id, input.id);
  assert.equal(f.rows.get(input.id).content, "hello");
  const b = await f.client(B);
  assert.equal((await b.next("message")).message.id, input.id);
  // Sender cannot acknowledge another user's receipt.
  a.send({ type: "ack", id: input.id }); await a.next("acked");
  assert.equal(f.rows.get(input.id).delivered_at, undefined);
  b.send({ type: "ack", id: input.id }); await b.next("acked");
  assert.equal(f.rows.get(input.id).content, "");
  a.send(input); await a.next("accepted");
  assert.equal(f.rows.size, 1);
  b.send({ type: "sync" }); await b.next("synced");
  assert.equal(b.messages.filter(item => item.type === "message").length, 0);
});

test("live delivery accepts back-to-back ACK and sync without disconnect", async t => {
  const f = await fixture(t);
  const a = await f.client(A);
  const b = await f.client(B);
  a.send(packet()); await a.next("accepted");
  const event = await b.next("message");
  b.send({ type: "ack", id: event.message.id });
  b.send({ type: "sync" });
  await b.next("acked"); await b.next("synced");
  assert.equal(b.ws.readyState, WebSocket.OPEN);
});

test("blocked users and users outside the pilot cannot receive new messages", async t => {
  const f = await fixture(t);
  const a = await f.client(A);
  f.setAllowed(false);
  a.send(packet()); await a.next("error");
  f.setAllowed(true);
  a.send(packet({ recipientId: randomUUID() })); await a.next("error");
  assert.equal(f.rows.size, 0);
});

test("database failure never acknowledges a send", async t => {
  const f = await fixture(t);
  const a = await f.client(A);
  f.failWrites(); a.send(packet()); await a.next("error");
  assert.equal(a.messages.some(item => item.type === "accepted"), false);
});

test("block added while recipient was offline suppresses pending delivery", async t => {
  const f = await fixture(t);
  const a = await f.client(A);
  const input = packet(); a.send(input); await a.next("accepted");
  f.setAllowed(false);
  const b = await f.client(B);
  b.send({ type: "sync" }); await b.next("synced");
  assert.equal(b.messages.some(item => item.type === "message"), false);
  assert.equal(f.rows.get(input.id).content, "");
});
