-- Avoid querying direct_chat_members from its own RLS policy: PostgreSQL then
-- detects infinite recursion (42P17). This function runs as the table owner.
create or replace function public.is_direct_chat_member(target_chat_id uuid)
returns boolean
language sql
security definer
set search_path = public
stable
as $$
  select exists (
    select 1 from public.direct_chat_members
    where chat_id = target_chat_id and user_id = auth.uid()
  );
$$;

drop policy if exists "members can read direct chats" on public.direct_chats;
drop policy if exists "members can read chat memberships" on public.direct_chat_members;
drop policy if exists "members read direct messages" on public.messages;
drop policy if exists "members mark direct messages read" on public.messages;

create policy "members can read direct chats" on public.direct_chats for select to authenticated
using (public.is_direct_chat_member(id));

create policy "members can read chat memberships" on public.direct_chat_members for select to authenticated
using (user_id = auth.uid() or public.is_direct_chat_member(chat_id));

create policy "members read direct messages" on public.messages for select to authenticated
using (public.is_direct_chat_member(chat_id));

create policy "members mark direct messages read" on public.messages for update to authenticated
using (public.is_direct_chat_member(chat_id))
with check (public.is_direct_chat_member(chat_id));

grant execute on function public.is_direct_chat_member(uuid) to authenticated;
