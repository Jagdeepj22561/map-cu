-- ===========================================================================
-- 20260911000002_fix_team_join_request_approval.sql
-- Fix team join request approval, member addition/removal, and re-requesting.
-- ===========================================================================

-- 1. Ensure join_requests table exists with full schema & RLS policies
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

-- Policies for join_requests
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

-- 2. Allow team owners to insert and remove members from their teams
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

-- 3. Atomic RPC to approve a join request with SECURITY DEFINER
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

    -- Update join request status
    update public.join_requests
    set status = 'approved',
        reviewed_by = auth.uid()
    where id = p_request_id;

    -- Insert into team members
    insert into public.event_team_members (team_id, user_id, role)
    values (v_team_id, v_requester_id, 'member')
    on conflict (team_id, user_id) do nothing;
end;
$$;

revoke all on function public.approve_join_request(uuid) from public;
grant execute on function public.approve_join_request(uuid) to authenticated;

-- 4. Atomic RPC to reject a join request with SECURITY DEFINER
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
