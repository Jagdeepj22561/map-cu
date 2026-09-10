-- Fix infinite recursion between groups and group_members RLS policies.
-- The groups SELECT policy used group_members, while the group_members
-- SELECT policy used groups. A SECURITY DEFINER helper breaks that cycle.

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

-- Keep the member policy dependent on groups, but groups no longer depends
-- on the RLS-protected group_members relation, so the policies cannot recurse.
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
