-- Enforce profile privacy, community roles, and one-message chat invitations
-- at the database boundary. Client-side checks are only presentation logic.

-- A normal profile is visible to every signed-in campus user. Ghost profiles
-- are visible only to their owner and to established friends.
drop policy if exists "authenticated users can read profiles" on public.profiles;
drop policy if exists "visible profiles can be read" on public.profiles;
create policy "visible profiles can be read"
on public.profiles for select to authenticated
using (
    id = auth.uid()
    or ghost_mode = false
    or exists (
        select 1
        from public.friendships f
        where (f.user_id = auth.uid() and f.friend_id = profiles.id)
           or (f.friend_id = auth.uid() and f.user_id = profiles.id)
    )
);

-- Discovery must apply ghost mode inside the SECURITY DEFINER function too,
-- because SECURITY DEFINER functions do not inherit the caller's RLS checks.
create or replace function public.discover_people(p_limit integer default 30)
returns table (user_id uuid, name text, email text, profile_pic_url text, course text, year text, semester text, score integer, reasons text[])
language sql
stable
security definer
set search_path = public
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
            case when m.latitude <> 0 and m.longitude <> 0 and p.latitude <> 0 and p.longitude <> 0
                and sqrt(power(m.latitude-p.latitude,2)+power(m.longitude-p.longitude,2)) < 0.01 then 'Nearby' end,
            case when exists (select 1 from (
                select f.friend_id as id from public.friendships f where f.user_id = p.id
                union select f.user_id as id from public.friendships f where f.friend_id = p.id
            ) candidate_friends where candidate_friends.id in (select id from my_friends)) then 'Mutual friends' end
        ], null) as reasons
    from public.profiles p cross join me m
    where p.id <> auth.uid()
      and p.ghost_mode = false
      and not exists (select 1 from blocked b where b.id = p.id)
      and not exists (select 1 from known k where k.id = p.id)
)
select id, name, email, profile_pic_url, course, year, semester,
       cardinality(reasons) + mutual_count, reasons
from scored
order by cardinality(reasons) + mutual_count desc, name asc
limit greatest(1, least(p_limit, 100));
$$;
revoke all on function public.discover_people(integer) from public, anon;
grant execute on function public.discover_people(integer) to authenticated;

-- Self-join always means member. Owners may add members/admins; admins may
-- add members. Only the owner can promote or demote a non-owner membership.
create or replace function public.is_group_owner(_group_id uuid, _user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.groups g
        where g.id = _group_id and g.owner_id = _user_id
    );
$$;
revoke all on function public.is_group_owner(uuid, uuid) from public, anon;
grant execute on function public.is_group_owner(uuid, uuid) to authenticated;

create or replace function public.is_group_admin(_group_id uuid, _user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select public.is_group_owner(_group_id, _user_id) or exists (
        select 1 from public.group_members gm
        where gm.group_id = _group_id
          and gm.user_id = _user_id
          and gm.role = 'admin'
    );
$$;
revoke all on function public.is_group_admin(uuid, uuid) from public, anon;
grant execute on function public.is_group_admin(uuid, uuid) to authenticated;

drop policy if exists "users can join groups" on public.group_members;
create policy "users can join groups"
on public.group_members for insert to authenticated
with check (
    (user_id = auth.uid() and role = 'member')
    or (
        role in ('member', 'admin')
        and public.is_group_owner(group_id, auth.uid())
    )
    or (
        role = 'member'
        and public.is_group_admin(group_id, auth.uid())
    )
);

drop policy if exists "admins can update member roles" on public.group_members;
create policy "owners can update non-owner roles"
on public.group_members for update to authenticated
using (
    role <> 'owner'
    and public.is_group_owner(group_id, auth.uid())
)
with check (
    role in ('member', 'admin')
    and public.is_group_owner(group_id, auth.uid())
);

-- The Render service records the first unsolicited message. The unique pair
-- makes the one-message allowance persistent even after delivery-queue cleanup.
create table if not exists public.chat_invites (
    sender_id uuid not null references public.profiles(id) on delete cascade,
    recipient_id uuid not null references public.profiles(id) on delete cascade,
    first_message_id uuid not null unique,
    created_at timestamptz not null default now(),
    primary key (sender_id, recipient_id),
    check (sender_id <> recipient_id)
);
alter table public.chat_invites enable row level security;
revoke all on table public.chat_invites from public, anon, authenticated;
grant select, insert, update, delete on table public.chat_invites to service_role;
create index if not exists chat_invites_recipient_idx
    on public.chat_invites(recipient_id, created_at desc);
