# Compat Checker

Compat Checker is an evidence-backed hardware compatibility platform for repair technicians, hardware engineers, and device owners. It only reports compatibility when an explicit published rule exists. Unknown remains unknown, and any `not_compatible_with` rule overrides weaker positive claims.

The repository contains three surfaces:

- **Android app** - Kotlin, Jetpack Compose, Material 3, MVVM/UDF, Room offline cache (catalog and guides), DataStore settings, English/Urdu with RTL, deep links, collections, follows, public profiles, and leaderboards.
- **Admin console** - React 19, TypeScript, Vite, TanStack Query, Supabase JS, plus public privacy and account-deletion routes.
- **Backend** - Supabase/PostgreSQL ordered migrations, RLS, explicit public views, server-side compatibility evaluation, tests, and Edge Functions.

## Repository layout

```text
apps/android       Native Android app
apps/admin_panel   Admin web console and public legal/deletion pages
supabase           Migrations, seed, SQL tests, Edge Functions, config
scripts            Plain-PostgreSQL test runners
docs               Product, architecture, security, test, and release docs
```

## Core rules

1. Compatibility comes only from explicit, published rules. No inference from brand, connectors, similarity, or an LLM.
2. `not_compatible_with` wins over every weaker positive rule.
3. Public data is exposed through explicit-column views with published/visible filters.
4. Rule votes and request votes are aggregate-only in public responses.
5. Only the public anon/publishable key is allowed in clients. Service-role keys stay server-side.
6. Applied migrations are immutable; changes require a new ordered migration and tests.
7. Rule evidence is retired, not deleted.
8. Collections are public or private at the owner's choice; private collections are never returned to other members.

## Local backend

Preferred when Docker and Supabase CLI are available:

```bash
supabase start
supabase db reset
```

`seed.sql` is synthetic local/test data. Never run it against production.

Plain-PostgreSQL/RLS verification (Linux/macOS, requires PostgreSQL client
tools and a reachable disposable database; it does not start a server):

```bash
PGPASSWORD=postgres PGUSER=postgres PGPORT=55322 ./scripts/db_test.sh --keep
```

Windows:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/db_test.ps1 -Keep
```

The scripts refuse databases that do not begin with `compat_` and apply the
test-only `supabase/tests/00_shim.sql` first. That shim must never be applied to
production.

## Admin console

```bash
cd apps/admin_panel
cp .env.example .env.local
npm ci
npm run typecheck
npm test
npm run build
```

On Windows use `npm.cmd`. The console uses only `VITE_SUPABASE_URL` and
`VITE_SUPABASE_ANON_KEY`; authorization is enforced by RLS and admin RPCs.

Public routes:

- `/privacy` - deployment-specific privacy policy.
- `/delete-account` - signed-in account-deletion request and cancellation.

Set `VITE_DATA_CONTROLLER` and `VITE_SUPPORT_EMAIL` before publishing these pages. Do not deploy empty controller/support placeholders. The static host must rewrite SPA routes to `index.html`.

Edge Function CORS uses exact `ADMIN_ORIGIN` values. Wildcards are ignored; local development falls back to `http://localhost:5173` and `http://127.0.0.1:5173`.

## Android app

Add untracked `apps/android/local.properties` (Android Studio normally adds
`sdk.dir`; set it manually when building from the command line):

```properties
sdk.dir=/home/you/Android/Sdk
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-public-anon-or-publishable-key
```

Local HTTP is accepted only in debug builds for emulator/local hosts. A key prefixed `sb_secret_` or a JWT declaring `service_role` is rejected at startup.

Linux/macOS:

```bash
cd apps/android
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
```

Windows:

```powershell
cd apps/android
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
```

Set `GRADLE_USER_HOME` to a cache on a drive with free space if the default
Gradle home is constrained. The SDK platform and build tools for API 36 must be
installed. Close Android Studio before CLI builds on a low-memory machine.

Run instrumentation tests on a booted emulator/device:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Deep links:

- `compatchecker://product/{id}`
- `compatchecker://checker`
- `compatchecker://guide/{id}`
- `compatchecker://rule/{id}`
- `compatchecker://collection/{id}`
- `compatchecker://profile/{id}`

Version `1.2.0` adds:

- Atomic admin saves for products, guides, and rules/evidence through
  server-authorized RPCs, with server-side ownership and canonical relationship
  handling.
- Exactly one final rule version per admin update, versioning for direct
  published-evidence edits, and rollback that restores the exact snapshot
  evidence set.
- A required change reason for rule updates in the admin console.
- Android contribution forms for new products, new rules, evidence, and
  guides, with validated payload builders and English/Urdu labels.
- Localized enum/status/relationship/difficulty labels and remaining hardcoded
  Android UI strings.
- Eight HTTP-level Edge Function tests covering CORS, methods, JSON, CSV, and
  cron authorization over real localhost requests.
- CI updates for the HTTP test file and network permission.

## Verification status

Observed in this workspace on 2026-11-12 for the v1.2 source, with an Android
mutation pass on 2026-11-12/13:

- Database: **8 SQL/RLS test files passed** with the plain-PostgreSQL runner
  after all **13 migrations** and the updated seed. The live local stack was
  reset twice; the final state had 7 products, 5 rules, 1 guide, and 4 profiles.
- Admin: TypeScript typecheck passed; **15 Vitest tests passed in 3 files**;
  the production build passed. The browser sweep against the live local database
  covered atomic product, guide, and rule/evidence saves, change reasons,
  version counts, rollback, reports, contributions, comments, requests,
  users/roles/status and suspended-session denial, media, CSV export/import and
  rollback, analytics, audit filtering, pagination, and route gating with no
  unexpected console errors.
- Android: `:app:assembleDebug`, `:app:testDebugUnitTest`, and
  `:app:assembleDebugAndroidTest` passed; **28 JVM tests in 7 test classes**
  passed with no failures. Version is `1.2.0` (version code 3). The v1.2
  connected suite passed **5/5** on a physical OnePlus A6010 (Android 11) on
  2026-11-12. The live mutation pass verified all four contribution forms, rule
  voting, favorites, comments, product request submit/vote, account-deletion
  request/cancel, token refresh after expiry, and signed-out cleanup after
  session revocation. A 200% font-scale smoke test found clipped bottom-nav
  labels; see `docs/KNOWN_ISSUES.md`.
- Edge Functions: Deno typecheck passed; **32 tests passed** with 0 failures,
  including eight HTTP-level tests over ephemeral localhost servers with
  injected auth/database fakes. Live local invocation with the real Edge
  Runtime, admin JWT, and database covered CORS, CSV export, cron-secret
  enforcement, and an account purge. No hosted function, scheduler, or
  production deployment has been tested.
- Full local Supabase stack (2026-11-12): reset with the pinned
  `public.ecr.aws/supabase/postgres:17.11.0.011` image. Auth health and password
  sign-in returned 200; public views, RLS denials, social lifecycles, evidence
  URL validation, vote privacy, admin-only RPCs, Storage object policies, live
  Edge HTTP invocation, and the full admin browser sweep were exercised. Hosted
  deployment remains unverified.
- CI has database, admin, Edge Function, and Android jobs. The workflow
  succeeded on GitHub Actions at commit `58d30cc`; the v1.2 commits remain local
  because no GitHub credentials are configured, so their remote result has not
  been observed.
- No remote Supabase project, Play signing key, or Play Console access has been
  used.

Remaining release gaps are tracked in [`docs/KNOWN_ISSUES.md`](docs/KNOWN_ISSUES.md).

## Documentation

- [Repository audit](docs/REPOSITORY_AUDIT.md)
- [Product specification](docs/PRODUCT_SPEC.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Data model](docs/DATA_MODEL.md)
- [Security](docs/SECURITY.md)
- [Test plan](docs/TEST_PLAN.md)
- [Deployment](docs/DEPLOYMENT.md)
- [Play Store release](docs/PLAY_STORE_RELEASE.md)
- [Roadmap](docs/ROADMAP.md)
- [Known issues](docs/KNOWN_ISSUES.md)
- [v1.1 implementation report](docs/V1_1_IMPLEMENTATION_REPORT.md)
- [v1.2 implementation report](docs/V1_2_IMPLEMENTATION_REPORT.md)
- [Next task](NEXT_TASK.md)

## License and data

No production dataset is included. Seed records are fictional synthetic fixtures with `.local` evidence URLs intended only for local verification. No license file exists yet; add an explicit license before public distribution.
