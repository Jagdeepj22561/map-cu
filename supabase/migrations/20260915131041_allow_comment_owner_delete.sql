-- Comments are publicly readable to authenticated app users, but only their
-- author may remove them.
drop policy if exists "users remove their own announcement comments"
    on public.announcement_comments;

create policy "users remove their own announcement comments"
    on public.announcement_comments
    for delete
    to authenticated
    using ((select auth.uid()) = author_id);

grant delete on public.announcement_comments to authenticated;
