const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { PGlite } = require("@electric-sql/pglite");

test("queue migration: RLS, grants, expiration index and content erasure", async () => {
  const db = new PGlite();
  try {
    await db.exec(`create role anon; create role authenticated; create role service_role bypassrls;
      create table public.profiles(id uuid primary key);
      insert into profiles values ('11111111-1111-4111-8111-111111111111'), ('22222222-2222-4222-8222-222222222222');`);
    await db.exec(fs.readFileSync(path.join(__dirname, "../../supabase/migrations/20260914084545_custom_chat_queue.sql"), "utf8"));
    const result = await db.query(`select relrowsecurity from pg_class where oid = 'public.chat_delivery_queue'::regclass`);
    assert.equal(result.rows[0].relrowsecurity, true);
    for (const role of ["anon", "authenticated"]) {
      for (const privilege of ["SELECT", "INSERT", "UPDATE", "DELETE"]) {
        const check = await db.query(`select has_table_privilege($1, 'public.chat_delivery_queue', $2) as allowed`, [role, privilege]);
        assert.equal(check.rows[0].allowed, false);
      }
    }
    await db.exec(`set role service_role;
      insert into public.chat_delivery_queue(id, chat_id, sender_id, recipient_id, content, type)
      values ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        '11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222', 'hello', 'TEXT');`);
    const expiry = await db.query(`select expires_at - created_at = interval '72 hours' as correct from chat_delivery_queue`);
    assert.equal(expiry.rows[0].correct, true);
    await db.exec(`update chat_delivery_queue set content = '', image_url = null, delivered_at = now()
      where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa' and recipient_id = '22222222-2222-4222-8222-222222222222';`);
    const ack = await db.query("select content, delivered_at is not null as delivered from chat_delivery_queue");
    assert.deepEqual(ack.rows, [{ content: "", delivered: true }]);
    await db.exec("update chat_delivery_queue set expires_at = now() - interval '1 second'; delete from chat_delivery_queue where expires_at < now();");
    assert.equal((await db.query("select * from chat_delivery_queue")).rows.length, 0);
  } finally { await db.close(); }
});
