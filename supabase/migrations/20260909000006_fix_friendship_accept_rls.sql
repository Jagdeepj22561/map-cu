-- Accepting a request creates two rows: receiver -> sender and sender ->
-- receiver. The latter was rejected because its user_id is the sender.
drop policy if exists "users create their own friendship rows" on public.friendships;
drop policy if exists "users create accepted friendship rows" on public.friendships;

create policy "request participants create accepted friendship rows"
on public.friendships for insert to authenticated
with check (
  user_id = auth.uid()
  or (
    friend_id = auth.uid()
    and exists (
      select 1 from public.friend_requests r
      where r.sender_id = friendships.user_id
        and r.receiver_id = auth.uid()
        and r.status = 'accepted'
    )
  )
);
