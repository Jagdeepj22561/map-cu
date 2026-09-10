-- Firebase Cloud Messaging still supplies Android device tokens. This allows
-- each signed-in user to register and refresh only their own token in Supabase.

create policy "users can insert their device tokens"
    on public.device_tokens for insert to authenticated
    with check (user_id = auth.uid());

create policy "users can update their device tokens"
    on public.device_tokens for update to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

create policy "users can delete their device tokens"
    on public.device_tokens for delete to authenticated
    using (user_id = auth.uid());

grant select, insert, update, delete on public.device_tokens to authenticated;
