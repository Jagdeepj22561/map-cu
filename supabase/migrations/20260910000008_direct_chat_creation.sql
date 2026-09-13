-- Allow authenticated users to insert direct chat memberships.
-- Without this, the client cannot create chats without a SECURITY DEFINER RPC.
create policy "authenticated users add chat members"
on public.direct_chat_members for insert to authenticated
with check (auth.uid() is not null);

-- Create the RPC if it doesn't already exist (acts as a fallback).
create or replace function public.create_direct_chat(p_chat_id uuid, p_friend_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.direct_chats (id)
  values (p_chat_id)
  on conflict (id) do nothing;

  insert into public.direct_chat_members (chat_id, user_id)
  values (p_chat_id, auth.uid()), (p_chat_id, p_friend_id)
  on conflict (chat_id, user_id) do nothing;
end;
$$;

grant execute on function public.create_direct_chat(uuid, uuid) to authenticated;
