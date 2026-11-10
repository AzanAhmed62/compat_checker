# Deployment

This document describes deployment preparation. No remote Supabase project, Play signing key, or production credential has been used or validated in this workspace.

## Prerequisites

- Node.js 22+ and npm
- JDK 17+ (JDK 21 verified locally)
- Android SDK platform 36 and build-tools 36.x
- Docker engine and Supabase CLI for the full local stack (Auth/PostgREST/RLS and Storage object policies verified locally on 2026-11-09; live Edge Function invocation still pending)
- Deno 2.x for Edge Function typechecking and tests
- PostgreSQL client tools and a disposable server for the plain-PostgreSQL test runner
- An authorized Supabase project for remote deployment

## Local backend

Preferred path:

```bash
supabase start
supabase db reset
```

`supabase db reset` applies the ordered migrations and `seed.sql` to the **local** database. Never run `seed.sql` against production: it creates synthetic accounts, experimental data, and local-only evidence URLs.

Plain-PostgreSQL SQL/RLS verification in this workspace (Linux/macOS):

```bash
PGPASSWORD=postgres PGUSER=supabase_admin PGPORT=55432 ./scripts/db_test.sh --keep
```

Windows: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/db_test.ps1 -Keep`.

This uses a test-only Supabase shim. The shim is not a deployment artifact.

Local API: `http://127.0.0.1:55321`; database: `127.0.0.1:55322`; Studio is
disabled by default (port `55323` if enabled); local SMTP: `127.0.0.1:55324`.
The pooler is disabled. These ports deliberately do not overlap the default
Supabase ports used by other projects on the same workstation.
Seed admin: `admin@compatchecker.test` / `CompatLocal!2026` (local synthetic data only).

## Admin console

```bash
cd apps/admin_panel
cp .env.example .env.local
# Set VITE_SUPABASE_URL and VITE_SUPABASE_ANON_KEY from local or authorized project.
npm ci
npm run typecheck
npm test
npm run build
```

On Windows use `npm.cmd`.

Deploy the `dist` directory to a static host. Configure SPA fallback to `index.html` for `/privacy`, `/delete-account`, and all admin routes. Set `VITE_DATA_CONTROLLER` and `VITE_SUPPORT_EMAIL` before publishing; these values are rendered on the public privacy page. The console holds only the public anon/publishable key; database RLS and admin RPC checks are authoritative.

The public web surfaces are:

- `/privacy` — deployment-specific privacy policy.
- `/delete-account` — self-service account-deletion request and cancellation. It is not an authenticated admin route.

Both require the same public Supabase URL and anon key as the console. Do not deploy the privacy page with empty controller/support placeholders, and do not put a service-role key in the browser bundle.

`ADMIN_ORIGIN` is a comma-separated list of exact browser origins allowed to call the Edge Functions. The default is local-only (`http://localhost:5173` and `http://127.0.0.1:5173`). A wildcard is ignored, not honored. Set it to the deployed admin origin before using CSV tools:

```bash
supabase secrets set ADMIN_ORIGIN=https://admin.example.com
```

## Edge Functions

In an authorized environment with Supabase CLI and Deno:

```bash
supabase functions deploy admin-csv
supabase functions deploy purge-pending-accounts --no-verify-jwt
```

Local typecheck and tests do not need a Supabase project:

```powershell
deno check supabase/functions/_shared/auth.ts supabase/functions/admin-csv/index.ts supabase/functions/admin-csv/handler.ts supabase/functions/purge-pending-accounts/index.ts supabase/functions/purge-pending-accounts/handler.ts supabase/functions/tests/auth_test.ts supabase/functions/tests/admin_csv_test.ts supabase/functions/tests/purge_test.ts
deno test --allow-env supabase/functions/tests
```

These cover handler behavior, CORS/origin rejection, auth failure mapping, CSV limits and formula neutralization, purge secrets, and bounded service-role invocations. They do not replace a live-stack test with a real admin JWT and database.

Configure secrets:

```bash
supabase secrets set ADMIN_ORIGIN=https://admin.example.com
supabase secrets set PURGE_CRON_SECRET=<high-entropy-random-value>
```

`SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `SUPABASE_SERVICE_ROLE_KEY` are provided by the hosted functions runtime or must be set through server-side secrets. Never put them in client environment files.

Schedule `purge-pending-accounts` with an external scheduler that sends `POST` and `x-cron-secret`. Keep the invocation frequency low (for example, daily) and monitor failures. The function is not authenticated by user JWT; the shared secret is the boundary.

## Remote Supabase

1. Obtain explicit written authorization from the project owner.
2. Back up the target database and confirm the project reference.
3. Review migrations and the production seed policy.
4. Link the CLI and run `supabase db push` (or the approved migration pipeline).
5. Confirm RLS, grants, function search paths, storage buckets, auth email settings, and redirect URLs.
6. Deploy functions and set secrets.
7. Create the first admin through an approved bootstrap process. Do not ship a default admin password.
8. Verify public anon access and blocked privileged access from a fresh client.
9. Enable monitoring, backups, and alerting.

Do not use `supabase db reset` on a remote project. Do not run `seed.sql` in production. Do not modify or reset a remote database without explicit authorization.

## Android

Add to `apps/android/local.properties` (untracked) or the build environment:

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-public-anon-or-publishable-key
```

The URL must be HTTPS for release. A service-role/secret key is rejected by the app configuration guard and must never be supplied.

Build and test:

```powershell
cd apps/android
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
```

Release builds require signing values and fail fast without them. See `docs/PLAY_STORE_RELEASE.md`.

## Configuration and operations checklist

- [ ] HTTPS enforced; TLS certificate valid.
- [ ] Only public keys in client artifacts.
- [ ] Admin MFA enabled at the identity provider or Supabase Auth policy.
- [ ] `ADMIN_ORIGIN` restricted.
- [ ] `PURGE_CRON_SECRET` strong and stored only in secret storage.
- [ ] Auth email confirmations and redirect URLs configured.
- [ ] Backups tested, not merely enabled.
- [ ] Logs do not contain tokens, credentials, or full private payloads.
- [ ] Rate limits reviewed for expected traffic.
- [ ] Audit log retention and access reviewed.
- [ ] Account deletion worker scheduled and monitored.
- [ ] Data retention and privacy policy published.
- [ ] Full-stack integration and Edge Function tests completed.

## Rollback

- Application rollback: redeploy the previous admin/mobile artifact.
- Database migrations: use a forward corrective migration. Do not edit applied migrations.
- Rule content: use admin version rollback; do not delete evidence history.
- Edge Functions: redeploy the prior reviewed version and rotate secrets if compromise is suspected.

## Known environment limitations

Docker, the Supabase CLI, and Deno are available in this workspace. The pinned local Supabase stack was started and reset on 2026-11-09, and Auth/PostgREST/RLS, Storage object policies, and the admin console were exercised over HTTP. Live Edge Function invocation and a production deployment were not. Edge Function typechecking and 24 handler tests pass, and instrumented Compose tests are compiled but have not run without a usable emulator/device. Do not treat local results as production release validation.
