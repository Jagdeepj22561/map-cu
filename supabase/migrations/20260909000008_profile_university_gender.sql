-- Adds the editable profile fields shown in the Android profile screen.
alter table public.profiles
    add column if not exists university text not null default '',
    add column if not exists gender text not null default '';
