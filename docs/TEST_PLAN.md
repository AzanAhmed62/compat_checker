# Test Plan

## Verified locally

These are exact, observed results from this workspace. They do not imply production readiness.

### Database and RLS

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/db_test.ps1 -Keep -HostName 127.0.0.1 -Port 55432
```

Last verified result on 2026-10-24: **6 test files passed** against a disposable PostgreSQL 18.6 cluster on port `55432`, after applying the test shim, all 11 ordered migrations, and `seed.sql`. All six files printed `PASS`. The database was kept for inspection. The runner was also corrected so Windows `dropdb` notices are no longer treated as fatal PowerShell errors.

Files:

- `10_schema.sql` - tables, enums, constraints, indexes, seed counts.
- `20_public_api.sql` - public views, search, product detail, guide detail, compatibility precedence.
- `30_member_rls.sql` - member isolation, favorites, comments, requests, votes, notifications.
- `40_moderation.sql` - report/moderation/role paths and audit.
- `50_contributions.sql` - contribution validation, review, points, withdrawal.
- `60_lifecycle_rollback.sql` - versions, rollback, CSV, media, account purge.

`00_shim.sql` is test-only and must never be applied to a production database. The runner refuses database names that do not begin with `compat_`.

### Admin console

Commands:

```powershell
cd apps/admin_panel
npm.cmd run typecheck
npm.cmd test
npm.cmd run build
```

Last verified result on 2026-10-24: typecheck passed; Vitest passed **12 tests in 2 files**; production build passed with **181 modules transformed** (252.90 kB JS, 80.74 kB gzip; 16.84 kB CSS). The public `/privacy` and `/delete-account` routes are included in the build but have no browser/live-backend integration test.

Covered tests:

- CSV parsing/serialization and CSV injection neutralization.
- Permission/capability helpers.

### Android

Commands (from `apps/android`, using the project wrapper and JDK 21):

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12'
.\gradlew.bat --offline --no-build-cache :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --console=plain --no-configuration-cache --no-daemon --max-workers=1
```

Last verified result on 2026-10-24:

- `:app:assembleDebug`, `:app:testDebugUnitTest`, and `:app:assembleDebugAndroidTest` complete successfully on the final source.
- **7 JVM tests passed** across 3 test classes in 2 files (`CatalogRepositoryOfflineTest` 3, `CompatibilityMappingTest` 2, `CorrectionPatchTest` 2), with 0 failures, 0 errors, and 0 skipped.
- Debug APK: `apps/android/app/build/outputs/apk/debug/app-debug.apk`, **25,664,550 bytes (24.48 MiB)**, SHA-256 `87426311575EC8BC78A0F3F699173DCE52FF8D2A6CA8840AE6456150C9499F27`.
- `apksigner verify --print-certs` confirms the Android Debug certificate (`CN=Android Debug`); `aapt2 dump badging` reports package `com.compatchecker.app.debug`, version `1.0.0-debug`, minSdk 26, targetSdk 36.
- Instrumentation test APK: `apps/android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`, **996,982 bytes**. It contains 4 Compose tests for unknown, compatible, incompatible, and same-product checker summaries. They have not run on a device/emulator.
- The first combined run failed while compiling the new instrumentation test because of an invalid `assertDoesNotExist` import. The import was removed; the re-run compiled the test APK and the final combined command passed.
- Warnings observed: Android SDK XML version 4 vs. tooling, `platform-tools-2` in an unexpected SDK location, native libraries `libandroidx.graphics.path.so` and `libdatastore_shared_counter.so` not stripped, and one corrupt Gradle `classAnalysis.bin` cache entry that Gradle discarded and rebuilt. None blocked the build.

The APK is a debug build; its Supabase configuration is blank unless `local.properties`/environment supplied values. No device, emulator, or live backend runtime was exercised.

### Edge Functions

Commands (from the repository root, with Deno 2.9.6):

```powershell
$env:DENO_DIR='C:\DEV\build-cache\deno'
C:\DEV\build-cache\tools\deno\node_modules\.bin\deno.cmd check supabase/functions/_shared/auth.ts supabase/functions/admin-csv/index.ts supabase/functions/admin-csv/handler.ts supabase/functions/purge-pending-accounts/index.ts supabase/functions/purge-pending-accounts/handler.ts supabase/functions/tests/auth_test.ts supabase/functions/tests/admin_csv_test.ts supabase/functions/tests/purge_test.ts
C:\DEV\build-cache\tools\deno\node_modules\.bin\deno.cmd test --allow-env supabase/functions/tests
```

Last verified result on 2026-10-24: typecheck passed; **24 tests passed, 0 failed** across three test files. Coverage includes exact-origin CORS and wildcard rejection, auth failure mapping, malformed JSON, CSV row limits, formula injection neutralization, export failure handling, purge secret comparison, bounded limits, service-role-only client creation, and error redaction.

Not run: deployment to a hosted Supabase project, live function HTTP invocation, real admin JWT/database RPC behavior, and scheduler execution. These require the full stack or an authorized staging project.

## Test strategy

### SQL/security

- Apply all migrations in filename order to an empty disposable database.
- Apply deterministic seed only in local/test.
- Test as anonymous, member, trusted, admin, suspended, banned, and deletion-pending roles using JWT claim setup.
- Assert both positive and negative access: unauthorized calls must fail, not merely return empty.
- Test vote privacy by verifying aggregates are readable while voter rows are not.
- Test incompatibility precedence with pairs containing positive and negative rules.
- Test idempotent favorites, one vote per user, request vote toggling, notification ownership, report uniqueness, contribution required fields, evidence URL rejection, rollback, CSV import, and account purge.
- Test public views never expose draft/private columns.

### Admin

- Unit-test pure helpers: CSV serialization/parsing, injection neutralization, permission gating.
- Typecheck all routes and API types.
- Production-build with only Vite public variables.
- Browser-check the public `/privacy` and `/delete-account` routes, including missing configuration, sign-in, pending state, and cancellation.
- Manual integration against a full Supabase stack (not run):
  - sign-in and wrong-role rejection
  - catalog create/update/publish
  - rule/evidence creation and rollback
  - report/comment/request moderation
  - user role/status changes and forced re-auth behavior
  - CSV upload/download
  - audit log visibility
  - pagination and error states

### Android

- Implemented JVM unit tests cover offline catalog search/filter/cache-miss behavior, compatibility DTO mapping (including incompatibility and unknown), and structured correction payload construction.
- Implemented Compose instrumentation tests cover unknown, incompatible, compatible, and same-product checker summaries. They compile with `:app:assembleDebugAndroidTest`; no emulator/device session has executed them.
- Instrumented integration tests against a local Supabase stack (not run):
  - anonymous browse/search/detail/checker/guides
  - sign-in/sign-out and session persistence
  - favorite, vote, comment, request, contribution, notification read
  - deep links for product/checker/guide
  - offline cached browse after network loss
  - account deletion request/cancel
  - RTL, dark theme, font scale, and TalkBack

### Edge Functions

Covered by local handler tests: origin allow-list and wildcard rejection, missing/invalid auth, non-admin role rejection, malformed JSON, bounded CSV rows, CSV formula injection, partial export failure, service-role client use, purge secret comparison, bounded purge limit, and error redaction.

Still required in an authorized environment:

- Deploy both functions and invoke them over HTTP with real JWTs and a live database.
- Verify scheduler credentials, function secrets, and `ADMIN_ORIGIN` for the deployed admin origin.
- Confirm logs contain no secrets or full tokens.

## Manual release matrix

| Area | Cases |
|---|---|
| Auth | sign in, sign out, expired session, suspended account, wrong password |
| Search | empty query, exact model, alias, no results, long query, category/brand filters, paging |
| Product | detail, specs, aliases, missing media, favorite while signed out/in, comments |
| Checker | same product, unknown pair, compatible, incompatible with positive rule present, evidence links |
| Rules | evidence display, confirm/dispute, own vote state |
| Guides | empty list, detail, steps, missing images, deep link |
| Requests | anonymous public list, submit signed out/in, vote toggle, aggregate count |
| Profile | me, reputation, favorites, contributions, notifications, deletion request/cancel |
| Admin | role-denied route, each CRUD page, pagination, error banner, rollback |
| Public pages | privacy with configured/missing controller, deletion sign-in, request, pending, cancel |
| UX | light/dark, English/Urdu RTL, font scale 200%, TalkBack, small screen, offline |
| Security | no key in APK/strings/logs; protected tables unreachable via anon; draft data absent |
