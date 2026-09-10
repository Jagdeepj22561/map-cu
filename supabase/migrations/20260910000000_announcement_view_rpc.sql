create or replace function public.increment_announcement_view(p_announcement_id text)
returns integer
language sql
security invoker
set search_path = public
as $$
    update public.announcements
    set view_count = view_count + 1,
        updated_at = now()
    where id = p_announcement_id
    returning view_count;
$$;

grant execute on function public.increment_announcement_view(text) to authenticated;
