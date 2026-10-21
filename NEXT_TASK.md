# Next Task

## Current verified state (2026-10-21)

- Database/RLS: 6 SQL test files passed against a disposable PostgreSQL 18.6 cluster on port `55432` after applying the test shim, all 11 ordered migrations, and `seed.sql`.
- Admin: TypeScript typecheck passed, 12 Vitest tests in 2 files passed, and the Vite production build passed.
- Android: `:app:assembleDebug` and `:app:testDebugUnitTest` passed. 7 JVM tests in 3 test classes passed with no failures/errors/skips. Debug APK: `apps/android/app/build/outputs/apk/debug/app-debug.apk`, 24.48 MiB.
- Edge Functions: source exists, but Deno and the Supabase CLI are unavailable; they have never been type-checked or executed.
- CI: `.github/workflows/ci.yml` is defined but has not been run on GitHub Actions.
- No remote Supabase project, Play Console access, signing key, privacy-policy URL, or store assets are available. None were used.

## Priority 1: Full-stack verification

Docker CLI and Supabase CLI are absent from this workspace. Install them (or use an explicitly authorized staging project) before claiming runtime readiness.

1. Start the local stack and apply the repository migrations and local seed:
   ```bash
   supabase start
   supabase db reset
   ```
2. Verify Auth email/password sign-in, session refresh, suspension/ban enforcement, and local confirmation policy.
3. Verify PostgREST exposure and RLS: anonymous browsing/search/detail/checker/guides, vote aggregates without voter rows, private member tables unreachable with the anon key.
4. Verify Storage policies with approved media types and sizes.
5. Exercise the admin console against the live stack: wrong-role rejection, catalog publish, rules/evidence, rollback, reports, contributions, comments, requests, users/roles, guides, media, CSV, analytics, audit logs, pagination, and error states.
6. Install the Android debug APK with a local public URL/anon key and exercise anonymous and signed-in flows, offline cache, deep links, RTL, and dark theme.
7. Record exact results in `docs/TEST_PLAN.md` and update `docs/KNOWN_ISSUES.md`.

## Priority 2: Edge Functions

1. Install Deno and run type checks.
2. Add execution tests for `admin-csv`: missing/invalid JWT, non-admin JWT, admin JWT, row limits, CSV export/import, and formula-injection neutralization.
3. Add execution tests for `purge-pending-accounts`: missing/wrong secret, configured secret, bounded limit, service-role-only behavior.
4. Confirm no function logs secrets or full tokens. Set a restrictive `ADMIN_ORIGIN`; the current default is `*`.

## Priority 3: Android runtime and accessibility

- Add instrumentation/Compose tests for browse, detail, checker unknown/compatible/incompatible states, auth prompt, deep links, offline fallback, RTL, and dark theme.
- Run on an emulator/device against the full stack; verify favorites, votes, comments, requests, contributions, notifications, account-deletion request/cancel, and session expiry.
- Complete TalkBack, switch access, 200% font-scale, and native-speaker Urdu review.
- Address remaining functional gaps: collections/follows UI, richer contribution forms, Markdown guide rendering, all notification link shapes, offline guides/requests, and media upload.

## Priority 4: CI and release

1. Push the workflow and fix any runner-only failures; it has not been executed remotely.
2. Produce a signed AAB with an upload key stored outside the repository.
3. Create the privacy policy and an externally reachable account-deletion path required by Play.
4. Complete data-safety/content-rating declarations and produce store listing assets.
5. Validate the release APK/AAB, R8 mapping, deep links, and rollback plan in an authorized environment.

## Verification commands

Database:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/db_test.ps1 -Keep
```

Admin:

```powershell
cd apps/admin_panel
npm.cmd run typecheck
npm.cmd test
npm.cmd run build
```

Android:

```powershell
cd apps/android
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12'
$env:GRADLE_USER_HOME='D:\DEV\build-cache\gradle-home'
$env:TEMP='D:\DEV\build-cache\temp'
$env:TMP='D:\DEV\build-cache\temp'
& 'D:\DEV\tools\gradle-8.13\bin\gradle.bat' :app:assembleDebug :app:testDebugUnitTest --console=plain --no-configuration-cache --no-daemon --max-workers=1
```

## Do not

- Do not connect to, reset, or mutate a remote Supabase project without explicit owner authorization.
- Do not run `seed.sql` against production.
- Do not weaken RLS, expose vote rows, or infer compatibility without explicit published rules.
- Do not put a service-role key, signing password, or other secret in a client build or tracked file.
- Do not invent test counts, credentials, deployment status, store assets, or runtime results.
