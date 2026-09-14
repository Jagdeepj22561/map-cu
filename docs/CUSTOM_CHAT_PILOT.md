# Custom chat pilot (Android)

This implementation adds a Render WebSocket transport for one-to-one Android chat. It is disabled by default. Both accounts must use a pilot APK and be listed on the server. iOS and ordinary APKs still use the existing Supabase message transport; do not mix them in pilot conversations.

## What changes

- A foreground socket stays connected while navigating between app screens. Outgoing messages are saved in Room and retried after network loss or process restart.
- The server verifies Supabase access tokens, derives the sender and conversation IDs, checks friendship and blocks in either direction, limits connections/frames/commands, and accepts messages only after storing them in a temporary Supabase queue.
- Recipients acknowledge only after a Room transaction saves the message and unread count. Duplicate message IDs do not add unread counts twice.
- Acknowledgement clears the queue's text and image URL. A small tombstone stays until expiry to make retries idempotent. Undelivered messages expire 72 hours after their original send time. Render purges expired rows on startup and hourly while running. Configure the database cleanup job below to purge while Render sleeps too.
- FCM sends a generic notification; message content is fetched over the authenticated socket. Push is best effort and is not the delivery queue. Render must wake before it can accept the first send and request its push.
- The UI distinguishes **Pending**, **Accepted** (stored by the server, not proof of delivery/read), and **Not sent**. Rejected messages can be copied and sent again after fixing access. Old pending messages stop retrying after 72 hours.
- Editing and deletion for everyone are disabled for the pilot. Local deletion remains available. Read receipts and multi-device history synchronization are not included. The first receiving device's ACK clears the queue; another device is not guaranteed to receive a copy.

Room migration 30 → 31 preserves existing chat history. Existing Supabase message history is not deleted. This is transport encryption (WSS), not end-to-end encryption. Uploaded images still use the existing Cloudinary storage and its retention; clearing a queue URL does not delete an uploaded image. Device backups are still governed by the app's existing Android backup configuration.

## Deploy the pilot

1. Apply only the new migration `supabase/migrations/20260914084545_custom_chat_queue.sql` to the intended Supabase project using your normal reviewed migration workflow. Do not rerun the initial schema. The queue is service-role-only and must not be added to Realtime or granted to `anon`/`authenticated`.
2. In Supabase, enable Cron and create an hourly SQL job named `custom-chat-expiry` with schedule `0 * * * *` and command:

   ```sql
   delete from public.chat_delivery_queue where expires_at < now();
   ```

   Check its first run succeeds. This physically removes expired temporary data, which is not recoverable through the app. Without this job, expired rows are inaccessible to delivery but can remain until Render next runs cleanup. See [Supabase Cron setup](https://supabase.com/docs/guides/cron/quickstart).
3. Update the existing Render web service with this repository revision. Root directory: `render-backend`; runtime: Node **22**; build command: `npm ci --omit=dev`; start command: `npm start`; health path: `/health`; instance: **Free**. No disk or paid subscription is required for this pilot. Do not create a second free service if you intend to reuse the existing backend.
4. Preserve the existing Supabase, Brevo and Cloudinary environment variables. Set these additional server variables:

   ```text
   CUSTOM_CHAT_ENABLED=true
   CHAT_PILOT_USER_IDS=<first-Supabase-user-UUID>,<second-Supabase-user-UUID>
   FIREBASE_SERVICE_ACCOUNT_JSON=<service-account-JSON-for-the-app-Firebase-project>
   ```

   Get the Firebase service account from Firebase Console → Project settings → Service accounts. Enter it directly into Render's secret environment configuration. Never commit it, paste it into app source, or use it as an Android build value. Without it, chat can deliver but push sending is disabled and the server logs a warning. Existing Brevo variables are still required by server startup.
5. On the Android build machine, set these public values in `local.properties` (or environment variables):

   ```properties
   USE_CUSTOM_CHAT_SERVER=true
   CUSTOM_CHAT_SERVER_URL=wss://YOUR-RENDER-SERVICE.onrender.com/chat
   ```

   Keep the existing public Supabase configuration. Build and install the same pilot APK on both test devices. Sign into the allowlisted accounts, accept their friendship, allow notifications and launch the app so its FCM token is registered.
6. Run the device checks below before adding accounts. This change has not deployed a service, modified the live database, supplied Firebase credentials, or enabled the pilot in your local configuration.

## Device checks

1. Send text and an image between two online devices. Check local status changes from Pending to Accepted and only one message appears on the recipient.
2. Move the recipient to the map/news screen. Verify the chat unread badge changes without opening the conversation.
3. Background the recipient app, send, and tap the notification. Verify it opens the correct chat, including the first message in a new conversation. Android force-stop can prevent push; test normal backgrounding separately.
4. Disconnect the sender network, send, restart the app, reconnect. Verify the saved pending message retries and arrives once.
5. Disconnect the recipient, send, restart/redeploy the Render service, reconnect the recipient within 72 hours. Verify queued delivery and content erasure after ACK.
6. Block either account or remove it from the pilot and send. Verify it is not delivered. Rejected sends should display Not sent.
7. Switch accounts and repeat. Verify queued content never enters another account's local database.
8. Let Render sleep, then send. Check the pending indicator survives the wake-up delay. [Render Free may take about a minute to wake](https://render.com/docs/free); one-second delivery cannot be guaranteed for that first message.

## Verification and rollback

Run `npm ci` followed by `npm test` in `render-backend`. Tests use local WebSockets and an embedded Postgres runtime, with no live credentials. They cover authentication, blocked/pilot access, queue persistence before acceptance, recipient-scoped ACK, duplicate delivery and SQL RLS/grants/expiry. Android compile: `./gradlew :app:compileDebugKotlin --no-daemon`. A successful local build does not verify live FCM credentials or two-device behavior.

To leave the pilot, first let both devices receive pending messages, then rebuild both with `USE_CUSTOM_CHAT_SERVER=false` and disable `CUSTOM_CHAT_ENABLED` on Render. Keep the current APK schema version; do not downgrade to an older APK that could destructively recreate Room. Custom chat history remains local, but is not uploaded or merged into Supabase history. There is deliberately no automatic fallback send through a second transport.

## Free-tier operating limits

This is a small pilot, not a tested user-capacity claim. The server allows at most 200 sockets and three per account as protective ceilings, not a capacity guarantee. It has a single-process session registry; do not scale to multiple instances without shared routing. An account may issue 240 commands/minute, including sync and receipt commands. A sync fetches up to 100 pending messages, with additional pages on subsequent 30-second syncs. Friendship and block checks and queue writes still consume database resources and egress; this design avoids Supabase Realtime billing, not all Supabase usage.

Monitor Render memory, bandwidth and free runtime hours; Supabase database size/egress; queue growth; push failures; and startup/cleanup errors. Current service terms and quotas are documented by [Render](https://render.com/docs/free) and [Supabase](https://supabase.com/pricing). Do not assume a fixed user count will remain free regardless of activity.

Posts, team updates, friend-request background notifications, group chat and iOS custom transport are outside this first chat pilot. Existing foreground friend-request polling remains at 30 seconds. This change does not claim to fix every notification source.

Before public rollout, replace the pilot allowlist with an explicit compatible-client rollout strategy; add read/delivery receipts, push retry jobs, stronger abuse limits, observability and device tests. The existing backend dependencies also need maintenance: the dependency audit reports a pre-existing Cloudinary advisory and a transitive UUID advisory; do not treat this pilot as a completed backend security audit.
