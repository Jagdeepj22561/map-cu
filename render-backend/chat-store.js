const { createHash } = require("node:crypto");

// Matches Android UUID.nameUUIDFromBytes(sorted user IDs joined by ':').
function chatIdFor(a, b) {
  const bytes = createHash("md5").update([a, b].sort().join(":")).digest();
  bytes[6] = (bytes[6] & 15) | 48;
  bytes[8] = (bytes[8] & 63) | 128;
  const hex = bytes.toString("hex");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function createChatStore(db) {
  const checked = async (query) => {
    const { data, error } = await query;
    if (error) throw error;
    return data;
  };
  return {
    async allowed(sender, recipient) {
      const [friends, blocks] = await Promise.all([
        checked(db.from("friendships").select("friend_id").eq("user_id", sender).eq("friend_id", recipient).limit(1)),
        checked(db.from("blocks").select("user_id").or(
          `and(user_id.eq.${sender},blocked_user_id.eq.${recipient}),and(user_id.eq.${recipient},blocked_user_id.eq.${sender})`
        ).limit(1)),
      ]);
      return friends.length > 0 && blocks.length === 0;
    },
    async enqueue(message) {
      // INSERT ON CONFLICT DO NOTHING preserves original payload and expiry.
      await checked(db.from("chat_delivery_queue").upsert(message, { onConflict: "id", ignoreDuplicates: true }));
      const row = await checked(db.from("chat_delivery_queue").select("*").eq("id", message.id).single());
      if (row.sender_id !== message.sender_id || row.recipient_id !== message.recipient_id || row.chat_id !== message.chat_id) {
        throw new Error("Message ID conflict");
      }
      return row;
    },
    pending(user) {
      return checked(db.from("chat_delivery_queue").select("*").eq("recipient_id", user)
        .is("delivered_at", null).gt("expires_at", new Date().toISOString())
        .order("created_at").order("id").limit(100));
    },
    ack(user, id) {
      return checked(db.from("chat_delivery_queue").update({
        delivered_at: new Date().toISOString(), content: "", image_url: null,
      }).eq("id", id).eq("recipient_id", user).is("delivered_at", null));
    },
    cleanup() {
      return checked(db.from("chat_delivery_queue").delete().lt("expires_at", new Date().toISOString()));
    },
  };
}
module.exports = { createChatStore, chatIdFor };
