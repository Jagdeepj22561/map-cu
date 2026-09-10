-- Idempotent fix: ensure SELECT policies exist for engagement tables.
-- On a fresh DB the initial migration (000000) already creates these.
-- On an existing DB where the tables were created earlier, the policies
-- may be missing. DROP IF EXISTS + CREATE makes this safe to re-run.

drop policy if exists "authenticated users can read announcement likes"
    on public.announcement_likes;
create policy "authenticated users can read announcement likes"
    on public.announcement_likes for select to authenticated using (true);

drop policy if exists "authenticated users can read announcement comments"
    on public.announcement_comments;
create policy "authenticated users can read announcement comments"
    on public.announcement_comments for select to authenticated using (true);

grant select on public.announcement_likes to authenticated;
grant select on public.announcement_comments to authenticated;
