# Roadmap

## Completed in this implementation

### Foundation and audit
- Repository audit and baseline preservation.
- Replaced demo-only assumptions with an evidence-backed platform specification.
- Established ordered migrations, deterministic synthetic seed, local SQL test runner, and documentation set.

### Backend
- Twelve ordered migrations: core, identity, catalog, rules, community, reputation, social, analytics, admin, hardening, purge, social read models.
- RLS, explicit public views, server-side compatibility evaluation, moderation, contribution review, rule versions/rollback, CSV import, analytics, audit logs, media policy, account deletion/purge, collection/profile read models, and evidence URL validation.
- Seven SQL/security test files passing against disposable PostgreSQL.

### Admin console
- Login/auth gate, role-aware navigation, dashboard, catalog CRUD, rules/evidence and rollback, reports, contributions, comments, requests, users/roles, guides, media, analytics, audit logs, CSV tools.
- Public `/privacy` and `/delete-account` routes with configurable data-controller and support contact.
- Typecheck, Vitest suite, and production build passing.

### Edge Functions
- `admin-csv` and `purge-pending-accounts` split into testable handlers with restrictive exact-origin CORS, auth/RPC error mapping, bounded CSV import, formula neutralization, and bounded service-role purge.
- Deno typecheck and 24 handler tests pass locally.

### Android
- Kotlin/Compose Material 3 app with manual DI, navigation, deep links, debug-only local HTTP exceptions, build-time configuration validation, English/Urdu resources, and RTL support.
- Data layer for Supabase Auth/PostgREST, Room offline cache (catalog and guides), DataStore preferences, and domain repository interfaces.
- Catalog/search with brand filters, product detail, checker, rules/evidence, guides with safe Markdown rendering, requests, auth, profile, favorites, collections, follows, public profiles, leaderboard, contributions, notifications, and settings screens.
- `:app:assembleDebug` passes; 17 JVM unit tests pass. On 2026-11-10 the connected suite passed 5/5 on a OnePlus 6T (Android 11), including the Room 1-to-2 migration and four checker Compose tests. Live smoke testing against the local API covered core browse/auth/social/guide/offline/deep-link/theme/RTL flows; broader mutation and accessibility coverage remains.

### Local full-stack verification (2026-11-09)
- The pinned local Supabase stack started and reset successfully. Auth health, password sign-in, PostgREST public views/RLS, collection/follow lifecycles, evidence URL validation, vote privacy, admin RPCs, and Storage object policies were exercised over HTTP.
- The admin console signed in against the local stack and loaded the dashboard, catalog, and users pages without console errors.
- Live Edge Function invocation remains unverified. Android connected tests and
  core live-device flows now pass; broader mutation/accessibility coverage
  remains unverified.

## Next

1. Complete live-stack coverage: invoke Edge Functions against the local stack; add Android runtime tests against the local API.
2. Extend Android runtime tests beyond the passed checker/Room suite and core smoke pass: add browse, detail, auth, social, offline, deep-link, RTL, and theme UI tests; complete favorites/votes/comments/request/contribution mutations, account deletion, session expiry, TalkBack, and font-scale checks.
3. Execute the defined GitHub Actions workflow (database, admin, Edge Functions, Android) on a runner; it has not been run remotely yet.
4. Produce a signed AAB and complete Play Console data-safety declarations, including deployed privacy and deletion URLs.
5. Conduct a security review of admin actions, rate limits, and secret handling before production.

## Later

- Advanced admin media upload workflow and evidence viewer.
- Offline detail for requests and account data.
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
