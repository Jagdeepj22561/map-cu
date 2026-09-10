-- Firebase-to-Supabase base schema. Run this before changing the client code.
-- Render creates auth.users after validating the Brevo OTP. This trigger creates
-- the matching public profile using the name supplied as user metadata.

create extension if not exists pgcrypto;

create table public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    email text not null unique,
    name text not null default '',
    phone_number text not null default '',
    year text not null default '',
    semester text not null default '',
    course text not null default '',
    dob text not null default '',
    profile_pic_url text not null default '',
    instagram_link text not null default '',
    snapchat_link text not null default '',
    linkedin_link text not null default '',
    latitude double precision not null default 0,
    longitude double precision not null default 0,
    ghost_mode boolean not null default false,
    last_updated bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create or replace function public.create_profile_for_auth_user()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
    insert into public.profiles (id, email, name)
    values (
        new.id,
        coalesce(new.email, ''),
        coalesce(new.raw_user_meta_data ->> 'name', '')
    )
    on conflict (id) do nothing;
    return new;
end;
$$;

create trigger on_auth_user_created
    after insert on auth.users
    for each row execute procedure public.create_profile_for_auth_user();

create table public.announcements (
    id text primary key,
    author_id uuid not null references public.profiles(id) on delete cascade,
    title text not null,
    content text not null,
    image_url text,
    type text not null check (type in ('NEWS', 'LOST_AND_FOUND', 'EVENT')),
    item_name text,
    place text,
    event_venue text,
    event_time text,
    event_purpose text,
    event_dl_type text,
    event_mode text,
    event_max_members integer,
    event_departments text,
    event_link text,
    share_count integer not null default 0,
    view_count integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.announcement_likes (
    announcement_id text not null references public.announcements(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (announcement_id, user_id)
);

create table public.announcement_comments (
    id uuid primary key default gen_random_uuid(),
    announcement_id text not null references public.announcements(id) on delete cascade,
    author_id uuid not null references public.profiles(id) on delete cascade,
    body text not null check (char_length(trim(body)) > 0),
    created_at timestamptz not null default now()
);

create table public.friend_requests (
    id uuid primary key default gen_random_uuid(),
    sender_id uuid not null references public.profiles(id) on delete cascade,
    receiver_id uuid not null references public.profiles(id) on delete cascade,
    status text not null default 'pending' check (status in ('pending', 'accepted', 'rejected')),
    created_at timestamptz not null default now(),
    unique (sender_id, receiver_id),
    check (sender_id <> receiver_id)
);

create table public.friendships (
    user_id uuid not null references public.profiles(id) on delete cascade,
    friend_id uuid not null references public.profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (user_id, friend_id),
    check (user_id <> friend_id)
);

create table public.direct_chats (
    id uuid primary key default gen_random_uuid(),
    created_at timestamptz not null default now(),
    last_message_at timestamptz
);

create table public.direct_chat_members (
    chat_id uuid not null references public.direct_chats(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    joined_at timestamptz not null default now(),
    primary key (chat_id, user_id)
);

create table public.messages (
    id uuid primary key default gen_random_uuid(),
    chat_id uuid not null references public.direct_chats(id) on delete cascade,
    sender_id uuid not null references public.profiles(id) on delete cascade,
    content text not null default '',
    image_url text,
    type text not null default 'TEXT',
    created_at timestamptz not null default now(),
    read_at timestamptz,
    check (char_length(trim(content)) > 0 or image_url is not null)
);

create table public.groups (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references public.profiles(id) on delete restrict,
    name text not null,
    description text not null default '',
    public_group_id text unique,
    invite_code text unique,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.group_members (
    group_id uuid not null references public.groups(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    role text not null default 'member' check (role in ('owner', 'admin', 'member')),
    joined_at timestamptz not null default now(),
    primary key (group_id, user_id)
);

create table public.group_messages (
    id uuid primary key default gen_random_uuid(),
    group_id uuid not null references public.groups(id) on delete cascade,
    sender_id uuid not null references public.profiles(id) on delete cascade,
    content text not null default '',
    image_url text,
    type text not null default 'TEXT',
    created_at timestamptz not null default now(),
    check (char_length(trim(content)) > 0 or image_url is not null)
);

create table public.blocks (
    user_id uuid not null references public.profiles(id) on delete cascade,
    blocked_user_id uuid not null references public.profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (user_id, blocked_user_id),
    check (user_id <> blocked_user_id)
);

create table public.device_tokens (
    token text primary key,
    user_id uuid not null references public.profiles(id) on delete cascade,
    platform text not null check (platform in ('android', 'ios')),
    updated_at timestamptz not null default now()
);

create index announcements_author_created_idx on public.announcements(author_id, created_at desc);
create index announcement_comments_announcement_created_idx on public.announcement_comments(announcement_id, created_at);
create index friend_requests_receiver_status_idx on public.friend_requests(receiver_id, status);
create index friendships_friend_idx on public.friendships(friend_id);
create index direct_chat_members_user_idx on public.direct_chat_members(user_id);
create index messages_chat_created_idx on public.messages(chat_id, created_at);
create index group_members_user_idx on public.group_members(user_id);
create index group_messages_group_created_idx on public.group_messages(group_id, created_at);

alter table public.profiles enable row level security;
alter table public.announcements enable row level security;
alter table public.announcement_likes enable row level security;
alter table public.announcement_comments enable row level security;
alter table public.friend_requests enable row level security;
alter table public.friendships enable row level security;
alter table public.direct_chats enable row level security;
alter table public.direct_chat_members enable row level security;
alter table public.messages enable row level security;
alter table public.groups enable row level security;
alter table public.group_members enable row level security;
alter table public.group_messages enable row level security;
alter table public.blocks enable row level security;
alter table public.device_tokens enable row level security;

-- The current app routes writes through Render. These policies permit only
-- authenticated read access; Render's service-role key bypasses RLS for writes.
create policy "authenticated users can read profiles" on public.profiles for select to authenticated using (true);
create policy "users can create their own profile" on public.profiles for insert to authenticated with check (id = auth.uid());
create policy "authenticated users can update their profile" on public.profiles for update to authenticated using (id = auth.uid()) with check (id = auth.uid());
grant select, insert, update on public.profiles to authenticated;
create policy "authenticated users can read announcements" on public.announcements for select to authenticated using (true);
create policy "authenticated users can read announcement likes" on public.announcement_likes for select to authenticated using (true);
create policy "authenticated users can read announcement comments" on public.announcement_comments for select to authenticated using (true);
create policy "users can read their requests" on public.friend_requests for select to authenticated using (sender_id = auth.uid() or receiver_id = auth.uid());
create policy "users can read their friendships" on public.friendships for select to authenticated using (user_id = auth.uid() or friend_id = auth.uid());
create policy "users can read their blocks" on public.blocks for select to authenticated using (user_id = auth.uid());
create policy "users can read their device tokens" on public.device_tokens for select to authenticated using (user_id = auth.uid());

-- Buckets are private. Render generates signed URLs after checking authorization.
insert into storage.buckets (id, name, public)
values ('profile-images', 'profile-images', false),
       ('announcement-images', 'announcement-images', false)
on conflict (id) do update set public = excluded.public;

-- Enable Supabase Realtime for the tables that replace Firestore listeners.
alter publication supabase_realtime add table public.announcements;
alter publication supabase_realtime add table public.friend_requests;
alter publication supabase_realtime add table public.messages;
alter publication supabase_realtime add table public.group_messages;
