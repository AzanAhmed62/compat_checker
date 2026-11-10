# Test Plan

## Verified locally

These are exact, observed results from this workspace on 2026-11-09 unless a
different date is stated. They do not imply production readiness.

### Database and RLS

Linux/macOS command (disposable PostgreSQL container on port `55432`):

```bash
PGPASSWORD=postgres PGUSER=supabase_admin PGPORT=55432 ./scripts/db_test.sh --keep
```

Windows command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/db_test.ps1 -Keep
```

Last verified result on 2026-11-09: **7 test files passed** against a
disposable PostgreSQL cluster on port `55432`, after applying the test shim,
all **12 ordered migrations**, and the updated `seed.sql`. All seven files
printed `PASS`. The final run used `--keep`, so the disposable database
`compat_test` was retained for inspection; the runner printed the transcript
path.

Files:

- `10_schema.sql` - tables, enums, constraints, indexes, seed counts.
- `20_public_api.sql` - public views, search, product detail, guide detail, compatibility precedence.
- `30_member_rls.sql` - member isolation, favorites, comments, requests, votes, notifications.
- `40_moderation.sql` - report/moderation/role paths and audit.
- `50_contributions.sql` - contribution validation, review, points, withdrawal.
- `60_lifecycle_rollback.sql` - versions, rollback, CSV, media, account purge.
- `70_social_read_models.sql` - collection/profile read models, follower state, private-collection isolation, and evidence URL validation.

`00_shim.sql` is test-only and must never be applied to a production database.
Both runners refuse database names that do not begin with `compat_`.

### Admin console

Commands:

```bash
cd apps/admin_panel
npm run typecheck
npm test
npm run build
```

Last verified result on 2026-11-09: typecheck passed; Vitest passed **15 tests
in 3 files**; the production build passed with **181 modules transformed**
(498.34 kB JS, 142.18 kB gzip; 16.84 kB CSS).

Live browser check against the local stack (`http://127.0.0.1:5173`): the
administrator signed in, the dashboard reported **4 active users, 7 published
products, 4 published rules, 0 pending contributions, 0 open reports, and 1
open request**; the catalog listed **7 products**; the users page listed **4
accounts**; no browser console errors were recorded. This was a targeted
smoke test, not full admin CRUD/moderation coverage. The public `/privacy` and
`/delete-account` routes are included in the build but have no browser or
deployed-backend integration test.

Covered tests:

- CSV parsing/serialization and CSV injection neutralization.
- Permission/capability helpers.
- PostgREST search-term sanitization (`sanitizeSearchTerm`), including filter
  separators, wildcard/escape characters, and empty terms.

### Android

Commands (from `apps/android`, using the project wrapper and JDK 21):

```bash
cd apps/android
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  ./gradlew --max-workers=1 :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
```

Last verified result on 2026-11-09:

- `:app:assembleDebug`, `:app:testDebugUnitTest`, and
  `:app:assembleDebugAndroidTest` complete successfully on the final v1.1
  source (version `1.1.0`, version code 2).
- **17 JVM tests passed** across 6 test classes with 0 failures, 0 errors, and
  0 skipped:
  `CatalogRepositoryOfflineTest` 3, `CompatibilityMappingTest` 2,
  `SocialMappingTest` 3, `MarkdownParserTest` 4, `NotificationRouterTest` 3,
  `CorrectionPatchTest` 2.
- Instrumentation test APK:
  `apps/android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
  It contains 5 tests: 4 Compose tests for unknown, compatible, incompatible,
  and same-product checker summaries, plus the Room 1-to-2 migration. All 5
  passed on a physical OnePlus 6T on 2026-11-10.
- Room schema version 2 is exported to
  `apps/android/app/schemas/com.compatchecker.app.data.local.CompatDatabase/2.json`.
  The 1-to-2 migration adds the brand slug and offline guide tables.
  `CatalogDatabaseMigrationTest` opened a real version-1 database on the device
  and verified that cached products survive and the guide tables are added.

The debug APK used for live smoke testing was built with the local Supabase URL
and anon key. A default build without those values rejects configuration at
runtime. Live-device coverage and remaining gaps are listed below.

Covered v1.1 unit tests:

- Catalog offline search/filter/cache-miss behavior, including brand filtering.
- Compatibility DTO mapping, including the corrected `risk_level` field,
  incompatibility, and unknown outcomes.
- Social DTO mapping for collection indexes, collection detail/media URLs, and
  public-profile follow state/counts.
- Markdown block/inline parsing and safe-link filtering (`http`/`https` only).
- Notification path parsing for product, guide, rule, profile, and collection
  destinations, plus malformed/unsafe-link rejection.
- Structured correction payload construction.

### Edge Functions

Commands (from the repository root, with Deno 2.9.6):

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

Last verified result on 2026-11-09: typecheck passed; **24 tests passed, 0
failed** across three test files. Coverage includes exact-origin CORS and
wildcard rejection, auth failure mapping, malformed JSON, CSV row limits,
formula injection neutralization, export failure handling, purge secret
comparison, bounded limits, service-role-only client creation, and error
redaction.

Not run: deployment to a hosted Supabase project, live function HTTP
invocation, real admin JWT/database RPC behavior, and scheduler execution.
These require an Edge Runtime-enabled local stack or an authorized staging
project.

### Full local Supabase stack

Verified on 2026-11-09 with Supabase CLI 3.0.0, Docker 29.9.3, and the pinned
`public.ecr.aws/supabase/postgres:17.11.0.011` image. The isolated local ports
are API `55321`, DB `55322`, shadow `55320`, and SMTP `55324`; Studio, Realtime,
Edge Runtime, Logflare, Vector, Supavisor, and Postgres Meta are disabled.

```bash
npx --yes supabase@3.0.0 start \
  -x realtime,studio,edge-runtime,logflare,vector,supavisor,postgres-meta \
  --ignore-health-check
npx --yes supabase@3.0.0 db reset
```

Observed results:

- `supabase start` and `supabase db reset` completed; all **12 migrations** and
  `seed.sql` applied. The seed was updated to write the empty GoTrue token
  columns and identity timestamps required by GoTrue v2.197.
- Auth health `200`; password sign-in returned `200` for the admin, member, and
  contributor seed accounts.
- REST root `200`; `products_public` and `profiles_public` returned `200`
  (7 products / 4 profiles); `collections`, `follows`, and `profiles` base
  tables returned `401` to anonymous callers; `list_my_collections` returned
  `401` anonymously.
- `get_collection` returned a public collection with **3 items** and the owner
  username; `get_public_profile` returned aggregate follower state;
  `search_products` returned **7** results. A same-product check returned
  `known: false`; an explicit `not_compatible_with` pair returned the expected
  incompatibility.
- Collection lifecycle: private create, owner detail with note, anonymous and
  other-member detail `null`, base-table row hidden by RLS, visibility toggle
  made anonymous detail visible, delete returned `true`, post-delete detail
  `null`.
- Follow lifecycle: follower count `1 -> 0 -> 1`; anonymous callers saw no
  follow state; self-follow returned HTTP `400` with code `22023`.
- Evidence validation: `example.com` and `javascript:` sources returned HTTP
  `400` with code `22023`; an uppercase `HTTPS://` source was accepted;
  withdrawal returned `true`.
- Vote privacy/admin: anonymous `rule_votes` returned `401`; a member received
  `200` with an empty own-vote result; member `admin_dashboard` returned `403`;
  admin `admin_dashboard` returned `200`; `admin_list_users` returned **4**
  users.
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

Not covered: Realtime (disabled), live Edge Function HTTP invocation,
rate-limit saturation, and production configuration.

## Test strategy

### SQL/security

- Apply all migrations in filename order to an empty disposable database.
- Apply deterministic seed only in local/test.
- Test as anonymous, member, trusted, admin, suspended, banned, and deletion-pending roles using JWT claim setup.
- Assert both positive and negative access: unauthorized calls must fail, not merely return empty.
- Test vote privacy by verifying aggregates are readable while voter rows are not.
- Test incompatibility precedence with pairs containing positive and negative rules.
- Test idempotent favorites, one vote per user, request vote toggling, notification ownership, report uniqueness, contribution required fields, evidence URL rejection, rollback, CSV import, account purge, collection privacy, and public-profile aggregates.
- Test public views never expose draft/private columns.

### Admin

- Unit-test pure helpers: CSV serialization/parsing, injection neutralization, permission gating, and PostgREST search sanitization.
- Typecheck all routes and API types.
- Production-build with only Vite public variables.
- Browser-check the public `/privacy` and `/delete-account` routes, including missing configuration, sign-in, pending state, and cancellation.
- Manual integration against the local stack (partially run on 2026-11-09):
  - verified in the browser: admin sign-in, dashboard counts, catalog listing,
    users listing, and zero console errors
  - verified at the RPC layer: member admin denial (`403`) and admin dashboard
    and user-list access
  - not run in the browser: catalog create/update/publish, rule/evidence
    creation and rollback, report/comment/request moderation, user role/status
    changes and forced re-auth, CSV upload/download, guides/media, analytics,
    audit-log filtering, pagination edge cases, and error states

### Android

- Implemented JVM unit tests cover offline catalog search/filter/cache-miss behavior, brand filtering, compatibility DTO mapping (including incompatibility and unknown), social read-model mapping, Markdown parsing/link safety, notification route parsing, and structured correction payload construction.
- Compose and Room instrumentation tests were executed on a physical OnePlus
  6T (`ONEPLUS A6010`, Android 11, arm64-v8a) on 2026-11-10: **5 tests, 0
  failures, 0 errors, 0 skipped**, 6.054 s in the final rerun. Coverage: the
  Room 1-to-2 migration (`CatalogDatabaseMigrationTest`) and checker result
  summaries (`CheckerResultSummaryTest`: unknown, incompatible, compatible,
  same-product).
- Live smoke testing against the local Supabase stack on the same device
  verified:
  - anonymous catalog load (**7 products**), product detail with
    specifications/compatibility/evidence, and popular guides
  - password sign-in as `sample_member` and `sample_contributor`
  - collection list/create, add-to-collection (item count updated to 1), and
    public/private toggling; anonymous `get_collection` returned `null` while
    private and the public object with 1 item after restoring visibility
  - follow/unfollow with follower count **1 -> 0 -> 1**
  - leaderboard ordering (`sample_member` #1 at 4 points, `sample_contributor`
    #2 at 3 points)
  - notification routing to the linked public profile and read-state update
    (2 unread -> 1 unread)
  - guide detail with Markdown heading/paragraphs and 3 steps
  - offline guide list and detail from Room cache with the "Showing saved
    results while offline" banner after removing `adb reverse`
  - light/dark theme switching and Urdu/RTL labels, then restored English
- Warm deep links were delivered to the running activity and navigated
  correctly for product, checker (`productId` preselected), guide, rule,
  collection, and profile. A cold-start product deep link was also verified.
- Still not run on the device: favorites/votes/comments/request/contribution
  mutations, account-deletion request/cancel, full session-expiry handling,
  200% font scale, TalkBack/switch access, and native-speaker Urdu review.

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
| Guides | empty list, detail, steps, missing images, deep link, offline cache, Markdown headings/lists/links |
| Collections | create, rename/visibility, add/remove product, delete, private-collection isolation, public detail |
| Profiles | public profile, follow/unfollow, leaderboard ranking, aggregate-only counts |
| Requests | anonymous public list, submit signed out/in, vote toggle, aggregate count |
| Profile | me, reputation, favorites, collections, contributions, notifications, deletion request/cancel |
| Notifications | product, guide, rule, profile, collection links; unsupported link message |
| Admin | role-denied route, each CRUD page, pagination, error banner, rollback |
| Public pages | privacy with configured/missing controller, deletion sign-in, request, pending, cancel |
| UX | light/dark, English/Urdu RTL, font scale 200%, TalkBack, small screen, offline |
| Security | no key in APK/strings/logs; protected tables unreachable via anon; draft data absent |
