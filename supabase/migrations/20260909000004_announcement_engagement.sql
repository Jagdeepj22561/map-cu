-- Enable the existing announcement likes and comments tables for signed-in users.
create policy "users manage their own announcement likes"
on public.announcement_likes for insert to authenticated
with check (user_id = auth.uid());

create policy "users remove their own announcement likes"
on public.announcement_likes for delete to authenticated
using (user_id = auth.uid());

create policy "users add their own announcement comments"
on public.announcement_comments for insert to authenticated
with check (author_id = auth.uid());

grant select, insert, delete on public.announcement_likes to authenticated;
grant select, insert on public.announcement_comments to authenticated;
