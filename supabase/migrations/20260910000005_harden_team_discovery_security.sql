-- Additional hardening for team discovery. This follows the original team migration
-- and safely replaces the discovery function with stronger blocking/mutual-friend logic.
create or replace function public.discover_people(p_limit integer default 30)
returns table (user_id uuid, name text, email text, profile_pic_url text, course text, year text, semester text, score integer, reasons text[])
language sql security definer set search_path = public
as $$
with me as (select * from public.profiles where id = auth.uid()),
my_friends as (
    select friend_id as id from public.friendships where user_id = auth.uid()
    union select user_id as id from public.friendships where friend_id = auth.uid()
),
blocked as (
    select blocked_user_id as id from public.blocks where user_id = auth.uid()
    union select user_id as id from public.blocks where blocked_user_id = auth.uid()
),
known as (
    select id from my_friends
    union select receiver_id from public.friend_requests where sender_id = auth.uid()
    union select sender_id from public.friend_requests where receiver_id = auth.uid()
),
scored as (
    select p.*,
        coalesce((select count(*) from (
            select f.friend_id as id from public.friendships f where f.user_id = p.id
            union select f.user_id as id from public.friendships f where f.friend_id = p.id
        ) candidate_friends where candidate_friends.id in (select id from my_friends)), 0)::integer as mutual_count,
        array_remove(array[
            case when m.course <> '' and p.course = m.course then 'Same course' end,
            case when m.year <> '' and p.year = m.year then 'Same year' end,
            case when m.semester <> '' and p.semester = m.semester then 'Same semester' end,
            case when m.latitude <> 0 and m.longitude <> 0 and p.latitude <> 0 and p.longitude <> 0 and sqrt(power(m.latitude-p.latitude,2)+power(m.longitude-p.longitude,2)) < 0.01 then 'Nearby' end,
            case when exists (select 1 from (
                select f.friend_id as id from public.friendships f where f.user_id = p.id
                union select f.user_id as id from public.friendships f where f.friend_id = p.id
            ) candidate_friends where candidate_friends.id in (select id from my_friends)) then 'Mutual friends' end
        ], null) as reasons
    from public.profiles p cross join me m
    where p.id <> auth.uid()
      and not exists (select 1 from blocked b where b.id = p.id)
      and not exists (select 1 from known k where k.id = p.id)
)
select id, name, email, profile_pic_url, course, year, semester, cardinality(reasons) + mutual_count, reasons
from scored order by cardinality(reasons) + mutual_count desc, name asc
limit greatest(1, least(p_limit, 100));
$$;

grant execute on function public.discover_people(integer) to authenticated;
