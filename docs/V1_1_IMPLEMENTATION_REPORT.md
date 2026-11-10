# Compat Checker v1.1 Implementation Report

- Report date: 2026-11-10
- Verification dates: 2026-11-09 (SQL, admin, Edge Functions, local stack) and
  2026-11-10 (Android device)
- Baseline commit: `da8286f` (`chore: publish verified release updates`)
- Branch: `main`
- Working tree: v1.1 changes were verified **uncommitted** on 2026-11-10, then
  committed locally on `main`; push to `origin/main` is pending GitHub
  authentication.

## 1. Executive summary

v1.1 adds social discovery and offline guide support to the existing
evidence-backed compatibility platform:

- Public/private collections, collection detail, add/remove product, visibility
  changes, and deletion.
- Follow/unfollow, public member profiles with aggregate counts, and a
  reputation leaderboard.
- Brand filtering in catalog search and a manual refresh action.
- Offline caching for guide lists and guide details (Room schema version 2 with
  an explicit 1-to-2 migration).
- A deliberately small, safe Markdown renderer for guide bodies.
- Notification routing for product, guide, rule, profile, and collection links,
  with a visible message for unsupported or malformed links.
- Server-side read models that keep collections/profiles behind RPCs instead of
  opening base tables, plus evidence URL validation for standalone evidence
  contributions.
- Admin search-term sanitization; Linux/macOS database test runner; CI Android
  SDK setup fix; independent local Supabase port profile.

The implementation compiles and its local test suites pass. The pinned local
Supabase stack was started and reset on 2026-11-09; Auth, PostgREST/RLS,
collection/follow lifecycles, evidence validation, vote privacy, and admin RPCs
were exercised over HTTP, and the admin console was smoke-tested against the
local API in a browser. Storage object upload/read/delete policies were
exercised by role. On 2026-11-10 the Android instrumentation suite passed
**5/5** on a physical OnePlus 6T and core live flows were smoke-tested against
the local stack. Live Edge Function invocation and the remaining Android gaps
listed in section 5 are still unverified.

## 2. Changes by area

### 2.1 Backend

New migration `supabase/migrations/20261021001200_social_read_models.sql`
(applied after the existing 11 migrations; no applied migration was modified):

- `public.list_my_collections()` - authenticated active user's collections with
  item counts and visibility; `SECURITY DEFINER`, `search_path = ''`.
- `public.get_collection(p_collection_id)` - returns a public collection or one
  owned by the caller. Private collections of other members return `null`;
  public detail only joins published products, active owners, brands, and
  categories. Anonymous and authenticated callers are explicitly granted.
- `public.get_public_profile(p_user_id)` - replaces the earlier profile RPC with
  the same explicit `profiles_public` projection plus aggregate follower and
  following counts, the caller's follow state, self flag, and approved
  contribution summaries.
- `private.validate_contribution_payload` - tightened standalone `evidence`
  contributions to require a real HTTP(S) source URL and reject example
  domains, matching `new_rule` evidence validation. The URL scheme check is
  case-insensitive.

Grants follow the existing hardening policy: `list_my_collections` is
authenticated-only; `get_collection`/`get_public_profile` are anon +
authenticated; PUBLIC execute is revoked where the functions are new.

New SQL coverage `supabase/tests/70_social_read_models.sql`:

- Anonymous public collection detail and item projection.
- Anonymous cannot call `list_my_collections`.
- Public profile follower/following/self state.
- Member unfollow/follow round-trip and follower counts.
- Owner collection index and item counts.
- Private collection invisible to anonymous callers and other members.
- Evidence contributions reject `example.com`, reject `javascript:`, and accept
  a valid HTTPS URL.

`supabase/tests/60_lifecycle_rollback.sql` was made deterministic: ranked
search no longer assumes the target product is the first result; the test
locates the product by UUID and asserts its media projection.

### 2.2 Android

- `SocialRepository` domain interface and `SocialRepositoryImpl` for the new
  RPCs, with DTOs/mappers for collection indexes, detail, public profile,
  follow result, and leaderboard.
- Social UI: `CollectionsScreen`, `CollectionDetailScreen`,
  `PublicProfileScreen`, `LeaderboardScreen`, `AddToCollectionDialog`, and
  `SocialViewModels`. Product detail exposes "Add to collection"; profile
  exposes collections and leaderboard links.
- Deep-link and notification routing: `compatchecker://collection/{id}` and
  `compatchecker://profile/{id}` route entries, matching intent filters, and a
  pure `NotificationRouter` used by the notifications screen.
- Catalog brand filter (`p_brand` parameter to `search_products`, Room cache
  filtering by brand slug) and refresh.
- Offline guides: Room entities/tables `cached_guides` and
  `cached_guide_details`, 30-day age cleanup, offline flags, and explicit
  Room migration 1 -> 2. `brandSlug` is added to cached products with a
  declarative default so the migration matches the exported schema.
- Markdown subset renderer: headings, paragraphs, bullet/ordered lists,
  quotes, fenced code, bold, italic, inline code, and absolute HTTP(S) links.
  Raw HTML is shown as text; non-HTTP links are not clickable.
- `risk_level` DTO mapping corrected from the previous `risk` name.
- Version bumped to `1.1.0` (version code 2).
- New JVM tests: `SocialMappingTest` (3), `MarkdownParserTest` (4),
  `NotificationRouterTest` (3). Existing tests were extended for brand slugs.

New strings were added in English and Urdu for collections, profiles, follows,
leaderboards, and unsupported notification links. Collection item counts use
plural resources, so a single item renders as "1 item" instead of "1 items".

### 2.3 Admin console

- Search input is sanitized before PostgREST filters: commas, `%`, parentheses,
  `*`, and backslashes are removed, whitespace is collapsed. Exported as
  `sanitizeSearchTerm` and covered by `src/lib/search.test.ts`.
- No new admin pages or privileged behavior. The admin console still holds only
  the public anon/publishable key.

### 2.4 Tooling, CI, and configuration

- `scripts/db_test.sh`: migrated to validated CLI flags, applies all migrations
  dynamically, runs all numbered tests, refuses non-`compat_*` databases, and
  optionally keeps the database. Made executable.
- `.github/workflows/ci.yml`: keep `android-actions/setup-android@v3` but pass
  `packages: "platform-tools"` and `accept-android-sdk-licenses: true`, avoiding
  the obsolete `tools` package that fails current SDK repositories.
- `supabase/config.toml`: independent local ports (`55321` API, `55322` DB,
  `55320` shadow, `55324` SMTP), Studio disabled, pooler disabled, storage
  vector disabled. This avoids collisions with other local Supabase projects.
- Documentation updated: README, AGENTS, NEXT_TASK, PRODUCT_SPEC, ARCHITECTURE,
  DATA_MODEL, SECURITY, TEST_PLAN, KNOWN_ISSUES, ROADMAP, DEPLOYMENT.

## 3. Verification results

All commands below are the exact commands run in this workspace. Results are
observed output, not projections.

### 3.1 Database and RLS

Command:

```bash
PGPASSWORD=postgres PGUSER=supabase_admin PGPORT=55432 ./scripts/db_test.sh --keep
```

Result on 2026-11-09: **7/7 SQL test files passed** after applying the test
shim, all **12 migrations**, and the updated `seed.sql`.

```
apply 20261021000100_core.sql ... 20261021001200_social_read_models.sql
apply seed.sql
test 10_schema.sql                  PASS
test 20_public_api.sql              PASS
test 30_member_rls.sql              PASS
test 40_moderation.sql              PASS
test 50_contributions.sql           PASS
test 60_lifecycle_rollback.sql      PASS
test 70_social_read_models.sql      PASS
All 7 SQL test files passed.
```

The final run used `--keep`; the disposable database and its transcript were
retained for inspection. An earlier run without `--keep` dropped the database
after success.

### 3.2 Admin console

Commands:

```bash
cd apps/admin_panel
npm run typecheck
npm test
npm run build
```

Result:

- Typecheck: exit 0.
- Vitest: **15 tests passed in 3 files** (`permissions` 3, `csv` 9, `search` 3).
- Production build: exit 0, **181 modules transformed**,
  `index-BSrQtqyO.js` **498.34 kB** (**142.18 kB gzip**), CSS **16.84 kB**
  (**4.28 kB gzip**).
- Live browser smoke test against `http://127.0.0.1:5173` and the local stack:
  administrator sign-in succeeded; dashboard reported 4 active users, 7
  published products, 4 published rules, 0 pending contributions, 0 open
  reports, and 1 open request; catalog listed 7 products; users page listed 4
  accounts; no console errors. Full CRUD/moderation flows were not exercised.

### 3.3 Android

Commands:

```bash
cd apps/android
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  ./gradlew --max-workers=1 :app:assembleDebug :app:testDebugUnitTest \
    :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest
```

Result (2026-11-10): `BUILD SUCCESSFUL`. The combined instrumentation run took
4m 42s; the final live-config `:app:assembleDebug` rerun took 29s.

- JVM tests: **17 passed in 6 classes**, 0 skipped, 0 failures, 0 errors:
  `CatalogRepositoryOfflineTest` 3, `CompatibilityMappingTest` 2,
  `SocialMappingTest` 3, `MarkdownParserTest` 4, `NotificationRouterTest` 3,
  `CorrectionPatchTest` 2.
- Connected tests on a physical OnePlus 6T (`ONEPLUS A6010`, Android 11,
  arm64-v8a, serial `7cdf8c61`): **5 passed, 0 failures, 0 errors, 0 skipped**
  in 6.054 s. Coverage: `CatalogDatabaseMigrationTest` (Room 1-to-2) and four
  `CheckerResultSummaryTest` Compose tests.
- Debug APK: `apps/android/app/build/outputs/apk/debug/app-debug.apk`,
  **26,657,229 bytes**; SHA-256
  `68e88f4790d10b9695d470324313025d884ea49deaf1104311be7cb5f56ce9d9`.
  BuildConfig verified: `SUPABASE_URL=http://127.0.0.1:55321` and a present
  153-character anon key.
- Badging: package `com.compatchecker.app.debug`, versionCode 2,
  versionName `1.1.0-debug`, minSdk 26, targetSdk 36.
- Signer: Android Debug certificate (`CN=Android Debug`), not a release key.
- Instrumentation test APK:
  `apps/android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`,
  **1,064,484 bytes**. It contains 5 tests and was executed on the device.

Live-device smoke results (same device, local stack via `adb reverse`):

- Anonymous catalog displayed **7 products**; product detail showed
  specifications, compatibility, and evidence.
- Password sign-in worked for `sample_member` and `sample_contributor`.
- "Device Smoke Collection" accepted a product and displayed **1 item**
  (pluralized correctly after the fix); public/private toggling verified.
  Anonymous `get_collection` returned `null` while private, then the public
  object with 1 item after restoring visibility.
- Follow/unfollow changed the follower count **1 -> 0 -> 1**; the leaderboard
  ordered `sample_member` (#1, 4 points) before `sample_contributor` (#2,
  3 points).
- Tapping a seeded notification opened the linked public profile and reduced
  unread notifications from 2 to 1.
- Guide detail rendered the Markdown heading, paragraphs, and 3 steps.
- With `adb reverse` removed, force-stop/relaunch, the guide list and detail
  loaded from Room cache with the "Showing saved results while offline" banner
  after about nine seconds.
- Warm deep links navigated the running app for product, checker (with
  `productId` preselected), guide, rule, collection, and profile. The earlier
  cold-start product deep link also worked.
- Light/dark themes and Urdu/RTL labels rendered; English was restored.
- Not exercised on device: favorites/votes/comments/request/contribution
  mutations, account-deletion request/cancel, full session-expiry handling,
  200% font scale, TalkBack/switch access, and native-speaker Urdu review.

### 3.4 Edge Functions

Commands:

```bash
deno check supabase/functions/_shared/auth.ts \
  supabase/functions/admin-csv/index.ts supabase/functions/admin-csv/handler.ts \
  supabase/functions/purge-pending-accounts/index.ts \
  supabase/functions/purge-pending-accounts/handler.ts \
  supabase/functions/tests/auth_test.ts \
  supabase/functions/tests/admin_csv_test.ts supabase/functions/tests/purge_test.ts
deno test --allow-env supabase/functions/tests
```

Result (2026-11-09): typecheck exit 0; **24 tests passed, 0 failed** (Deno
2.9.6). No Edge Function source changed in this pass.

### 3.5 Full local Supabase stack

Completed on 2026-11-09 with Supabase CLI 3.0.0, Docker 29.9.3, and the
repository's pinned `public.ecr.aws/supabase/postgres:17.11.0.011` image. The
isolated ports are API `55321`, DB `55322`, shadow `55320`, and SMTP `55324`;
Studio, Realtime, Edge Runtime, Logflare, Vector, Supavisor, and Postgres Meta
are disabled.

```bash
npx --yes supabase@3.0.0 start \
  -x realtime,studio,edge-runtime,logflare,vector,supavisor,postgres-meta \
  --ignore-health-check
npx --yes supabase@3.0.0 db reset
```

Observed results:

- `supabase start` and `supabase db reset` completed; all **12 migrations** and
  the updated `seed.sql` applied. The seed writes the empty GoTrue token
  columns and identity timestamps required by GoTrue v2.197; without them,
  password sign-in failed with a database schema scan error.
- Auth health `200`; password sign-in returned `200` for the admin, member, and
  contributor seed accounts.
- REST root `200`; `products_public`/`profiles_public` `200` (7 products, 4
  profiles); anonymous `collections`, `follows`, and `profiles` base tables
  `401`; anonymous `list_my_collections` `401`.
- `get_collection` returned the public seed collection with **3 items** and
  owner `sample_contributor`; `get_public_profile` returned aggregate follower
  state; `search_products` returned **7** results. A same-product check returned
  `known: false`; an explicit `not_compatible_with` pair returned the expected
  incompatibility.
- Collection lifecycle: private create, owner detail with note, anonymous and
  other-member detail `null`, base-table row hidden by RLS, visibility toggle
  made anonymous detail visible, delete returned `true`, post-delete detail
  `null`.
- Follow lifecycle: follower count `1 -> 0 -> 1`; anonymous callers saw no
  follow state; self-follow returned HTTP `400` with code `22023`.
- Evidence validation: `example.com` and `javascript:` sources returned HTTP
  `400` with code `22023`; an uppercase `HTTPS://docs.local/live-check` source
  was accepted; withdrawal returned `true`.
- Vote privacy/admin: anonymous `rule_votes` `401`; member `rule_votes` `200`
  with an empty own-vote result; member `admin_dashboard` `403`; admin
  `admin_dashboard` `200`; `admin_list_users` `200` with **4** users.
- Storage object policies: admin upload to `media` `200`; anonymous and member
  public reads `200`; anonymous and member uploads to `media` rejected with an
  RLS `403` body; admin upload to `evidence` `200`; anonymous and member reads
  of `evidence` `404`; admin evidence read `200`; admin deletes `200`. The test
  objects were removed and the bucket was empty afterwards. Bucket/list
  endpoints returned `200` empty, and a missing public object returned HTTP
  `400` with a `404`/`NoSuchKey` body.
- Admin UI: signed in at `http://127.0.0.1:5173`; dashboard counted 4 active
  users, 7 published products, and 4 published rules; catalog listed 7
  products; users page listed 4 accounts; no browser console errors.

The repository's pinned image version was **not** changed or bypassed. The
older `postgres:17.6.1.166` image remains in use only for the disposable SQL
test container, not as the v1.1 stack.

## 4. Security review

- Compatibility semantics are unchanged: only published explicit rules are
  used; `not_compatible_with` wins; unknown remains unknown. No inference,
  similarity, or client-side compatibility was added.
- New functions use `SECURITY DEFINER` with `SET search_path = ''`, schema-
  qualified identifiers, explicit grants, and no PUBLIC execute on the new
  authenticated-only function.
- Private collections are filtered server-side and return `null` for anonymous
  callers and other members; public collection detail only includes published
  products with active owners.
- Public profiles reuse the explicit-column `profiles_public` projection and add
  aggregates/follow state only. Email and private identifiers are not exposed.
- Follower rows remain private; only aggregate counts and the caller's own
  follow state are returned. `toggle_follow` rejects self-follows, verifies an
  active target, and applies a rate limit.
- Vote details remain private; aggregate-only projections were not changed.
- Admin search sanitization now also removes `*` and backslashes in addition to
  PostgREST filter separators.
- Client builds still receive only `VITE_SUPABASE_ANON_KEY` /
  `SUPABASE_ANON_KEY`. No service-role key or real credential is tracked.
  `apps/admin_panel/.env.local` exists but is gitignored and contains blank
  values. A staged-file check found no staged changes and a pattern scan found
  no tracked `sb_secret_`/`service_role`/JWT credentials outside test/docs
  text.
- The new Android debug APK is signed with the public Android Debug certificate
  and must not be published as a release artifact.

Residual risks and unverified runtime boundaries are recorded in
`docs/KNOWN_ISSUES.md` and `docs/SECURITY.md`.

## 5. Not run / not verified

- Admin console workflows beyond sign-in, dashboard, catalog, and users listing:
  catalog create/update/publish, rule/evidence editing and rollback, reports,
  contributions, comments, requests, guides, media, CSV, analytics, pagination
  edge cases, and error states.
- Android flows not run on the device: favorites/votes/comments/request/
  contribution mutations, account-deletion request/cancel, full session-expiry
  handling, 200% font scale, TalkBack/switch access, and native-speaker Urdu
  review. The instrumentation suite, Room 1-to-2 migration, offline guide
  cache, deep links, RTL, and light/dark themes were executed on the device;
  see section 3.3.
- Live Edge Function HTTP invocation, real JWT/database RPC, scheduler, and
  production CORS/secrets.
- GitHub Actions execution on a runner.
- Signed AAB, Play Console declarations, deployed privacy/deletion URLs, and
  store assets.
- Remote Supabase connection/reset was **not** attempted.

## 6. Repository state and next steps

- Branch `main`, baseline `da8286f`; v1.1 was committed locally on `main` on
  2026-11-10. Push to `origin/main` is pending GitHub authentication.
- The v1.1 commit contains **49 tracked files modified and 18 new files**
  (including the migration, SQL test, social UI, Markdown renderer, Room schema
  2, and tests).
- Generated build outputs are ignored by Git. The exported Room schema
  `2.json` is intentionally untracked-but-present and should be committed with
  the schema change. `supabase/.branches/` is local CLI state and is now
  gitignored.
- Recommended next steps:
  1. Commit the reviewed v1.1 diff (including the Room `2.json` schema) when the
     owner is ready.
  2. Complete the remaining local live-stack checks: Edge Function HTTP
     invocation and the rest of the admin workflows.
  3. Complete the remaining Android device checks (favorites/votes/comments/
     request/contribution mutations, account deletion, session expiry, 200%
     font scale, and TalkBack); the connected suite, Room migration, and core
     live smoke pass are complete.
  4. Execute the GitHub Actions workflow and verify the Android SDK setup fix.
  5. Deploy Edge Functions and the admin site with real secrets/configuration;
     produce a signed AAB only after those checks.

## 7. Conclusion

The v1.1 implementation is complete at the source level and is backed by
passing SQL, admin, Android unit-test, Edge handler, and connected Android
suites, plus live local Auth/PostgREST/RLS, Storage object-policy, admin, and
core Android device smoke checks. It is **not** release-ready: Edge Function
HTTP invocation, the remaining Android mutation/accessibility flows, signed
artifact production, and deployment remain unverified, and no production
credentials or deployment actions were used.
