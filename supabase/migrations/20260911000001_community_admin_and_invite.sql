-- 20260911000001_community_admin_and_invite.sql
-- Community Admin Rights & Invite System Policies

-- 1. Ensure invite_code column exists on groups
alter table public.groups add column if not exists invite_code text unique;

-- 2. Grant update on group_members to authenticated users
grant select, insert, update, delete on public.group_members to authenticated;

-- 3. Drop existing delete policy and create comprehensive admin/member delete policy
drop policy if exists "users can leave groups" on public.group_members;
drop policy if exists "admins can remove members" on public.group_members;

create policy "admins can remove members"
    on public.group_members for delete to authenticated
    using (
        -- User can leave the group themselves (if not owner)
        (user_id = auth.uid() and role <> 'owner')
        or
        -- Group owner can remove any non-owner member or admin
        exists (
            select 1 from public.groups g
            where g.id = group_members.group_id
              and g.owner_id = auth.uid()
              and group_members.role <> 'owner'
        )
        or
        -- Admin can remove regular members
        exists (
            select 1 from public.group_members gm
            where gm.group_id = group_members.group_id
              and gm.user_id = auth.uid()
              and gm.role in ('owner', 'admin')
              and group_members.role = 'member'
        )
    );

-- 4. Drop existing insert policy and create policy allowing users to join OR admins to add members
drop policy if exists "users can join groups" on public.group_members;
create policy "users can join groups"
    on public.group_members for insert to authenticated
    with check (
        -- User joining themselves
        user_id = auth.uid()
        or
        -- Group owner or admin adding someone
        exists (
            select 1 from public.groups g
            where g.id = group_members.group_id
              and g.owner_id = auth.uid()
        )
        or
        exists (
            select 1 from public.group_members gm
            where gm.group_id = group_members.group_id
              and gm.user_id = auth.uid()
              and gm.role in ('owner', 'admin')
        )
    );

-- 5. Policy for updating member roles (promoting to admin / demoting)
drop policy if exists "admins can update member roles" on public.group_members;
create policy "admins can update member roles"
    on public.group_members for update to authenticated
    using (
        -- Only group owner can promote or demote members
        exists (
            select 1 from public.groups g
            where g.id = group_members.group_id
              and g.owner_id = auth.uid()
        )
    );

-- 6. Trigger to ensure creator is automatically inserted into group_members as owner
create or replace function public.handle_new_group()
returns trigger language plpgsql security definer as $$
begin
    insert into public.group_members (group_id, user_id, role)
    values (new.id, new.owner_id, 'owner')
    on conflict (group_id, user_id) do update set role = 'owner';
    return new;
end;
$$;

drop trigger if exists on_group_created on public.groups;
create trigger on_group_created
    after insert on public.groups
    for each row execute function public.handle_new_group();
