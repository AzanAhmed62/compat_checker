# Roadmap

## Completed in this implementation

### Foundation and audit
- Repository audit and baseline preservation.
- Replaced demo-only assumptions with an evidence-backed platform specification.
- Established ordered migrations, deterministic synthetic seed, local SQL test runner, and documentation set.

### Backend
- Eleven ordered migrations: core, identity, catalog, rules, community, reputation, social, analytics, admin, hardening, purge.
- RLS, explicit public views, server-side compatibility evaluation, moderation, contribution review, rule versions/rollback, CSV import, analytics, audit logs, media policy, account deletion/purge.
- Six SQL/security test files passing against disposable PostgreSQL.

### Admin console
- Login/auth gate, role-aware navigation, dashboard, catalog CRUD, rules/evidence and rollback, reports, contributions, comments, requests, users/roles, guides, media, analytics, audit logs, CSV tools.
- Typecheck, Vitest suite, and production build passing.

### Android
- Kotlin/Compose Material 3 app with manual DI, navigation, deep links, debug-only local HTTP exceptions, build-time configuration validation, English/Urdu resources, and RTL support.
- Data layer for Supabase Auth/PostgREST, Room offline cache, DataStore preferences, and domain repository interfaces.
- Catalog/search, product detail, checker, rules/evidence, guides, requests, auth, profile, favorites, contributions, notifications, and settings screens.
- `:app:assembleDebug` passes; 7 JVM unit tests pass across 3 test classes. The debug APK is 24.48 MiB. No device/emulator or live backend runtime has been exercised.

## Next

1. Run a full Supabase stack (Auth, PostgREST, Storage, Realtime disabled) and exercise admin and app flows against it.
2. Validate Edge Functions with Deno/Supabase CLI in an authorized environment.
3. Add Android instrumentation/Compose tests, install the APK on a device/emulator, and complete a TalkBack/RTL/font-scale accessibility pass.
4. Execute the defined GitHub Actions CI workflow (database, admin, Android) on a runner; it has not been run remotely yet.
6. Produce a signed AAB and complete Play Console data-safety declarations.
7. Conduct a security review of admin actions, rate limits, and secret handling before production.

## Later

- Collection management UI on Android.
- Full notification deep-link handling for all server link shapes.
- Rich Markdown rendering for guides.
- Advanced admin media upload workflow and evidence viewer.
- Offline detail for guides and requests.
- Localization expansion beyond English and Urdu after review.
- Performance work for large catalogs and rule graphs.

## Release gates

A release is blocked until:

- All migrations and SQL tests pass in CI.
- Admin typecheck, tests, and build pass.
- Android debug build and unit tests pass (verified); instrumentation/runtime behavior and a release build with a real signing key are still required.
- Edge Functions have execution evidence.
- No service-role key or signing secret is present in tracked files or build artifacts.
- `docs/KNOWN_ISSUES.md` is reviewed and deployment-specific risks are closed or explicitly accepted.
