-- Combined idempotent migration script.
-- Safe to paste into the Supabase SQL Editor multiple times.
-- Assumes the initial schema (20260908000000) and profile compatibility (20260908000001)
-- have already been applied (they create the base tables).

-- ===========================================================================
-- 20260909000000 - Device token policies
-- ===========================================================================
drop policy if exists "users can insert their device tokens" on public.device_tokens;
create policy "users can insert their device tokens"
    on public.device_tokens for insert to authenticated
    with check (user_id = auth.uid());

drop policy if exists "users can update their device tokens" on public.device_tokens;
create policy "users can update their device tokens"
    on public.device_tokens for update to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

drop policy if exists "users can delete their device tokens" on public.device_tokens;
create policy "users can delete their device tokens"
    on public.device_tokens for delete to authenticated
    using (user_id = auth.uid());

grant select, insert, update, delete on public.device_tokens to authenticated;

-- ===========================================================================
-- 20260909000001 - Announcements: extra columns, reports table
-- ===========================================================================
alter table public.announcements
    add column if not exists timestamp bigint not null default 0,
    add column if not exists author text not null default '',
    add column if not exists author_profile_pic_url text,
    add column if not exists time text,
    add column if not exists reward text;

create table if not exists public.reports (
    id uuid primary key default gen_random_uuid(),
    announcement_id text not null references public.announcements(id) on delete cascade,
    reporter_id uuid not null references public.profiles(id) on delete cascade,
    author_id uuid references public.profiles(id) on delete set null,
    reason text not null check (char_length(trim(reason)) > 0),
    created_at timestamptz not null default now()
);

create index if not exists announcements_timestamp_idx on public.announcements(timestamp desc);
alter table public.reports enable row level security;

drop policy if exists "users can create announcements" on public.announcements;
create policy "users can create announcements" on public.announcements for insert to authenticated with check (author_id = auth.uid());

drop policy if exists "authors can update announcements" on public.announcements;
create policy "authors can update announcements" on public.announcements for update to authenticated using (author_id = auth.uid()) with check (author_id = auth.uid());

drop policy if exists "authors can delete announcements" on public.announcements;
create policy "authors can delete announcements" on public.announcements for delete to authenticated using (author_id = auth.uid());

drop policy if exists "users can create reports" on public.reports;
create policy "users can create reports" on public.reports for insert to authenticated with check (reporter_id = auth.uid());

grant select, insert, update, delete on public.announcements to authenticated;
grant insert on public.reports to authenticated;

-- ===========================================================================
-- 20260909000002 - Chat client policies (friend requests, friendships, chats)
-- ===========================================================================
drop policy if exists "users send their own friend requests" on public.friend_requests;
create policy "users send their own friend requests" on public.friend_requests for insert to authenticated with check (sender_id = auth.uid());

drop policy if exists "receivers update their friend requests" on public.friend_requests;
create policy "receivers update their friend requests" on public.friend_requests for update to authenticated using (receiver_id = auth.uid()) with check (receiver_id = auth.uid());

drop policy if exists "users remove their own requests" on public.friend_requests;
create policy "users remove their own requests" on public.friend_requests for delete to authenticated using (sender_id = auth.uid() or receiver_id = auth.uid());

drop policy if exists "users create accepted friendship rows" on public.friendships;
drop policy if exists "request participants create accepted friendship rows" on public.friendships;

drop policy if exists "users remove their own friendship rows" on public.friendships;
create policy "users remove their own friendship rows" on public.friendships for delete to authenticated using (user_id = auth.uid());

drop policy if exists "authenticated users create direct chats" on public.direct_chats;
create policy "authenticated users create direct chats" on public.direct_chats for insert to authenticated with check (true);

drop policy if exists "authenticated users add chat members" on public.direct_chat_members;
create policy "authenticated users add chat members" on public.direct_chat_members for insert to authenticated with check (true);

drop policy if exists "members send direct messages" on public.messages;
create policy "members send direct messages" on public.messages for insert to authenticated with check (sender_id = auth.uid() and exists (select 1 from public.direct_chat_members m where m.chat_id = messages.chat_id and m.user_id = auth.uid()));

drop policy if exists "senders delete direct messages" on public.messages;
create policy "senders delete direct messages" on public.messages for delete to authenticated using (sender_id = auth.uid());

drop policy if exists "users manage their blocks" on public.blocks;
create policy "users manage their blocks" on public.blocks for insert to authenticated with check (user_id = auth.uid());

drop policy if exists "users remove their blocks" on public.blocks;
create policy "users remove their blocks" on public.blocks for delete to authenticated using (user_id = auth.uid());

grant select, insert, update, delete on public.friend_requests, public.friendships, public.direct_chats, public.direct_chat_members, public.messages, public.blocks to authenticated;

-- ===========================================================================
-- 20260909000004 - Announcement engagement policies
-- ===========================================================================
drop policy if exists "users manage their own announcement likes" on public.announcement_likes;
create policy "users manage their own announcement likes"
on public.announcement_likes for insert to authenticated
with check (user_id = auth.uid());

drop policy if exists "users remove their own announcement likes" on public.announcement_likes;
create policy "users remove their own announcement likes"
on public.announcement_likes for delete to authenticated
using (user_id = auth.uid());

drop policy if exists "users add their own announcement comments" on public.announcement_comments;
create policy "users add their own announcement comments"
on public.announcement_comments for insert to authenticated
with check (author_id = auth.uid());

grant select, insert, delete on public.announcement_likes to authenticated;
grant select, insert on public.announcement_comments to authenticated;

-- ===========================================================================
-- 20260909000005 - Fix direct chat RLS (use SECURITY DEFINER to avoid recursion)
-- ===========================================================================
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
create policy "members can read direct chats" on public.direct_chats for select to authenticated
using (public.is_direct_chat_member(id));

drop policy if exists "members can read chat memberships" on public.direct_chat_members;
create policy "members can read chat memberships" on public.direct_chat_members for select to authenticated
using (user_id = auth.uid() or public.is_direct_chat_member(chat_id));

drop policy if exists "members read direct messages" on public.messages;
create policy "members read direct messages" on public.messages for select to authenticated
using (public.is_direct_chat_member(chat_id));

drop policy if exists "members mark direct messages read" on public.messages;
create policy "members mark direct messages read" on public.messages for update to authenticated
using (public.is_direct_chat_member(chat_id))
with check (public.is_direct_chat_member(chat_id));

grant execute on function public.is_direct_chat_member(uuid) to authenticated;

-- ===========================================================================
-- 20260909000006 - Fix friendship accept RLS
-- ===========================================================================
drop policy if exists "users create their own friendship rows" on public.friendships;
drop policy if exists "users create accepted friendship rows" on public.friendships;
drop policy if exists "request participants create accepted friendship rows" on public.friendships;

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

-- ===========================================================================
-- 20260909000007 - Direct chat insert policy (explicit)
-- ===========================================================================
drop policy if exists "authenticated users create direct chats" on public.direct_chats;
create policy "authenticated users create direct chats"
on public.direct_chats for insert to authenticated
with check (auth.uid() is not null);

grant insert on public.direct_chats to authenticated;

-- ===========================================================================
-- 20260909000008 - Profile university and gender columns
-- ===========================================================================
alter table public.profiles
    add column if not exists university text not null default '',
    add column if not exists gender text not null default '';

-- ===========================================================================
-- 20260909000009 - Fix engagement RLS
-- ===========================================================================
drop policy if exists "authenticated users can read announcement likes" on public.announcement_likes;
create policy "authenticated users can read announcement likes"
    on public.announcement_likes for select to authenticated using (true);

drop policy if exists "authenticated users can read announcement comments" on public.announcement_comments;
create policy "authenticated users can read announcement comments"
    on public.announcement_comments for select to authenticated using (true);

grant select on public.announcement_likes to authenticated;
grant select on public.announcement_comments to authenticated;

-- ===========================================================================
-- 20260910000000 - Announcement view RPC (replaced by 000001)
-- ===========================================================================
-- Skipped: superseded by 20260910000001.

-- ===========================================================================
-- 20260910000001 - Harden announcement view RPC
-- ===========================================================================
create or replace function public.increment_announcement_view(p_announcement_id text)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    new_count integer;
begin
    if auth.uid() is null then
        raise exception 'authentication required';
    end if;
    update public.announcements
    set view_count = view_count + 1,
        updated_at = now()
    where id = p_announcement_id
    returning view_count into new_count;
    return new_count;
end;
$$;

grant execute on function public.increment_announcement_view(text) to authenticated;

-- ===========================================================================
-- 20260910000002 - Team events and friend discovery
-- ===========================================================================
create table if not exists public.event_teams (
    id uuid primary key default gen_random_uuid(),
    announcement_id text not null references public.announcements(id) on delete cascade,
    owner_id uuid not null references public.profiles(id) on delete cascade,
    name text not null default '',
    max_members integer,
    created_at timestamptz not null default now(),
    unique (announcement_id, owner_id),
    check (max_members is null or max_members > 0)
);

create table if not exists public.event_team_members (
    team_id uuid not null references public.event_teams(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    role text not null default 'member' check (role in ('owner', 'member')),
    joined_at timestamptz not null default now(),
    primary key (team_id, user_id)
);

create table if not exists public.team_requirements (
    id uuid primary key default gen_random_uuid(),
    announcement_id text not null references public.announcements(id) on delete cascade,
    author_id uuid not null references public.profiles(id) on delete cascade,
    content text not null check (char_length(trim(content)) > 0),
    members_needed integer not null default 1 check (members_needed > 0),
    created_at timestamptz not null default now()
);

create table if not exists public.team_requirement_responses (
    requirement_id uuid not null references public.team_requirements(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (requirement_id, user_id)
);

create index if not exists event_teams_announcement_idx on public.event_teams(announcement_id);
create index if not exists event_team_members_user_idx on public.event_team_members(user_id);
create index if not exists team_requirements_event_idx on public.team_requirements(announcement_id, created_at desc);
create index if not exists team_requirements_author_idx on public.team_requirements(author_id);

alter table public.event_teams enable row level security;
alter table public.event_team_members enable row level security;
alter table public.team_requirements enable row level security;
alter table public.team_requirement_responses enable row level security;

grant select, insert, update, delete on public.event_teams to authenticated;
grant select, insert, update, delete on public.event_team_members to authenticated;
grant select, insert, update, delete on public.team_requirements to authenticated;
grant select, insert, delete on public.team_requirement_responses to authenticated;

drop policy if exists "authenticated users can view event teams" on public.event_teams;
create policy "authenticated users can view event teams" on public.event_teams for select to authenticated using (true);

drop policy if exists "users can create own event team" on public.event_teams;
create policy "users can create own event team" on public.event_teams for insert to authenticated with check (owner_id = auth.uid());

drop policy if exists "team owners can update event team" on public.event_teams;
create policy "team owners can update event team" on public.event_teams for update to authenticated using (owner_id = auth.uid()) with check (owner_id = auth.uid());

drop policy if exists "team owners can delete event team" on public.event_teams;
create policy "team owners can delete event team" on public.event_teams for delete to authenticated using (owner_id = auth.uid());

drop policy if exists "authenticated users can view event team members" on public.event_team_members;
create policy "authenticated users can view event team members" on public.event_team_members for select to authenticated using (true);

drop policy if exists "users can join event teams as themselves" on public.event_team_members;
create policy "users can join event teams as themselves" on public.event_team_members for insert to authenticated with check (user_id = auth.uid());

drop policy if exists "users can update their event team membership" on public.event_team_members;
create policy "users can update their event team membership" on public.event_team_members for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists "users can leave event teams" on public.event_team_members;
create policy "users can leave event teams" on public.event_team_members for delete to authenticated using (user_id = auth.uid());

drop policy if exists "authenticated users can view team requirements" on public.team_requirements;
create policy "authenticated users can view team requirements" on public.team_requirements for select to authenticated using (true);

drop policy if exists "users can create own team requirements" on public.team_requirements;
create policy "users can create own team requirements" on public.team_requirements for insert to authenticated with check (author_id = auth.uid());

drop policy if exists "authors can update team requirements" on public.team_requirements;
create policy "authors can update team requirements" on public.team_requirements for update to authenticated using (author_id = auth.uid()) with check (author_id = auth.uid());

drop policy if exists "authors can delete team requirements" on public.team_requirements;
create policy "authors can delete team requirements" on public.team_requirements for delete to authenticated using (author_id = auth.uid());

drop policy if exists "authenticated users can view requirement responses" on public.team_requirement_responses;
create policy "authenticated users can view requirement responses" on public.team_requirement_responses for select to authenticated using (true);

drop policy if exists "users can respond as themselves" on public.team_requirement_responses;
create policy "users can respond as themselves" on public.team_requirement_responses for insert to authenticated with check (user_id = auth.uid());

drop policy if exists "users can remove own requirement response" on public.team_requirement_responses;
create policy "users can remove own requirement response" on public.team_requirement_responses for delete to authenticated using (user_id = auth.uid());

create index if not exists profiles_course_year_semester_idx on public.profiles(course, year, semester);
create index if not exists profiles_location_idx on public.profiles(latitude, longitude);

-- ===========================================================================
-- 20260910000003 - Harden team membership and discovery
-- ===========================================================================
create or replace function public.enforce_event_team_membership()
returns trigger
language plpgsql
security definer set search_path = public
as $$
declare
    team_owner uuid;
    capacity integer;
    current_count integer;
begin
    select owner_id, max_members into team_owner, capacity
    from public.event_teams where id = new.team_id for update;
    if team_owner is null then raise exception 'Team not found'; end if;
    if auth.uid() is not null and auth.uid() <> new.user_id and auth.uid() <> team_owner then
        raise exception 'Can only add yourself to a team';
    end if;
    if new.user_id = team_owner then new.role := 'owner'; else new.role := 'member'; end if;
    if capacity is not null then
        select count(*) into current_count from public.event_team_members where team_id = new.team_id;
        if current_count >= capacity and not exists (select 1 from public.event_team_members where team_id = new.team_id and user_id = new.user_id) then
            raise exception 'Team is full';
        end if;
    end if;
    return new;
end;
$$;

drop trigger if exists enforce_event_team_membership_trigger on public.event_team_members;
create trigger enforce_event_team_membership_trigger
before insert or update on public.event_team_members
for each row execute function public.enforce_event_team_membership();

create or replace function public.add_event_team_owner()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
    insert into public.event_team_members(team_id, user_id, role)
    values (new.id, new.owner_id, 'owner')
    on conflict (team_id, user_id) do nothing;
    return new;
end;
$$;

drop trigger if exists add_event_team_owner_trigger on public.event_teams;
create trigger add_event_team_owner_trigger
after insert on public.event_teams
for each row execute function public.add_event_team_owner();

-- ===========================================================================
-- 20260910000004 - Groups feature
-- ===========================================================================
alter table public.groups
    add column if not exists description text not null default '',
    add column if not exists group_pic_url text,
    add column if not exists year text,
    add column if not exists semester text,
    add column if not exists course text,
    add column if not exists is_public boolean not null default true;

create index if not exists groups_public_created_idx
    on public.groups(is_public, created_at desc);
create index if not exists groups_owner_idx
    on public.groups(owner_id);

alter table public.groups enable row level security;
alter table public.group_members enable row level security;
alter table public.group_messages enable row level security;

drop policy if exists "authenticated users can read groups" on public.groups;
drop policy if exists "users can create groups" on public.groups;
drop policy if exists "group admins can update groups" on public.groups;
drop policy if exists "users can join groups" on public.group_members;
drop policy if exists "users can read group members" on public.group_members;
drop policy if exists "users can leave groups" on public.group_members;
drop policy if exists "group members can read group messages" on public.group_messages;
drop policy if exists "group members can send group messages" on public.group_messages;

create policy "users can create groups"
    on public.groups for insert to authenticated
    with check (owner_id = auth.uid());

create policy "users can join groups"
    on public.group_members for insert to authenticated
    with check (user_id = auth.uid() and role = 'member');

create policy "users can leave groups"
    on public.group_members for delete to authenticated
    using (user_id = auth.uid() and role <> 'owner');

grant select, insert, update on public.groups to authenticated;
grant select, insert, delete on public.group_members to authenticated;
grant select, insert on public.group_messages to authenticated;

create or replace function public.add_group_owner_membership()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
    insert into public.group_members (group_id, user_id, role)
    values (new.id, new.owner_id, 'owner')
    on conflict (group_id, user_id) do update set role = 'owner';
    return new;
end;
$$;

drop trigger if exists on_group_created_add_owner on public.groups;
create trigger on_group_created_add_owner
after insert on public.groups
for each row execute procedure public.add_group_owner_membership();

do $$
begin
    if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'groups') then
        alter publication supabase_realtime add table public.groups;
    end if;
    if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'group_members') then
        alter publication supabase_realtime add table public.group_members;
    end if;
end;
$$;

-- ===========================================================================
-- 20260910000005 - Harden team discovery (discover_people with mutual friends)
-- ===========================================================================
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

-- ===========================================================================
-- 20260910000006 - Fix groups RLS recursion
-- ===========================================================================
create or replace function public.is_group_member(_group_id uuid, _user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.group_members gm
        where gm.group_id = _group_id
          and gm.user_id = _user_id
    );
$$;

revoke all on function public.is_group_member(uuid, uuid) from public;
grant execute on function public.is_group_member(uuid, uuid) to authenticated;

drop policy if exists "authenticated users can read groups" on public.groups;
create policy "authenticated users can read groups"
    on public.groups for select to authenticated
    using (
        is_public = true
        or owner_id = auth.uid()
        or public.is_group_member(id, auth.uid())
    );

drop policy if exists "users can read group members" on public.group_members;
create policy "users can read group members"
    on public.group_members for select to authenticated
    using (
        user_id = auth.uid()
        or exists (
            select 1
            from public.groups g
            where g.id = group_members.group_id
              and (g.is_public = true or g.owner_id = auth.uid())
        )
    );

-- ===========================================================================
-- 20260910000007 - Fix group messages RLS
-- ===========================================================================
create or replace function public.is_group_member_or_owner(_group_id uuid, _user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.groups g
        where g.id = _group_id
          and (
              g.owner_id = _user_id
              or exists (
                  select 1
                  from public.group_members gm
                  where gm.group_id = g.id
                    and gm.user_id = _user_id
              )
          )
    );
$$;

revoke all on function public.is_group_member_or_owner(uuid, uuid) from public;
grant execute on function public.is_group_member_or_owner(uuid, uuid) to authenticated;

drop policy if exists "group members can read group messages" on public.group_messages;
create policy "group members can read group messages"
on public.group_messages
for select
to authenticated
using (public.is_group_member_or_owner(group_id, auth.uid()));

drop policy if exists "group members can send group messages" on public.group_messages;
create policy "group members can send group messages"
on public.group_messages
for insert
to authenticated
with check (
    sender_id = auth.uid()
    and public.is_group_member_or_owner(group_id, auth.uid())
);

grant select, insert on public.group_messages to authenticated;

-- ===========================================================================
-- 20260910000008 - Direct chat creation (policy + RPC fallback)
-- ===========================================================================
drop policy if exists "authenticated users add chat members" on public.direct_chat_members;
create policy "authenticated users add chat members"
on public.direct_chat_members for insert to authenticated
with check (auth.uid() is not null);

create or replace function public.create_direct_chat(p_chat_id uuid, p_friend_id uuid)
returns void language plpgsql security definer set search_path = public
as $$ begin
  insert into public.direct_chats (id) values (p_chat_id) on conflict (id) do nothing;
  insert into public.direct_chat_members (chat_id, user_id)
  values (p_chat_id, auth.uid()), (p_chat_id, p_friend_id)
  on conflict (chat_id, user_id) do nothing;
end; $$;

grant execute on function public.create_direct_chat(uuid, uuid) to authenticated;

-- ===========================================================================
-- 20260911000002 - Team join request approval, member policies, and RPC
-- ===========================================================================
create table if not exists public.join_requests (
    id uuid primary key default gen_random_uuid(),
    team_id uuid not null references public.event_teams(id) on delete cascade,
    requester_id uuid not null references public.profiles(id) on delete cascade,
    message text not null default '',
    status text not null default 'pending' check (status in ('pending', 'approved', 'rejected')),
    reviewed_by uuid references public.profiles(id) on delete set null,
    created_at timestamptz not null default now()
);

create index if not exists join_requests_team_status_idx on public.join_requests(team_id, status);
create index if not exists join_requests_requester_idx on public.join_requests(requester_id);

alter table public.join_requests enable row level security;
grant select, insert, update, delete on public.join_requests to authenticated;

drop policy if exists "users can view their own join requests or team owner view" on public.join_requests;
create policy "users can view their own join requests or team owner view"
    on public.join_requests for select to authenticated
    using (
        requester_id = auth.uid()
        or exists (
            select 1 from public.event_teams
            where event_teams.id = join_requests.team_id
              and event_teams.owner_id = auth.uid()
        )
    );

drop policy if exists "users can submit join requests" on public.join_requests;
create policy "users can submit join requests"
    on public.join_requests for insert to authenticated
    with check (requester_id = auth.uid());

drop policy if exists "requester can cancel join request" on public.join_requests;
create policy "requester can cancel join request"
    on public.join_requests for delete to authenticated
    using (
        requester_id = auth.uid()
        or exists (
            select 1 from public.event_teams
            where event_teams.id = join_requests.team_id
              and event_teams.owner_id = auth.uid()
        )
    );

drop policy if exists "team owners can review join requests" on public.join_requests;
create policy "team owners can review join requests"
    on public.join_requests for update to authenticated
    using (
        exists (
            select 1 from public.event_teams
            where event_teams.id = join_requests.team_id
              and event_teams.owner_id = auth.uid()
        )
    )
    with check (
        exists (
            select 1 from public.event_teams
            where event_teams.id = join_requests.team_id
              and event_teams.owner_id = auth.uid()
        )
    );

drop policy if exists "team owners can add members" on public.event_team_members;
create policy "team owners can add members"
    on public.event_team_members for insert to authenticated
    with check (
        user_id = auth.uid()
        or exists (
            select 1 from public.event_teams
            where event_teams.id = event_team_members.team_id
              and event_teams.owner_id = auth.uid()
        )
    );

drop policy if exists "team owners can remove members" on public.event_team_members;
create policy "team owners can remove members"
    on public.event_team_members for delete to authenticated
    using (
        user_id = auth.uid()
        or exists (
            select 1 from public.event_teams
            where event_teams.id = event_team_members.team_id
              and event_teams.owner_id = auth.uid()
        )
    );

create or replace function public.approve_join_request(p_request_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_team_id uuid;
    v_requester_id uuid;
    v_owner_id uuid;
    v_status text;
    v_capacity integer;
    v_count integer;
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;

    select r.team_id, r.requester_id, r.status, t.owner_id, t.max_members
    into v_team_id, v_requester_id, v_status, v_owner_id, v_capacity
    from public.join_requests r
    join public.event_teams t on t.id = r.team_id
    where r.id = p_request_id;

    if v_team_id is null then
        raise exception 'Join request not found';
    end if;

    if v_owner_id <> auth.uid() then
        raise exception 'Only the team leader can approve join requests';
    end if;

    if v_capacity is not null then
        select count(*) into v_count
        from public.event_team_members
        where team_id = v_team_id;

        if v_count >= v_capacity then
            raise exception 'Team is already full';
        end if;
    end if;

    update public.join_requests
    set status = 'approved',
        reviewed_by = auth.uid()
    where id = p_request_id;

    insert into public.event_team_members (team_id, user_id, role)
    values (v_team_id, v_requester_id, 'member')
    on conflict (team_id, user_id) do nothing;
end;
$$;

revoke all on function public.approve_join_request(uuid) from public;
grant execute on function public.approve_join_request(uuid) to authenticated;

create or replace function public.reject_join_request(p_request_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_owner_id uuid;
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;

    select t.owner_id into v_owner_id
    from public.join_requests r
    join public.event_teams t on t.id = r.team_id
    where r.id = p_request_id;

    if v_owner_id is null then
        raise exception 'Join request not found';
    end if;

    if v_owner_id <> auth.uid() then
        raise exception 'Only the team leader can reject join requests';
    end if;

    update public.join_requests
    set status = 'rejected',
        reviewed_by = auth.uid()
    where id = p_request_id;
end;
$$;

revoke all on function public.reject_join_request(uuid) from public;
grant execute on function public.reject_join_request(uuid) to authenticated;

