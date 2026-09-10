-- Groups feature: complete the existing group tables with discovery metadata
-- and secure client-side membership operations.

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
create policy "authenticated users can read groups"
    on public.groups for select to authenticated
    using (is_public = true or owner_id = auth.uid() or exists (
        select 1 from public.group_members gm
        where gm.group_id = groups.id and gm.user_id = auth.uid()
    ));

drop policy if exists "users can create groups" on public.groups;
create policy "users can create groups"
    on public.groups for insert to authenticated
    with check (owner_id = auth.uid());

drop policy if exists "group admins can update groups" on public.groups;
create policy "group admins can update groups"
    on public.groups for update to authenticated
    using (owner_id = auth.uid() or exists (
        select 1 from public.group_members gm
        where gm.group_id = groups.id
          and gm.user_id = auth.uid()
          and gm.role in ('owner', 'admin')
    ))
    with check (owner_id = auth.uid() or exists (
        select 1 from public.group_members gm
        where gm.group_id = groups.id
          and gm.user_id = auth.uid()
          and gm.role in ('owner', 'admin')
    ));

drop policy if exists "users can join groups" on public.group_members;
create policy "users can join groups"
    on public.group_members for insert to authenticated
    with check (user_id = auth.uid() and role = 'member');

drop policy if exists "users can read group members" on public.group_members;
create policy "users can read group members"
    on public.group_members for select to authenticated
    using (user_id = auth.uid() or exists (
        select 1 from public.groups g
        where g.id = group_members.group_id
          and (g.is_public = true or g.owner_id = auth.uid())
    ));

drop policy if exists "users can leave groups" on public.group_members;
create policy "users can leave groups"
    on public.group_members for delete to authenticated
    using (user_id = auth.uid() and role <> 'owner');

drop policy if exists "group members can read group messages" on public.group_messages;
create policy "group members can read group messages"
    on public.group_messages for select to authenticated
    using (exists (
        select 1 from public.group_members gm
        where gm.group_id = group_messages.group_id
          and gm.user_id = auth.uid()
    ));

drop policy if exists "group members can send group messages" on public.group_messages;
create policy "group members can send group messages"
    on public.group_messages for insert to authenticated
    with check (
        sender_id = auth.uid()
        and exists (
            select 1 from public.group_members gm
            where gm.group_id = group_messages.group_id
              and gm.user_id = auth.uid()
        )
    );

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

alter publication supabase_realtime add table public.groups;
alter publication supabase_realtime add table public.group_members;
