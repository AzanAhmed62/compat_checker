# Next Task

## Current verified state (2026-11-10)

- Database/RLS: **7 SQL test files passed** against a disposable PostgreSQL
  cluster on port `55432` after applying the test shim, all **12 ordered
  migrations**, and the updated `seed.sql`.
- Full local Supabase stack: **started and reset successfully** with the pinned
  `public.ecr.aws/supabase/postgres:17.11.0.011` image and the isolated ports
  in `supabase/config.toml`. Auth health and password sign-in returned 200;
  public views, RLS denials, collection/follow lifecycles, evidence URL
  validation, vote privacy, admin-only RPCs, and Storage object
  upload/read/delete policies by role were exercised over HTTP. Live Edge
  Function invocation remains unverified.
- Admin: TypeScript build/typecheck passed; **15 Vitest tests in 3 files**
  passed; Vite production build transformed 181 modules (498.34 kB JS,
  142.18 kB gzip; 16.84 kB CSS). Sign-in, dashboard, catalog, and users pages
  were exercised against the local stack in a browser with no console errors.
- Android: `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, and
  **17 JVM tests in 6 test classes** passed with no failures/errors/skips.
  Version `1.1.0` (version code 2). On 2026-11-10 the connected suite passed
  **5/5** on a OnePlus 6T (Android 11): Room 1-to-2 migration plus four checker
  Compose tests. Live smoke testing against the local API covered catalog (7
  products), product detail, password sign-in, collection create/add/visibility,
  follow/unfollow, leaderboard, notification routing, guide Markdown, offline
  guide cache, cold/warm deep links, light/dark themes, and Urdu/RTL. Remaining
  device gaps are listed under Priority 2.
- Edge Functions: Deno typecheck passed; **24 handler tests passed** with 0
  failures (2026-11-09).
- Public admin routes: `/privacy` and `/delete-account` are implemented and
  built. They require `VITE_DATA_CONTROLLER`, `VITE_SUPPORT_EMAIL`,
  deployment, and an external URL before Play declarations.
- CI: `.github/workflows/ci.yml` defines database, admin, Edge Function, and
  Android jobs; it has not run on GitHub Actions.
- No remote Supabase project, Play Console access, signing key, deployed
  privacy/deletion URL, or store assets are available. None were used.

## v1.1 delivered (committed locally; push pending)

- Social read models: `list_my_collections`, `get_collection`,
  `get_public_profile`, and standalone evidence URL validation in migration
  `20261021001200_social_read_models.sql`, with SQL coverage in
  `supabase/tests/70_social_read_models.sql`.
- Android collections (public/private, create/update/delete, add/remove
  product), follows, public profiles, reputation leaderboard, product-screen
  "add to collection", and profile links.
- Catalog brand filtering and refresh.
- Offline guide list/detail caching (Room schema version 2, explicit 1-to-2
  migration) and a safe Markdown subset renderer.
- Notification routing for product, guide, rule, profile, and collection links,
  with a visible message for unsupported/malformed links.
- Admin search-term sanitization and tests.
- Linux/macOS database runner `scripts/db_test.sh`; CI Android SDK
  platform/build-tools fix; local Supabase port profile documented in
  `supabase/config.toml`.

## Priority 1: Close remaining live-stack gaps

The local stack, Auth, PostgREST/RLS, social lifecycles, evidence validation,
vote privacy, Storage object policies, and admin console
sign-in/dashboard/catalog/users are verified. Do not repeat completed work;
extend the same local environment.

1. Invoke both Edge Functions against the local stack with real admin JWTs and
   database RPCs; confirm CORS, auth mapping, CSV import/export, purge
   authorization, and log redaction.
2. Exercise the remaining admin console workflows against the live stack:
   catalog create/update/publish, rules/evidence, rollback, reports,
   contributions, comments, requests, users/roles, guides, media, CSV,
   analytics, audit logs, pagination, and error states.
3. Extend Android device coverage beyond the completed catalog/detail/auth/
   collection/follow/leaderboard/notification/guide/offline/deep-link/theme
   smoke pass: favorites, votes, comments, requests, contributions, account
   deletion, and session expiry.
4. Keep exact results in `docs/TEST_PLAN.md` and `docs/KNOWN_ISSUES.md` current
   as the remaining device and admin flows are exercised.

Never connect to, reset, or mutate a remote Supabase project without explicit
owner authorization.

## Priority 2: Android runtime and accessibility

- The connected suite now passes **5/5** on a physical OnePlus 6T. Rerun it
  after Android changes:
  ```bash
  cd apps/android
  ./gradlew :app:connectedDebugAndroidTest
  ```
- Current instrumentation coverage is the checker result summary (unknown,
  compatible, incompatible, same-product) and the Room 1-to-2 migration. Add
  browse, detail, auth prompt, deep links, collections, profiles, offline
  fallback, RTL, and dark-theme tests.
- Verify favorites, votes, comments, requests, contributions, notifications,
  account-deletion request/cancel, and session expiry against the full stack.
  Follow/unfollow, leaderboard, notification routing, and core social screens
  were exercised on device on 2026-11-10.
- Complete TalkBack, switch access, 200% font-scale, and native-speaker Urdu
  review.
- Address remaining functional gaps: richer contribution forms (new rule,
  evidence, guide), media upload, and offline request/account data.

## Priority 3: Edge Functions and deployment

- Deploy to an authorized staging project and invoke both functions with real
  admin JWTs and database RPCs.
- Configure exact `ADMIN_ORIGIN` (comma-separated, no wildcard),
  `PURGE_CRON_SECRET`, and service-role secrets server-side.
- Schedule and monitor the purge worker.
- Re-run live-stack tests after any function or migration change.

## Priority 4: CI and release

1. Push the workflow and fix any runner-only failures; it has not executed
   remotely.
2. Deploy the admin site with `/privacy` and `/delete-account`, configure the
   data controller and support email, and verify SPA fallback and CORS.
3. Produce a signed AAB with an upload key stored outside the repository.
4. Complete data-safety/content-rating declarations and produce store listing
   assets, including the deployed privacy policy and account-deletion URLs.
5. Validate the release AAB, R8 mapping, deep links, and rollback plan in an
   authorized environment.

## Verification commands

Database (Linux/macOS; disposable PostgreSQL on `55432`):

```bash
PGPASSWORD=postgres PGUSER=supabase_admin PGPORT=55432 ./scripts/db_test.sh --keep
```

Windows:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/db_test.ps1 -Keep -HostName 127.0.0.1 -Port 55432
```

Admin:

```bash
cd apps/admin_panel
npm run typecheck
npm test
npm run build
```

Android (close Android Studio first on a low-memory machine):

```bash
cd apps/android
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --console=plain --no-configuration-cache --no-daemon --max-workers=1
```

Edge Functions (from repository root, with Deno installed):

```bash
deno check supabase/functions/_shared/auth.ts \
  supabase/functions/admin-csv/index.ts \
  supabase/functions/admin-csv/handler.ts \
  supabase/functions/purge-pending-accounts/index.ts \
  supabase/functions/purge-pending-accounts/handler.ts \
  supabase/functions/tests/auth_test.ts \
  supabase/functions/tests/admin_csv_test.ts \
  supabase/functions/tests/purge_test.ts
deno test --allow-env supabase/functions/tests
```

## Do not

- Do not connect to, reset, or mutate a remote Supabase project without explicit owner authorization.
- Do not run `seed.sql` or `supabase/tests/00_shim.sql` against production.
- Do not weaken RLS, expose vote rows, or infer compatibility without explicit published rules.
- Do not put a service-role key, signing password, or other secret in a client build or tracked file.
- Do not invent test counts, credentials, deployment status, store assets, or runtime results.
