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
- Public `/privacy` and `/delete-account` routes with configurable data-controller and support contact.
- Typecheck, Vitest suite, and production build passing.

### Edge Functions
- `admin-csv` and `purge-pending-accounts` split into testable handlers with restrictive exact-origin CORS, auth/RPC error mapping, bounded CSV import, formula neutralization, and bounded service-role purge.
- Deno typecheck and 24 handler tests pass locally.

### Android
- Kotlin/Compose Material 3 app with manual DI, navigation, deep links, debug-only local HTTP exceptions, build-time configuration validation, English/Urdu resources, and RTL support.
- Data layer for Supabase Auth/PostgREST, Room offline cache, DataStore preferences, and domain repository interfaces.
- Catalog/search, product detail, checker, rules/evidence, guides, requests, auth, profile, favorites, contributions, notifications, and settings screens.
- `:app:assembleDebug` passes; JVM unit tests pass. Compose instrumentation tests for checker result states compile; no device/emulator or live backend runtime has been exercised.

## Next

1. Run a full Supabase stack (Auth, PostgREST, Storage, Realtime disabled) and exercise admin and app flows against it.
2. Compile and run the Compose instrumentation tests on a booted emulator/device, then complete a TalkBack/RTL/font-scale accessibility pass.
3. Execute the defined GitHub Actions workflow (database, admin, Edge Functions, Android) on a runner; it has not been run remotely yet.
4. Produce a signed AAB and complete Play Console data-safety declarations, including deployed privacy and deletion URLs.
5. Conduct a security review of admin actions, rate limits, and secret handling before production.

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
