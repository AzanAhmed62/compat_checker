# Roadmap

## Completed in this implementation

### Foundation and audit
- Repository audit and baseline preservation.
- Replaced demo-only assumptions with an evidence-backed platform specification.
- Established ordered migrations, deterministic synthetic seed, local SQL test runner, and documentation set.

### Backend
- Thirteen ordered migrations: core, identity, catalog, rules, community, reputation, social, analytics, admin, hardening, purge, social read models, atomic admin writes.
- RLS, explicit public views, server-side compatibility evaluation, moderation, contribution review, rule versions/rollback, CSV import, analytics, audit logs, media policy, account deletion/purge, collection/profile read models, evidence URL validation, and atomic admin saves.
- Eight SQL/security test files passing against disposable PostgreSQL, including transaction atomicity, snapshot fidelity, evidence ownership, canonical relationship orientation, and exact rollback evidence sets.

### Admin console
- Login/auth gate, role-aware navigation, dashboard, catalog CRUD, rules/evidence and rollback with required change reasons, reports, contributions, comments, requests, users/roles, guides, media, analytics, audit logs, CSV tools.
- Product, guide, and rule/evidence saves call atomic server RPCs; the client no longer composes multi-request writes or sends actor IDs.
- Public `/privacy` and `/delete-account` routes with configurable data-controller and support contact.
- Typecheck, 15 Vitest tests, and production build passing.

### Edge Functions
- `admin-csv` and `purge-pending-accounts` split into testable handlers with restrictive exact-origin CORS, auth/RPC error mapping, bounded CSV import, formula neutralization, and bounded service-role purge.
- Deno typecheck and 32 tests pass locally, including eight HTTP-level tests over ephemeral localhost servers with injected clients.

### Android
- Kotlin/Compose Material 3 app with manual DI, navigation, deep links, debug-only local HTTP exceptions, build-time configuration validation, English/Urdu resources, and RTL support.
- Data layer for Supabase Auth/PostgREST, Room offline cache (catalog and guides), DataStore preferences, and domain repository interfaces.
- Catalog/search with brand filters, product detail, checker, rules/evidence, guides with safe Markdown rendering, requests, auth, profile, favorites, collections, follows, public profiles, leaderboard, contributions, notifications, and settings screens.
- Contribution forms for product corrections, new products, new rules, evidence, and guides with validated payload builders and localized labels.
- `:app:assembleDebug` passes; 28 JVM unit tests pass in 7 classes. The v1.2 connected suite passed 5/5 on 2026-11-12 on a OnePlus A6010 (Android 11), including the Room 1-to-2 migration and four checker Compose tests. Live smoke testing against the local API covered core browse/auth/social/guide/offline/deep-link/theme/RTL flows; broader mutation and accessibility coverage remains.

### Local full-stack verification (2026-11-12)
- The pinned local Supabase stack was reset twice with all 13 migrations and the seed; the final state had 7 products, 5 rules, 1 guide, and 4 profiles. Auth health, password sign-in, PostgREST public views/RLS, social lifecycles, evidence URL validation, vote privacy, admin RPCs, and Storage object policies were exercised.
- The admin console's atomic catalog, guide, and rule/evidence saves, change reasons, version counts, and rollback, plus reports, contributions, comments, requests, users/roles/status and suspended-session denial, media, CSV export/import and rollback, analytics, audit filtering, pagination, and route gating were exercised in the browser without unexpected console errors.
- Edge Functions were invoked live in a local Edge Runtime with a real admin JWT and database, including CORS, CSV export, cron-secret enforcement, and account purge. Hosted Edge deployment remains unverified. Deployed legal/deletion pages, session-expiry refresh, and device mutation/accessibility coverage remain unverified.

## Next

1. Test the public `/privacy` and `/delete-account` routes against a deployed backend; add full admin session-expiry/refresh browser coverage and the remaining moderation validation combinations.
2. Extend Android runtime tests beyond the passed checker/Room suite and core smoke pass: add browse, detail, auth, social, offline, deep-link, RTL, and theme UI tests; complete favorites/votes/comments/request/contribution mutations, account deletion, session expiry, TalkBack, and font-scale checks.
3. Deploy both Edge Functions to an authorized staging project, configure `ADMIN_ORIGIN`, the purge secret, and the scheduler, then invoke them over hosted HTTP.
4. Run the defined GitHub Actions workflow on a runner for the v1.2 changes; the workflow succeeded at `58d30cc`, but the local v1.2 commit has not executed remotely.
5. Produce a signed AAB and complete Play Console data-safety declarations, including deployed privacy and deletion URLs.
6. Conduct a security review of admin actions, rate limits, and secret handling before production.

## Later

- Advanced admin media upload workflow and evidence viewer.
- Offline detail for requests and account data.
- Localization expansion beyond English and Urdu after review.
- Performance work for large catalogs and rule graphs.

## Release gates

A release is blocked until:

- All migrations and SQL tests pass in CI.
- Admin typecheck, tests, and build pass.
- Android debug build, unit tests, and instrumentation-APK compilation pass; instrumentation/runtime behavior for v1.2 and a release build with a real signing key are still required.
- Edge Functions have execution evidence.
- No service-role key or signing secret is present in tracked files or build artifacts.
- `docs/KNOWN_ISSUES.md` is reviewed and deployment-specific risks are closed or explicitly accepted.
