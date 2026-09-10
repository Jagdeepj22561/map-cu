-- Enforce team capacity and prevent arbitrary role escalation at the database boundary.
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
    if new.user_id <> auth.uid() and new.user_id <> team_owner then raise exception 'Can only add yourself to a team'; end if;
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

-- A discovery RPC keeps ranking server-side and avoids exposing a larger-than-needed
-- profile dataset to the client. Scores cover the currently available profile signals.
create or replace function public.discover_people(p_limit integer default 30)
returns table (
    user_id uuid,
    name text,
    email text,
    profile_pic_url text,
    course text,
    year text,
    semester text,
    score integer,
    reasons text[]
)
language sql
security definer set search_path = public
as $$
with me as (select * from public.profiles where id = auth.uid()),
blocked as (select blocked_user_id from public.blocks where user_id = auth.uid()),
known as (
    select friend_id as id from public.friendships where user_id = auth.uid()
    union select user_id from public.friendships where friend_id = auth.uid()
    union select receiver_id from public.friend_requests where sender_id = auth.uid()
    union select sender_id from public.friend_requests where receiver_id = auth.uid()
),
scored as (
    select p.*, array_remove(array[
        case when m.course <> '' and p.course = m.course then 'Same course' end,
        case when m.year <> '' and p.year = m.year then 'Same year' end,
        case when m.semester <> '' and p.semester = m.semester then 'Same semester' end,
        case when m.latitude <> 0 and m.longitude <> 0 and p.latitude <> 0 and p.longitude <> 0
             and sqrt(power(m.latitude-p.latitude,2)+power(m.longitude-p.longitude,2)) < 0.01 then 'Nearby' end
    ], null) as reasons
    from public.profiles p cross join me m
    where p.id <> auth.uid() and not exists (select 1 from blocked b where b.blocked_user_id = p.id)
      and not exists (select 1 from known k where k.id = p.id)
)
select id, name, email, profile_pic_url, course, year, semester, cardinality(reasons), reasons
from scored order by cardinality(reasons) desc, name asc limit greatest(1, least(p_limit, 100));
$$;

grant execute on function public.discover_people(integer) to authenticated;
