-- Apply this after the initial migration if it was already run before the
-- Android Supabase-auth/profile integration was added.

alter table public.profiles
    alter column dob type text using coalesce(dob::text, ''),
    alter column dob set default '',
    alter column dob set not null;

alter table public.profiles
    add column if not exists last_updated bigint not null default 0;

do $$
begin
    if not exists (
        select 1 from pg_policies
        where schemaname = 'public'
          and tablename = 'profiles'
          and policyname = 'users can create their own profile'
    ) then
        create policy "users can create their own profile"
            on public.profiles for insert to authenticated
            with check (id = auth.uid());
    end if;
end;
$$;

grant select, insert, update on public.profiles to authenticated;
