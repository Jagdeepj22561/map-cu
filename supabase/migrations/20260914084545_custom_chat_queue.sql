-- Temporary delivery only. No client can read/write this service-owned queue.
create table public.chat_delivery_queue (
    id uuid primary key,
    chat_id uuid not null,
    sender_id uuid not null references public.profiles(id) on delete cascade,
    recipient_id uuid not null references public.profiles(id) on delete cascade,
    content text not null default '' check (octet_length(content) <= 8000),
    image_url text check (length(image_url) <= 2048),
    type text not null check (type in ('TEXT', 'IMAGE')),
    created_at timestamptz not null default now(),
    expires_at timestamptz not null default now() + interval '72 hours',
    delivered_at timestamptz,
    check (sender_id <> recipient_id)
);
alter table public.chat_delivery_queue enable row level security;
revoke all on public.chat_delivery_queue from public, anon, authenticated;
grant select, insert, update, delete on public.chat_delivery_queue to service_role;
create index chat_delivery_pending_idx on public.chat_delivery_queue(recipient_id, created_at, id)
    where delivered_at is null;
create index chat_delivery_expiry_idx on public.chat_delivery_queue(expires_at);
-- ACK clears content immediately. Empty delivery tombstones remain until expiry
-- to prevent a retried send from recreating an already delivered message.
-- The backend purges expired rows at startup and hourly while running.
