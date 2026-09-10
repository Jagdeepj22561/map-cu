-- Team events and friend-discovery support.
-- Keep event membership and team requirements separate from announcement content.

create table public.event_teams (
    id uuid primary key default gen_random_uuid(),
    announcement_id text not null references public.announcements(id) on delete cascade,
    owner_id uuid not null references public.profiles(id) on delete cascade,
    name text not null default '',
    max_members integer,
    created_at timestamptz not null default now(),
    unique (announcement_id, owner_id),
    check (max_members is null or max_members > 0)
);

create table public.event_team_members (
    team_id uuid not null references public.event_teams(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    role text not null default 'member' check (role in ('owner', 'member')),
    joined_at timestamptz not null default now(),
    primary key (team_id, user_id)
);

create table public.team_requirements (
    id uuid primary key default gen_random_uuid(),
    announcement_id text not null references public.announcements(id) on delete cascade,
    author_id uuid not null references public.profiles(id) on delete cascade,
    content text not null check (char_length(trim(content)) > 0),
    members_needed integer not null default 1 check (members_needed > 0),
    created_at timestamptz not null default now()
);

create table public.team_requirement_responses (
    requirement_id uuid not null references public.team_requirements(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (requirement_id, user_id)
);

create index event_teams_announcement_idx on public.event_teams(announcement_id);
create index event_team_members_user_idx on public.event_team_members(user_id);
create index team_requirements_event_idx on public.team_requirements(announcement_id, created_at desc);
create index team_requirements_author_idx on public.team_requirements(author_id);

alter table public.event_teams enable row level security;
alter table public.event_team_members enable row level security;
alter table public.team_requirements enable row level security;
alter table public.team_requirement_responses enable row level security;

grant select, insert, update, delete on public.event_teams to authenticated;
grant select, insert, update, delete on public.event_team_members to authenticated;
grant select, insert, update, delete on public.team_requirements to authenticated;
grant select, insert, delete on public.team_requirement_responses to authenticated;

create policy "authenticated users can view event teams" on public.event_teams for select to authenticated using (true);
create policy "users can create own event team" on public.event_teams for insert to authenticated with check (owner_id = auth.uid());
create policy "team owners can update event team" on public.event_teams for update to authenticated using (owner_id = auth.uid()) with check (owner_id = auth.uid());
create policy "team owners can delete event team" on public.event_teams for delete to authenticated using (owner_id = auth.uid());

create policy "authenticated users can view event team members" on public.event_team_members for select to authenticated using (true);
create policy "users can join event teams as themselves" on public.event_team_members for insert to authenticated with check (user_id = auth.uid());
create policy "users can update their event team membership" on public.event_team_members for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "users can leave event teams" on public.event_team_members for delete to authenticated using (user_id = auth.uid());

create policy "authenticated users can view team requirements" on public.team_requirements for select to authenticated using (true);
create policy "users can create own team requirements" on public.team_requirements for insert to authenticated with check (author_id = auth.uid());
create policy "authors can update team requirements" on public.team_requirements for update to authenticated using (author_id = auth.uid()) with check (author_id = auth.uid());
create policy "authors can delete team requirements" on public.team_requirements for delete to authenticated using (author_id = auth.uid());

create policy "authenticated users can view requirement responses" on public.team_requirement_responses for select to authenticated using (true);
create policy "users can respond as themselves" on public.team_requirement_responses for insert to authenticated with check (user_id = auth.uid());
create policy "users can remove own requirement response" on public.team_requirement_responses for delete to authenticated using (user_id = auth.uid());

-- Friend discovery can safely use the existing public profile fields.
-- Expose only authenticated profile reads; ranking/filtering stays in the client/repository.
create index profiles_course_year_semester_idx on public.profiles(course, year, semester);
create index profiles_location_idx on public.profiles(latitude, longitude);
