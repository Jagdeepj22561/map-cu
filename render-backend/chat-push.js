const fs = require("node:fs");

function firebaseCredentialJson() {
  const environmentValue = process.env.FIREBASE_SERVICE_ACCOUNT_JSON
    || process.env.FIREBASE_SERVICE_ACCOUNT
    || process.env["Firebase service account"];
  if (environmentValue) return environmentValue;
  const candidates = [
    process.env.FIREBASE_SERVICE_ACCOUNT_PATH,
    "/etc/secrets/firebase-service-account.json",
    "/etc/secrets/Firebase service account",
  ].filter(Boolean);
  for (const path of candidates) {
    try { return fs.readFileSync(path, "utf8"); } catch (_) { /* try next configured path */ }
  }
  return null;
}

function hasChatPushConfig() {
  try {
    const json = firebaseCredentialJson();
    return Boolean(json && JSON.parse(json).project_id && JSON.parse(json).private_key);
  } catch (_) { return false; }
}

function createChatPush(db) {
  const credentialJson = firebaseCredentialJson();
  if (!credentialJson) {
    console.warn("Chat push disabled: FIREBASE_SERVICE_ACCOUNT_JSON is missing");
    return async () => {};
  }
  const { initializeApp, cert } = require("firebase-admin/app");
  const { getMessaging } = require("firebase-admin/messaging");
  const firebase = initializeApp({ credential: cert(JSON.parse(credentialJson)) }, "chat");
  return async row => {
    const { data, error } = await db.from("device_tokens").select("token").eq("user_id", row.recipient_id).limit(10);
    if (error) throw error;
    if (!data.length) return;
    const result = await getMessaging(firebase).sendEachForMulticast({
      tokens: data.map(item => item.token),
      notification: { title: "New message", body: "Open Campus Map to read your message" },
      data: { kind: "custom_chat", recipientId: row.recipient_id, deep_link_chat_id: row.chat_id },
      android: { priority: "high", ttl: 3600000, notification: { tag: row.chat_id } },
      apns: { payload: { aps: { sound: "default" } } },
    });
    for (let i = 0; i < result.responses.length; i++) {
      const code = result.responses[i].error?.code;
      if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") {
        await db.from("device_tokens").delete().eq("token", data[i].token).eq("user_id", row.recipient_id);
      }
    }
  };
}
module.exports = { createChatPush, hasChatPushConfig };
