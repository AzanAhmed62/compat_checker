# Test Plan

## Verified locally

These are exact, observed results from this workspace. Dates are stated per
section; the v1.2 source tests and the live local-stack, browser, and
connected-device checks were completed on 2026-11-12. They do not imply
production readiness.

### Database and RLS

Linux/macOS command (final v1.2 run used the local stack's isolated port and
superuser; adjust to your disposable server):

```bash
PGPASSWORD=postgres PGUSER=postgres PGPORT=55322 ./scripts/db_test.sh --keep
```

Windows command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/db_test.ps1 -Keep
```

Last verified result on 2026-11-12: **8 test files passed** against a
disposable PostgreSQL cluster on port `55322`, after applying the test shim,
all **13 ordered migrations**, and the updated `seed.sql`. All eight files
printed `PASS`. The final v1.2 run used `--keep`, so the disposable database
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
- `80_admin_transactions.sql` - atomic product/guide/rule saves, one-snapshot-per-update, published-evidence versioning, evidence-rule ownership, canonical symmetric orientation, and exact rollback evidence restoration.

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

Last verified result on 2026-11-12: typecheck passed; Vitest passed **15 tests
in 3 files**; the production build passed.

Live v1.2 browser check on 2026-11-12 against the reset local stack
(`http://127.0.0.1:5173`): the administrator signed in and `/rules` listed the
5 seed rules with no browser console errors. The new atomic save paths were
exercised through the UI:

- Created a draft rule with one evidence item through `admin_save_rule`
  (HTTP `200`): the rule had one version containing the final evidence set.
- Published the rule with a change reason: exactly one additional version,
  status published, and one evidence row.
- Restored version 2, then version 1: one new version per rollback, final
  status draft, one evidence row preserved, and four total versions.
- Created a draft product with two aliases through `admin_save_product`
  (HTTP `200`), then updated its summary and reduced the alias set to one:
  the full alias set was replaced correctly in the transaction.
- Created a draft guide with one ordered step through `admin_save_guide`
  (HTTP `200`), then updated its summary: the ordered step survived.

The same live browser sweep then exercised the remaining admin pages with
synthetic local-only rows. Database effects were checked directly with SQL;
the final `supabase db reset` removed every synthetic row.

- Reports: resolved a reported comment with `hide_comment`, resolved a product
  report with `retire_rule`, and dismissed a second product report with no
  content action. The rows ended `resolved`/`resolved`/`dismissed` with the
  entered notes; the reported comment was `hidden`; the reported draft rule was
  `retired`; the open report queue emptied.
- Contributions: approved a synthetic `new_product` contribution. It ended
  `approved` with `applied_entity_id` pointing at the new published product
  `browser-contribution-phone`; the pending queue emptied.
- Comments: pagination showed 27 visible comments over two pages at 25 per page.
  Body search and the Hidden filter returned the expected single rows. Hiding
  the seed reply and then restoring it produced `hidden -> visible` with the
  expected audit entries.
- Requests: moved the sample request `open -> planned -> fulfilled` with Aurora
  X1 selected as the fulfilling published product; the stored
  `fulfilled_product_id` matched.
- Users: changed the sample member `member -> contributor -> member` and
  `active -> suspended -> active`, each with a reason. A fresh sign-in while
  suspended was rejected; an existing member JWT received HTTP `403`
  `{"code":"42501","message":"Account is not active"}` from an authenticated
  RPC while the public profile projection remained readable. The role-change
  notification was delivered.
- Media: uploaded a 1x1 PNG, rejected then approved it, and deleted it. SQL
  showed `product_media` and `media_assets` empty and `storage.objects` empty
  for the media bucket after deletion. The product-media panel updated
  immediately; the registry panel showed the previous status until reload, a
  client-cache refresh timing observation rather than a database mismatch.
- CSV: the product export had a UTF-8 BOM and 9 lines (header plus 8 products at
  that point); the rules export had a BOM and 6 lines. A valid one-row import
  created `browser-csv-product` as a draft. A two-row import containing a
  duplicate slug returned HTTP `400` with `duplicate key value violates unique
  constraint "products_slug_key"` and rolled back the whole batch:
  `browser-csv-product` remained a draft and `browser-csv-rollback` was absent.
  The browser console recorded the expected failed-resource `400` for that
  deliberate negative test; no other console errors appeared.
- Analytics: the page loaded 4 events, 3 product views, 1 search, 0 checker
  runs, and 4 unique sessions, and switched to the 7-day window.
- Audit: filtering `contribution` + `contributions` returned one
  `contribution.approved` row. Its Before/after detail showed
  `status: pending -> approved`, the applied entity id, and the reviewer note.
- Access control: a contributor session showed
  `Administration access denied`; a trusted reviewer could open `/reports` but
  received `Administrator access required` on `/` and `/users`. A signed-in
  contributor remains on the login form until navigating to a protected route;
  this is a minor UX observation, not an authorization failure.
- Public legal routes: `/privacy` rendered with explicit
  "not configured for this deployment" notices because the local controller and
  support variables are empty. `/delete-account` supported member sign-in,
  request, pending state, and cancellation, with the profile verified as
  `deletion_pending` and then `active`.

After the final reset, the dashboard again reported 4 active users, 7 published
products, 4 published rules, 0 pending contributions, 0 open reports, 1 open
request, 0 hidden comments, 0 draft products, 1 draft rule, and 1 unread
notification. The seed database contained 0 reports, 0 contributions, 0 media
assets, 0 storage objects, 2 seed comments, 1 seed request, 14 audit entries,
and 4 analytics events. The administrator signed in successfully after the
reset.

Historical live browser check on 2026-11-09: the dashboard reported **4 active
users, 7 published products, 4 published rules, 0 pending contributions, 0 open
reports, and 1 open request**; the catalog listed **7 products**; the users page
listed **4 accounts**; no browser console errors were recorded. That smoke test
did not cover the v1.2 atomic mutations.

This sweep covers the admin CRUD/moderation pages and the negative CSV and
access-control paths, but not every field-validation or moderation combination.
The public `/privacy` and `/delete-account` routes have not been tested against
a deployed backend.

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

Last verified result on 2026-11-12:

- `:app:assembleDebug`, `:app:testDebugUnitTest`, and
  `:app:assembleDebugAndroidTest` complete successfully on the final v1.2
  source (version `1.2.0`, version code 3).
- **28 JVM tests passed** across 7 test classes with 0 failures, 0 errors, and
  0 skipped:
  `CatalogRepositoryOfflineTest` 3, `CompatibilityMappingTest` 2,
  `SocialMappingTest` 3, `MarkdownParserTest` 4, `NotificationRouterTest` 3,
  `CorrectionPatchTest` 2, `ContributionPayloadsTest` 11.
- Instrumentation test APK:
  `apps/android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
  It contains 5 tests: 4 Compose tests for unknown, compatible, incompatible,
  and same-product checker summaries, plus the Room 1-to-2 migration. All 5
  passed on a physical OnePlus A6010 on 2026-11-12 under v1.2 source.
- Room schema version 2 is exported to
  `apps/android/app/schemas/com.compatchecker.app.data.local.CompatDatabase/2.json`.
  The 1-to-2 migration adds the brand slug and offline guide tables.
  `CatalogDatabaseMigrationTest` opened a real version-1 database on the device
  and verified that cached products survive and the guide tables are added.

The debug APK used for live smoke testing was built with the local Supabase URL
and anon key. A default build without those values rejects configuration at
runtime. Live-device coverage and remaining gaps are listed below.

Covered unit tests:

- Catalog offline search/filter/cache-miss behavior, including brand filtering.
- Compatibility DTO mapping, including the corrected `risk_level` field,
  incompatibility, and unknown outcomes.
- Social DTO mapping for collection indexes, collection detail/media URLs, and
  public-profile follow state/counts.
- Markdown block/inline parsing and safe-link filtering (`http`/`https` only).
- Notification path parsing for product, guide, rule, profile, and collection
  destinations, plus malformed/unsafe-link rejection.
- Structured correction payload construction.
- Contribution payload builders for new products, new rules, evidence, and
  guides: slug normalization, alias/spec filtering, identifier validation,
  release-year bounds, required evidence, example-domain rejection, and ordered
  guide steps.

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
  supabase/functions/tests/purge_test.ts \
  supabase/functions/tests/http_test.ts
deno test --allow-env --allow-net supabase/functions/tests
```

Last verified result on 2026-11-12: typecheck passed; **32 tests passed, 0
failed** across four test files. In addition to the handler coverage, eight
HTTP-level tests start the handlers on ephemeral localhost ports and exercise
CORS preflight/origin rejection, method and JSON-body handling, error-to-CORS
mapping, RPC forwarding, CSV BOM/content, cron-secret enforcement, bounded
limits, and incomplete-environment responses. They use injected auth/database
fakes, not a live Supabase runtime.

Live local Edge Runtime invocation on 2026-11-12: the functions ran in
`public.ecr.aws/supabase/edge-runtime:v1.74.3` against the reset local stack.
The CLI's `functions serve` attempted to pull edge-runtime `v1.77.9` and
stalled, so the cached image was started directly with a minimal local router;
this was a validation workaround, not repository configuration. Real requests
produced:

- allowed-origin `OPTIONS /admin-csv` -> `204` with
  `access-control-allow-origin: http://127.0.0.1:5173`
- disallowed-origin `POST /admin-csv` -> `403` `{"error":"Origin not allowed"}`
- rules CSV export with a real admin JWT -> `200`, `text/csv; charset=utf-8`,
  UTF-8 BOM, and 6 lines (header plus 5 seed rules)
- purge without the cron secret -> `401` `{"error":"Invalid cron credential"}`
- purge with the cron secret -> `200` `{"ok":true,"purged":0}` on the clean
  seed; after creating an eligible account with
  `deletion_requested_at = now() - interval '31 days'`, the same call returned
  `200` with `purged: 1` and the matching id, and both the auth user and
  profile rows were gone

After the final reset, the export returned `200` with 6 lines and the purge
returned `200` with `purged: 0`.

Still not run: deployment to a hosted Supabase project, hosted scheduler
execution, and production function-secret configuration. These require an
authorized staging project.

### Full local Supabase stack

#### v1.2 live-stack reset (2026-11-12)

Verified with Supabase CLI 3.0.0, Docker 29.9.3, and the pinned
`public.ecr.aws/supabase/postgres:17.11.0.011` image on the isolated ports:
API `55321`, DB `55322`, shadow `55320`, and SMTP `55324`.

```bash
npx --yes supabase@3.0.0 db reset
```

Observed: the reset applied all **13 ordered migrations**, including
`20261021001300_admin_transactions.sql`, then `seed.sql`. Auth, PostgREST,
Storage, Kong, and the database were healthy. The reset was run twice: once to
apply v1.2 and again after browser testing. The final database contained
**7 products, 5 compatibility rules, 1 guide, and 4 profiles**, with no
synthetic browser rows.

The administrator signed in over the live Auth endpoint after the final reset
(HTTP `200`), and the Edge Function checks described above used the live
PostgREST/database state with a real admin JWT. The v1.2 browser save/version/
rollback flows are recorded in the Admin console section.

#### Historical v1.1 HTTP checks (2026-11-09)

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
  columns and identity timestamps required by GoTrue v2.197. This run predates
  the v1.2 migration 013 and is historical evidence only.
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

Historical run not covered: Realtime (disabled), live Edge Function HTTP
invocation, rate-limit saturation, and production configuration. The v1.2 run
on 2026-11-12 added the live local Edge invocation described above; hosted
deployment remains untested.

## Test strategy

### SQL/security

- Apply all migrations in filename order to an empty disposable database.
- Apply deterministic seed only in local/test.
- Test as anonymous, member, trusted, admin, suspended, banned, and deletion-pending roles using JWT claim setup.
- Assert both positive and negative access: unauthorized calls must fail, not merely return empty.
- Test vote privacy by verifying aggregates are readable while voter rows are not.
- Test incompatibility precedence with pairs containing positive and negative rules.
- Test idempotent favorites, one vote per user, request vote toggling, notification ownership, report uniqueness, contribution required fields, evidence URL rejection, rollback, CSV import, account purge, collection privacy, and public-profile aggregates.
- Test atomic admin saves end-to-end: a failing child write must leave the previous product/guide/rule state intact; rule updates must create exactly one final version containing the complete final evidence set; draft evidence edits must not create history; direct published-evidence edits must; evidence cannot be attached to a rule the admin does not own; symmetric relationships must be stored canonically; and rollback must restore the exact snapshot evidence set in a new version.
- Test public views never expose draft/private columns.

### Admin

- Unit-test pure helpers: CSV serialization/parsing, injection neutralization, permission gating, and PostgREST search sanitization.
- Typecheck all routes and API types.
- Production-build with only Vite public variables.
- Browser-check the public `/privacy` and `/delete-account` routes, including missing configuration, sign-in, pending state, and cancellation.
- Manual integration against the local stack:
  - verified on 2026-11-12 in the browser: admin sign-in; catalog create/update;
    guide create/update with an ordered step; rule/evidence create, publish,
    and rollback; version and evidence counts; reports; contributions; comments;
    requests; users/roles/status and suspended-session denial; media
    upload/status/delete; CSV export/import and rollback; analytics; audit
    filtering and before/after detail; pagination; expected negative CSV error
    state; contributor/trusted route gating; public privacy/deletion routes
  - verified on 2026-11-09 in the browser: dashboard counts, catalog listing,
    and users listing
  - verified at the RPC layer: member admin denial (`403`) and admin dashboard
    and user-list access
  - not run in the browser: public privacy/deletion routes against a deployed
    backend, full session-expiry/refresh handling, deployed CORS, and every
    field-validation or moderation combination

### Android

- Implemented JVM unit tests cover offline catalog search/filter/cache-miss behavior, brand filtering, compatibility DTO mapping (including incompatibility and unknown), social read-model mapping, Markdown parsing/link safety, notification route parsing, structured correction payload construction, and contribution payload builders for new products, new rules, evidence, and guides.
- Compose and Room instrumentation tests were executed on a physical OnePlus
  A6010 (`ONEPLUS A6010`, marketed as the OnePlus 6T, Android 11, arm64-v8a)
  on 2026-11-12 under v1.2 source: **5 tests, 0 failures, 0 errors, 0
  skipped**. The first run completed with `BUILD SUCCESSFUL in 2m 15s`; the
  final verification rerun completed with `BUILD SUCCESSFUL in 1m 47s`.
  Coverage: the Room 1-to-2 migration
  (`CatalogDatabaseMigrationTest`) and checker result summaries
  (`CheckerResultSummaryTest`: unknown, incompatible, compatible,
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

Covered by local tests: origin allow-list and wildcard rejection, missing/invalid auth, non-admin role rejection, malformed JSON, bounded CSV rows, CSV formula injection, partial export failure, service-role client use, purge secret comparison, bounded purge limit, and error redaction. Eight HTTP-level tests additionally exercise real requests against handlers bound to ephemeral localhost ports, including CORS preflight and origin rejection, method/body handling, error mapping, RPC forwarding, CSV BOM/content, and cron authorization. They use injected auth/database fakes.

The 2026-11-12 live local invocation additionally exercised the actual Edge
Runtime, real admin JWT, real PostgREST/database access, CSV export, cron-secret
enforcement, and an account purge that deleted the eligible auth/profile rows.

Still required in an authorized environment:

- Deploy both functions to a hosted project and invoke them over HTTP there.
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
