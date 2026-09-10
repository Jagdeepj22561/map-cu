-- Fix group chat message RLS without recursive policy evaluation.
-- The client must be able to read/send messages only when the current user
-- belongs to the community (owners are members automatically).

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
