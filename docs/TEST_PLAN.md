# Test Plan

## Verified locally

These are exact, observed results from this workspace. They do not imply production readiness.

### Database and RLS

Command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/db_test.ps1 -Keep -HostName 127.0.0.1 -Port 55432
```

Last verified result on 2026-10-21: **6 test files passed** against a disposable PostgreSQL 18.6 cluster on port `55432`, after applying the test shim, all 11 ordered migrations, and `seed.sql`. The runner was also corrected so Windows `dropdb` notices are no longer treated as fatal PowerShell errors.

Files:

- `10_schema.sql` — tables, enums, constraints, indexes, seed counts.
- `20_public_api.sql` — public views, search, product detail, guide detail, compatibility precedence.
- `30_member_rls.sql` — member isolation, favorites, comments, requests, votes, notifications.
- `40_moderation.sql` — report/moderation/role paths and audit.
- `50_contributions.sql` — contribution validation, review, points, withdrawal.
- `60_lifecycle_rollback.sql` — versions, rollback, CSV, media, account purge.

`00_shim.sql` is test-only and must never be applied to a production database. The runner refuses database names that do not begin with `compat_`.

### Admin console

Commands:

```powershell
cd apps/admin_panel
npm.cmd run typecheck
npm.cmd test
npm.cmd run build
```

Last verified result on 2026-10-21: typecheck passed; Vitest passed **12 tests in 2 files**; production build passed.

Covered tests:

- CSV parsing/serialization and CSV injection neutralization.
- Permission/capability helpers.

### Android

Commands (from `apps/android`; the observed run used the portable Gradle 8.13 distribution, and `.\gradlew.bat` is equivalent):

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12'
$env:GRADLE_USER_HOME='D:\DEV\build-cache\gradle-home'
$env:TEMP='D:\DEV\build-cache\temp'
$env:TMP='D:\DEV\build-cache\temp'
& 'D:\DEV\tools\gradle-8.13\bin\gradle.bat' :app:assembleDebug :app:testDebugUnitTest --console=plain --no-configuration-cache --no-daemon --max-workers=1
```

Last verified result on 2026-10-21: **BUILD SUCCESSFUL in 10m 54s**, 47 actionable tasks (23 executed, 6 from cache, 18 up-to-date). **7 JVM tests passed** across 3 test classes in 2 files, with 0 failures, 0 errors, and 0 skipped. Debug APK: `apps/android/app/build/outputs/apk/debug/app-debug.apk`, **24.48 MiB** (25,664,670 bytes). `apksigner verify` confirmed it is signed with the Android Debug certificate. The APK is a debug build; its Supabase configuration is blank unless `local.properties`/environment supplied values, and no device, emulator, or live backend runtime was exercised.

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
- Manual integration against a full Supabase stack (blocked until Docker/Supabase CLI are available):
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
- Compose UI tests for browse, detail, checker unknown/incompatible states, auth prompt, RTL, and dark theme are not implemented.
- Instrumented integration tests against a local Supabase stack:
  - anonymous browse/search/detail/checker/guides
  - sign-in/sign-out and session persistence
  - favorite, vote, comment, request, contribution, notification read
  - deep links for product/checker/guide
  - offline cached browse after network loss
  - account deletion request/cancel

### Edge Functions

Blocked locally because Deno and Supabase CLI are absent.

Required tests in an authorized environment:

- `admin-csv` rejects missing/invalid JWT and non-admin JWT; accepts admin; enforces size/row limits.
- `purge-pending-accounts` rejects missing/wrong secret; accepts configured secret; returns bounded invocation result.
- No function logs secrets or full user tokens.

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
| UX | light/dark, English/Urdu RTL, font scale 200%, TalkBack, small screen, offline |
| Security | no key in APK/strings/logs; protected tables unreachable via anon; draft data absent |
