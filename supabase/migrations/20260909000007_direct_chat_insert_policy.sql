-- Explicitly permit an authenticated user to create a direct-chat container.
-- Membership and message policies still restrict access to the participants.
drop policy if exists "authenticated users create direct chats" on public.direct_chats;

create policy "authenticated users create direct chats"
on public.direct_chats for insert to authenticated
with check (auth.uid() is not null);

grant insert on public.direct_chats to authenticated;
