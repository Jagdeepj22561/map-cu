-- P4: Friend-request deduplication at the database level.
--
-- The existing unique(sender_id, receiver_id) only blocks the exact ordered pair.
-- These triggers close the two remaining gaps:
--   1. A new pending request in the opposite direction of an existing pending one.
--   2. A new request between two users who are already friends.
-- Both are idempotent: re-running this migration drops the old trigger/functions
-- first so it is safe to paste into the Supabase SQL Editor again.

drop trigger if exists friend_requests_no_bidirectional_pending on public.friend_requests;
drop trigger if exists friend_requests_no_friendship_overlap on public.friend_requests;
drop function if exists public.friend_requests_block_reverse_pending();
drop function if exists public.friend_requests_block_existing_friendship();

create or replace function public.friend_requests_block_reverse_pending()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if new.status = 'pending' and exists (
        select 1 from public.friend_requests
        where sender_id = new.receiver_id
          and receiver_id = new.sender_id
          and status = 'pending'
    ) then
        raise exception 'A pending friend request already exists between these users.'
            using errcode = '23505';
    end if;
    return new;
end;
$$;

create or replace function public.friend_requests_block_existing_friendship()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if exists (
        select 1 from public.friendships
        where (user_id = new.sender_id and friend_id = new.receiver_id)
           or (user_id = new.receiver_id and friend_id = new.sender_id)
    ) then
        raise exception 'You are already friends with this user.'
            using errcode = '23505';
    end if;
    return new;
end;
$$;

create trigger friend_requests_no_bidirectional_pending
    before insert on public.friend_requests
    for each row execute function public.friend_requests_block_reverse_pending();

create trigger friend_requests_no_friendship_overlap
    before insert on public.friend_requests
    for each row execute function public.friend_requests_block_existing_friendship();

grant execute on function public.friend_requests_block_reverse_pending() to authenticated;
grant execute on function public.friend_requests_block_existing_friendship() to authenticated;
