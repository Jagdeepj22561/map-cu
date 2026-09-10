-- Fields retained from existing Firestore announcements.
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
create policy "users can create announcements" on public.announcements for insert to authenticated with check (author_id = auth.uid());
create policy "authors can update announcements" on public.announcements for update to authenticated using (author_id = auth.uid()) with check (author_id = auth.uid());
create policy "authors can delete announcements" on public.announcements for delete to authenticated using (author_id = auth.uid());
create policy "users can create reports" on public.reports for insert to authenticated with check (reporter_id = auth.uid());
grant select, insert, update, delete on public.announcements to authenticated;
grant insert on public.reports to authenticated;
