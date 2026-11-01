# Next Task

## Current verified state (2026-10-24)

- Database/RLS: 6 SQL test files passed against a disposable PostgreSQL 18.6 cluster on port `55432` after applying the test shim, all 11 ordered migrations, and `seed.sql`.
- Admin: TypeScript build/typecheck passed; 12 Vitest tests in 2 files passed; Vite production build transformed 181 modules (252.90 kB JS, 80.74 kB gzip; 16.84 kB CSS).
- Android: `:app:assembleDebug` passed; 7 JVM tests in 3 test classes passed with no failures/errors/skips. Debug APK: `apps/android/app/build/outputs/apk/debug/app-debug.apk`, 25,664,550 bytes (24.48 MiB), SHA-256 `87426311575EC8BC78A0F3F699173DCE52FF8D2A6CA8840AE6456150C9499F27`, signed with the Android Debug certificate. The checker Compose instrumentation suite compiles (`:app:assembleDebugAndroidTest`, test APK 996,982 bytes); no device/emulator has executed it.
- Edge Functions: Deno typecheck passed; 24 handler tests passed with 0 failures. This covers exact-origin CORS, malformed JSON, auth mapping, CSV limits/formula neutralization, export failure, purge secrets, bounded limits, and error redaction.
- Public admin routes: `/privacy` and `/delete-account` are implemented and built. They require `VITE_DATA_CONTROLLER`, `VITE_SUPPORT_EMAIL`, deployment, and an external URL before Play declarations.
- CI: `.github/workflows/ci.yml` defines database, admin, Edge Function, and Android jobs; it has not run on GitHub Actions.
- No remote Supabase project, Play Console access, signing key, deployed privacy/deletion URL, or store assets are available. None were used.

## Priority 1: Full-stack verification

Docker Desktop is installed but its Linux engine was unavailable at the last check (`dockerDesktopLinuxEngine` pipe missing). No `supabase start` or `db reset` has been completed. This is the largest remaining backend blocker.

1. Close Android Studio and other memory-heavy apps. Start Docker Desktop and confirm `docker info` uses the Linux engine.
2. Start a local stack with enough free memory, applying all migrations and local seed:
   ```bash
   supabase start
   supabase db reset
   ```
3. Verify Auth email/password sign-in, session refresh, suspension/ban enforcement, and local confirmation policy.
4. Verify PostgREST exposure and RLS: anonymous browsing/search/detail/checker/guides, vote aggregates without voter rows, private member tables unreachable with the anon key.
5. Verify Storage policies with approved media types and sizes.
6. Exercise the admin console against the live stack: wrong-role rejection, catalog publish, rules/evidence, rollback, reports, contributions, comments, requests, users/roles, guides, media, CSV, analytics, audit logs, pagination, and error states.
7. Install the Android debug APK with a local public URL/anon key and exercise anonymous and signed-in flows, offline cache, deep links, RTL, and dark theme.
8. Record exact results in `docs/TEST_PLAN.md` and update `docs/KNOWN_ISSUES.md`.

Never connect to, reset, or mutate a remote Supabase project without explicit owner authorization.

## Priority 2: Android runtime and accessibility

- Boot the existing `Medium_Phone_API_37.0` AVD or attach a device, then run:
  ```powershell
  cd apps/android
  .\gradlew.bat :app:connectedDebugAndroidTest
  ```
- Current Compose coverage is the checker result summary (unknown, compatible, incompatible, same-product). Add browse, detail, auth prompt, deep links, offline fallback, RTL, and dark-theme tests.
- Verify favorites, votes, comments, requests, contributions, notifications, account-deletion request/cancel, and session expiry against the full stack.
- Complete TalkBack, switch access, 200% font-scale, and native-speaker Urdu review.
- Address remaining functional gaps: collections/follows UI, richer contribution forms, Markdown guide rendering, all notification link shapes, offline guides/requests, and media upload.

## Priority 3: Edge Functions and deployment

- Deploy to an authorized staging project and invoke both functions with real admin JWTs and database RPCs.
- Configure exact `ADMIN_ORIGIN` (comma-separated, no wildcard), `PURGE_CRON_SECRET`, and service-role secrets server-side.
- Schedule and monitor the purge worker.
- Re-run live-stack tests after any function or migration change.

## Priority 4: CI and release

1. Push the workflow and fix any runner-only failures; it has not executed remotely.
2. Deploy the admin site with `/privacy` and `/delete-account`, configure the data controller and support email, and verify SPA fallback and CORS.
3. Produce a signed AAB with an upload key stored outside the repository.
4. Complete data-safety/content-rating declarations and produce store listing assets, including the deployed privacy policy and account-deletion URLs.
5. Validate the release AAB, R8 mapping, deep links, and rollback plan in an authorized environment.

## Verification commands

Database:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/db_test.ps1 -Keep -HostName 127.0.0.1 -Port 55432
```

Admin:

```powershell
cd apps/admin_panel
npm.cmd run typecheck
npm.cmd test
npm.cmd run build
```

Android (close Android Studio first on this low-memory machine):

```powershell
cd apps/android
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --console=plain --no-configuration-cache --no-daemon --max-workers=1
```

Edge Functions (from repository root):

```powershell
$env:DENO_DIR='C:\DEV\build-cache\deno'
C:\DEV\build-cache\tools\deno\node_modules\.bin\deno.cmd check supabase/functions/_shared/auth.ts supabase/functions/admin-csv/index.ts supabase/functions/admin-csv/handler.ts supabase/functions/purge-pending-accounts/index.ts supabase/functions/purge-pending-accounts/handler.ts supabase/functions/tests/auth_test.ts supabase/functions/tests/admin_csv_test.ts supabase/functions/tests/purge_test.ts
C:\DEV\build-cache\tools\deno\node_modules\.bin\deno.cmd test --allow-env supabase/functions/tests
```

## Do not

- Do not connect to, reset, or mutate a remote Supabase project without explicit owner authorization.
- Do not run `seed.sql` or `supabase/tests/00_shim.sql` against production.
- Do not weaken RLS, expose vote rows, or infer compatibility without explicit published rules.
- Do not put a service-role key, signing password, or other secret in a client build or tracked file.
- Do not invent test counts, credentials, deployment status, store assets, or runtime results.
