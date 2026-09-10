-- Supabase client policies for friends and direct messages.
create policy "users send their own friend requests" on public.friend_requests for insert to authenticated with check (sender_id = auth.uid());
create policy "receivers update their friend requests" on public.friend_requests for update to authenticated using (receiver_id = auth.uid()) with check (receiver_id = auth.uid());
create policy "users remove their own requests" on public.friend_requests for delete to authenticated using (sender_id = auth.uid() or receiver_id = auth.uid());

create policy "users create accepted friendship rows" on public.friendships for insert to authenticated with check (
    user_id = auth.uid() or exists (
        select 1 from public.friend_requests r
        where r.status = 'accepted' and r.sender_id = user_id and r.receiver_id = auth.uid()
    )
);
create policy "users remove their own friendship rows" on public.friendships for delete to authenticated using (user_id = auth.uid());

create policy "members can read direct chats" on public.direct_chats for select to authenticated using (exists (select 1 from public.direct_chat_members m where m.chat_id = id and m.user_id = auth.uid()));
create policy "authenticated users create direct chats" on public.direct_chats for insert to authenticated with check (true);
create policy "members can read chat memberships" on public.direct_chat_members for select to authenticated using (exists (select 1 from public.direct_chat_members mine where mine.chat_id = chat_id and mine.user_id = auth.uid()));
create policy "authenticated users add chat members" on public.direct_chat_members for insert to authenticated with check (true);

create policy "members read direct messages" on public.messages for select to authenticated using (exists (select 1 from public.direct_chat_members m where m.chat_id = messages.chat_id and m.user_id = auth.uid()));
create policy "members send direct messages" on public.messages for insert to authenticated with check (sender_id = auth.uid() and exists (select 1 from public.direct_chat_members m where m.chat_id = messages.chat_id and m.user_id = auth.uid()));
create policy "members mark direct messages read" on public.messages for update to authenticated using (exists (select 1 from public.direct_chat_members m where m.chat_id = messages.chat_id and m.user_id = auth.uid())) with check (exists (select 1 from public.direct_chat_members m where m.chat_id = messages.chat_id and m.user_id = auth.uid()));
create policy "senders delete direct messages" on public.messages for delete to authenticated using (sender_id = auth.uid());

create policy "users manage their blocks" on public.blocks for insert to authenticated with check (user_id = auth.uid());
create policy "users remove their blocks" on public.blocks for delete to authenticated using (user_id = auth.uid());

grant select, insert, update, delete on public.friend_requests, public.friendships, public.direct_chats, public.direct_chat_members, public.messages, public.blocks to authenticated;
