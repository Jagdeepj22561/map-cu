# Supabase setup

1. In the Supabase dashboard, configure Brevo under **Authentication > SMTP Settings**.
2. Add `https://campus-map-backend-fpz8.onrender.com/auth/callback` to **Authentication > URL Configuration > Redirect URLs**. Do not add a custom-scheme recovery URL.
3. Apply every file in `migrations/` in timestamp order through the Supabase CLI or SQL Editor. The latest migration enforces ghost-profile visibility, community roles, and one-message chat invitations.
4. Add these **public** values to the untracked root `local.properties` file:

```properties
SUPABASE_URL=https://your-project-ref.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
```

Do not put `SUPABASE_SERVICE_ROLE_KEY` in this repository or the Android/iOS applications. Store it only in Render environment variables. Render uses it for the OTP-verified account creation endpoint.
