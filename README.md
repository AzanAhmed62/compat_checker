# Compat Checker

Compat Checker is an evidence-backed hardware compatibility platform for repair technicians, hardware engineers, and device owners. It only reports compatibility when an explicit published rule exists. Unknown remains unknown, and any `not_compatible_with` rule overrides weaker positive claims.

The repository contains three surfaces:

- **Android app** - Kotlin, Jetpack Compose, Material 3, MVVM/UDF, Room offline cache, DataStore settings, English/Urdu with RTL, deep links.
- **Admin console** - React 19, TypeScript, Vite, TanStack Query, Supabase JS, plus public privacy and account-deletion routes.
- **Backend** - Supabase/PostgreSQL ordered migrations, RLS, explicit public views, server-side compatibility evaluation, tests, and Edge Functions.

## Repository layout

```text
apps/android       Native Android app
apps/admin_panel   Admin web console and public legal/deletion pages
supabase           Migrations, seed, SQL tests, Edge Functions, config
scripts            Docker-free PostgreSQL test runners
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

## Local backend

Preferred when Docker and Supabase CLI are available:

```bash
supabase start
supabase db reset
```

`seed.sql` is synthetic local/test data. Never run it against production.

Docker-free PostgreSQL/RLS verification:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/db_test.ps1 -Keep
```

or on Bash:

```bash
./scripts/db_test.sh
```

The script refuses databases that do not begin with `compat_` and applies the test-only `supabase/tests/00_shim.sql` first. That shim must never be applied to production.

## Admin console

```powershell
cd apps/admin_panel
Copy-Item .env.example .env.local
npm.cmd ci
npm.cmd run typecheck
npm.cmd test
npm.cmd run build
```

Use `npm.cmd` on this Windows machine. The console uses only `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY`; authorization is enforced by RLS and admin RPCs.

Public routes:

- `/privacy` - deployment-specific privacy policy.
- `/delete-account` - signed-in account-deletion request and cancellation.

Set `VITE_DATA_CONTROLLER` and `VITE_SUPPORT_EMAIL` before publishing these pages. Do not deploy empty controller/support placeholders. The static host must rewrite SPA routes to `index.html`.

Edge Function CORS uses exact `ADMIN_ORIGIN` values. Wildcards are ignored; local development falls back to `http://localhost:5173` and `http://127.0.0.1:5173`.

## Android app

Add untracked `apps/android/local.properties` (Android Studio normally adds
`sdk.dir`; set it manually when building from the command line):

```properties
sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-public-anon-or-publishable-key
```

Local HTTP is accepted only in debug builds for emulator/local hosts. A key prefixed `sb_secret_` or a JWT declaring `service_role` is rejected at startup.

```powershell
cd apps/android
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
```

Set `GRADLE_USER_HOME` to a cache on a drive with free space if the default Gradle
home is constrained. The SDK platform and build tools for API 36 must be installed.
Close Android Studio before CLI builds on a low-memory machine.

Run instrumentation tests on a booted emulator/device:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Deep links:

- `compatchecker://product/{id}`
- `compatchecker://checker`
- `compatchecker://guide/{id}`
- `compatchecker://rule/{id}`

## Verification status

Observed in this workspace on 2026-10-24:

- Database: 6 SQL/RLS test files passed with the Docker-free PostgreSQL runner after all 11 migrations and seed.
- Admin: TypeScript build/typecheck passed; 12 Vitest tests passed in 2 files; production build transformed 181 modules (252.90 kB JS, 80.74 kB gzip; 16.84 kB CSS). Public pages have no live-backend UI test.
- Android: `:app:assembleDebug` passed; **7 JVM tests in 3 test classes** passed with no failures. The debug APK is 25,664,550 bytes (24.48 MiB), signed with the Android Debug certificate; SHA-256 is `87426311575EC8BC78A0F3F699173DCE52FF8D2A6CA8840AE6456150C9499F27`. The checker Compose instrumentation suite compiles; it has not run on a device/emulator. No live backend runtime has been exercised, so this is not a release validation.
- Edge Functions: Deno typecheck passed; **24 handler tests passed**. No hosted function, real admin JWT/database RPC, or scheduler invocation has been tested.
- CI has database, admin, Edge Function, and Android jobs. It has not run on GitHub Actions.
- No remote Supabase project, Play signing key, or Play Console access has been used.

See [`docs/KNOWN_ISSUES.md`](docs/KNOWN_ISSUES.md) for blockers.

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
- [Next task](NEXT_TASK.md)

## License and data

No production dataset is included. Seed records are fictional synthetic fixtures with `.local` evidence URLs intended only for local verification. No license file exists yet; add an explicit license before public distribution.
