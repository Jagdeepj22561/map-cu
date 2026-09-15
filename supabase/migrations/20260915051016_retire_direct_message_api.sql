-- Direct-message content now flows only through the Render WebSocket service
-- and its service-role-only chat_delivery_queue. Keep the old tables so an
-- applied migration never destroys historical data, but remove public client
-- access and the obsolete chat-creation RPC.

drop policy if exists "members can read direct chats" on public.direct_chats;
drop policy if exists "authenticated users create direct chats" on public.direct_chats;

drop policy if exists "members can read chat memberships" on public.direct_chat_members;
drop policy if exists "authenticated users add chat members" on public.direct_chat_members;

drop policy if exists "members read direct messages" on public.messages;
drop policy if exists "members send direct messages" on public.messages;
drop policy if exists "members mark direct messages read" on public.messages;
drop policy if exists "senders delete direct messages" on public.messages;

revoke all on table public.direct_chats from anon, authenticated;
revoke all on table public.direct_chat_members from anon, authenticated;
revoke all on table public.messages from anon, authenticated;
revoke all on function public.create_direct_chat(uuid, uuid) from public, anon, authenticated;
