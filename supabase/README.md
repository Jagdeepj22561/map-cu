# Supabase setup

1. In the Supabase dashboard, configure Brevo under **Authentication > SMTP Settings**.
2. Add `maps123://auth` to **Authentication > URL Configuration > Redirect URLs**.
3. Run the migration in `migrations/20260908000000_initial_schema.sql` through the Supabase CLI or SQL Editor.
4. Add these **public** values to the untracked root `local.properties` file:

```properties
SUPABASE_URL=https://your-project-ref.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
```

Do not put `SUPABASE_SERVICE_ROLE_KEY` in this repository or the Android/iOS applications. Store it only in Render environment variables. Render uses it for the OTP-verified account creation endpoint.
