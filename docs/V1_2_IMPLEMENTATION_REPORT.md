# v1.2 Implementation Report

Date: 2026-11-12 (updated 2026-11-13)

Status: local source, live-stack, browser, and substantial device validation
complete; committed locally; push blocked by missing GitHub credentials; not
deployed or release-validated.

## Scope

The v1.2 directive targeted data integrity, Android contribution workflows,
admin reliability, backend security, accessibility/localization, Edge HTTP
coverage, CI, and truthful release documentation. Work remained local. No
remote Supabase project, production credential, signing key, Play Console
account, or deployment target was used.

## Delivered

### Atomic admin writes and rule history

`supabase/migrations/20261021001300_admin_transactions.sql` adds:

- `admin_save_product(uuid, jsonb, jsonb, jsonb)` — validates the acting admin,
  updates the product and replaces aliases/specifications in one transaction.
- `admin_save_guide(uuid, jsonb, jsonb)` — replaces guide fields and ordered
  steps in one transaction.
- `admin_save_rule(uuid, jsonb, jsonb, text)` — replaces rule fields and the
  complete evidence set in one transaction, requires a change reason on update,
  verifies that evidence updates belong to the target rule, and canonicalizes
  symmetric relationships.
- Snapshot suppression during staged evidence writes, followed by exactly one
  final version containing the final evidence set.
- A trigger that leaves a version trail for direct evidence edits on published
  rules while deliberately not versioning draft evidence edits.
- An immutability trigger that prevents moving evidence between rules.
- Corrected `admin_rollback_rule(uuid, integer, text)` that restores the exact
  evidence set recorded in the target snapshot (matched by stable source URL)
  and writes a new version instead of mutating older reasons.
- Explicit revocation from `public`/`anon` and execution grants to
  `authenticated`; authorization remains server-side.

`supabase/tests/80_admin_transactions.sql` covers successful saves, failing
child writes leaving prior state intact, one-snapshot-per-update behavior,
direct published-evidence versioning, draft evidence not versioned, evidence
cannot be moved between rules, canonical symmetric pairs, and exact rollback
evidence restoration.

### Admin console

- `apps/admin_panel/src/lib/api.ts` calls the atomic RPCs for catalog, guide,
  and rule/evidence saves. Client-supplied actor IDs were removed.
- `RulesPage.tsx` requires and sends a change reason for rule updates.
- Catalog and guide save paths no longer compose multiple independent
  PostgREST requests.

### Android contributions and localization

- New `ContributionPayloads.kt` builders for `new_product`, `new_rule`,
  `evidence`, and `guide`, with identifier, URL, length, range, duplicate, and
  required-evidence validation.
- New `ContributionDialogs.kt` forms, wired through profile, catalog, and rule
  screens. The rule screen exposes an evidence-suggestion action.
- Repository/ViewModel changes route contributions through the existing
  `submit_contribution` backend contract.
- English and Urdu resources for contribution fields, statuses, roles,
  relationship types, verification states, difficulty, evidence types, vote
  state, connector/form factor, and guide step positions.
- `Format.kt` labels are localized and dates use the device locale.
- `ContributionPayloadsTest.kt` adds 11 JVM tests.

### Edge HTTP coverage

- `supabase/functions/tests/http_test.ts` runs eight tests against handlers
  bound to ephemeral localhost ports with injected auth/database fakes:
  - CORS preflight and allowed-origin reflection
  - disallowed-origin rejection
  - method and malformed-JSON handling
  - auth-error-to-CORS mapping
  - RPC forwarding for CSV import
  - CSV content type and UTF-8 BOM
  - cron-secret enforcement and bounded purge
  - incomplete-environment response
- `.github/workflows/ci.yml` includes the new file in typechecking and runs the
  suite with `--allow-net`.

### Documentation and version

- `docs/ARCHITECTURE.md`, `docs/SECURITY.md`, `docs/DATA_MODEL.md`,
  `docs/TEST_PLAN.md`, `docs/DEPLOYMENT.md`, `docs/KNOWN_ISSUES.md`,
  `docs/ROADMAP.md`, `README.md`, and `NEXT_TASK.md` now describe the v1.2
  behavior, counts, and gaps.
- Android is `1.2.0` (version code 3). The admin package version remains
  `1.0.0`.

## Verification results

All commands were run from the repository root unless a working directory is
shown.

### Database

```bash
PGPASSWORD=postgres PGUSER=postgres PGPORT=55322 ./scripts/db_test.sh --keep
```

Observed: all 13 migrations applied, seed applied, and **all 8 SQL test files
passed**:

```text
10_schema.sql PASS
20_public_api.sql PASS
30_member_rls.sql PASS
40_moderation.sql PASS
50_contributions.sql PASS
60_lifecycle_rollback.sql PASS
70_social_read_models.sql PASS
80_admin_transactions.sql PASS
All 8 SQL test files passed.
Database 'compat_test' kept for inspection.
Full transcript: /tmp/compat-db-test-j1BXLQ
```

The actual local Supabase stack was reset with
`npx --yes supabase@3.0.0 db reset` twice on 2026-11-12; all **13 migrations**
and `seed.sql` applied. The final reset left a clean database with **7
products, 5 rules, 1 guide, and 4 profiles**. The second reset removed the
synthetic browser rows created during the admin workflow checks.

### Admin console

```bash
cd apps/admin_panel
npm run typecheck
npm test -- --run
npm run build
```

Observed:

- Typecheck passed on the final verification sweep.
- Vitest: **3 files passed, 15 tests passed, 0 failed** (1.53 s).
- Production build: **181 modules transformed**; JS `496.73 kB`
  (`141.71 kB` gzip), CSS `16.84 kB` (`4.28 kB` gzip), HTML `0.56 kB`.

### Edge Functions

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

Observed: typecheck passed; **32 tests passed, 0 failed** (1 s) across:

- `admin_csv_test.ts` — 11
- `auth_test.ts` — 5
- `http_test.ts` — 8
- `purge_test.ts` — 8

The HTTP tests use real localhost requests but injected clients, not a live
Supabase runtime.

Live local invocation on 2026-11-12 used the actual
`public.ecr.aws/supabase/edge-runtime:v1.74.3` container against the reset
local stack on port `55325`, with the real admin JWT and real PostgREST/database
access. Observed results:

- Allowed-origin `OPTIONS /admin-csv` returned **204** with the expected
  `Access-Control-Allow-Origin`.
- A disallowed-origin POST returned **403** with `Origin not allowed`.
- Rules CSV export with the admin JWT returned **200**, a UTF-8 BOM, and **6
  lines** (header plus 5 rules).
- Purge without the cron secret returned **401**; with the secret it returned
  **200**.
- A synthetic 31-day-pending account was created, then purged with
  `purged: 1`; its auth and profile rows were deleted.
- After the final reset, export returned **200 / 6 lines** and purge returned
  **200 / `purged: 0`**.

The Supabase CLI `functions serve` path stalled pulling edge-runtime `v1.77.9`,
so the cached `v1.74.3` image was run directly with a temporary local router.
The functions themselves were the repository sources. Hosted Edge deployment
was not tested.

### Admin browser workflows

Against the same reset local stack, with the admin UI served at
`http://127.0.0.1:5173`, the v1.2 atomic flows completed with **zero browser
console errors**:

- Created a draft rule with evidence: **1 version**.
- Published it with a change reason: exactly **2 versions**.
- Rolled back to v2, then v1: **4 total versions**, final state draft, evidence
  preserved.
- Created a draft product with **2 aliases**, updated its summary, and left
  **1 alias**.
- Created a draft guide with **1 ordered step**, updated its summary, and the
  step was preserved.

The same sweep then covered the remaining admin pages and negative paths:

- Reports resolved with `hide_comment` and `retire_rule`, plus a dismissal with
  no content action; all three rows, the hidden comment, and the retired rule
  were verified in SQL.
- A synthetic `new_product` contribution was approved atomically and its
  `applied_entity_id` pointed at the published product.
- Comment pagination, body search, Hidden filtering, hide, and restore were
  exercised; 27 visible comments were split across two pages at 25 per page.
- The sample request moved `open -> planned -> fulfilled` with a published
  product selected and persisted.
- User role changes (`member -> contributor -> member`) and status changes
  (`active -> suspended -> active`) were applied with reasons. A fresh sign-in
  while suspended was rejected, an existing member JWT received HTTP `403`
  `{"code":"42501","message":"Account is not active"}` from an authenticated
  RPC, and the public profile projection remained readable.
- A 1x1 PNG was uploaded, rejected, approved, and deleted. SQL verified empty
  `product_media`, `media_assets`, and media `storage.objects` after deletion.
  The registry panel needed a reload to show status changes during rapid
  automation; the database was correct.
- The products export had a UTF-8 BOM and 9 lines; the rules export had a BOM
  and 6 lines. A valid one-row import created a draft; a duplicate-slug batch
  returned the expected HTTP `400` and rolled back the second row.
- Analytics loaded 4 events, 3 product views, 1 search, 0 checker runs, and 4
  sessions. Audit filtering returned the `contribution.approved` entry with its
  Before/after JSON and reviewer note.
- A contributor session showed `Administration access denied`; a trusted
  reviewer could open `/reports` but not `/` or `/users`. `/privacy` rendered
  with explicit unconfigured-controller notices, and `/delete-account` completed
  a member request, pending state, and cancellation.

All synthetic rows were removed by the final `supabase db reset`. After the
reset, the dashboard again reported 4 active users, 7 published products, 4
published rules, 0 pending contributions, 0 open reports, 1 open request, and 1
unread notification. The seed database contained 0 reports, 0 contributions, 0
media assets, and 0 storage objects.

### Android

```bash
cd apps/android
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  ./gradlew :app:assembleDebug :app:testDebugUnitTest \
  :app:assembleDebugAndroidTest \
  --no-daemon --no-configuration-cache --max-workers=1 --console=plain
```

Observed: `BUILD SUCCESSFUL in 2m 23s` (78 actionable tasks, 20 executed).

- `:app:assembleDebug` passed; debug APK `app-debug.apk` is 25 MB and reports
  `versionName="1.2.0-debug"`.
- `:app:testDebugUnitTest` passed with **28 tests across 7 classes**, 0
  failures, 0 errors, 0 skipped:
  - `CatalogRepositoryOfflineTest` — 3
  - `CompatibilityMappingTest` — 2
  - `SocialMappingTest` — 3
  - `NotificationRouterTest` — 3
  - `MarkdownParserTest` — 4
  - `CorrectionPatchTest` — 2
  - `ContributionPayloadsTest` — 11
- `:app:assembleDebugAndroidTest` passed and produced the instrumentation APK.

The connected v1.2 suite was then run on a physical OnePlus A6010 (`7cdf8c61`,
Android 11, arm64-v8a) against the local stack:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew \
  :app:connectedDebugAndroidTest --no-daemon --no-configuration-cache \
  --max-workers=1 --console=plain
```

Observed: `BUILD SUCCESSFUL in 2m 15s` on the first run; the final verification
rerun completed in `1m 47s` with the build/test tasks up to date. The connected
result was **5 tests, 0 failures, 0 errors, 0 skipped** (7.131 s of device test
time). Coverage was the Room 1-to-2 migration and the four checker result
summaries (unknown, incompatible, compatible, same-product).

A live mutation pass against the local stack followed on 2026-11-12/13 on the
same device. The debug APK was rebuilt with the local Supabase URL and anon key
and installed over `adb reverse tcp:55321 tcp:55321`. Observed results:

- Signed-in `sample_contributor` submitted `new_product`, `new_rule` (one
  citation), `guide` (one step), and standalone `evidence` contributions. All
  four appeared as `Pending`; SQL confirmed the rows. The `new_product`
  contribution did not create a product while pending.
- Rule confirm vote produced **1 confirm** with a matching `rule_votes` row;
  favorite produced `Saved` with a `favorites` row; the product comment was
  stored with status `visible`. The discussion header still showed
  `Discussion (2)` after the third comment until a reload.
- A product request was created with status `open` and one vote was cast
  (**1 vote**, matching `product_requests`/`request_votes` rows).
- Account deletion request changed the account to `Deletion pending` with SQL
  `account_status = deletion_pending` and `deletion_requested_at` set.
  Cancellation restored `Account: Active`, cleared the timestamp, and showed a
  confirmation message.
- Forcing an expired stored access token with a valid refresh token caused an
  automatic refresh: a new access token was stored and the session stayed
  signed in. Revoking the server-side Auth session and forcing expiry cleared
  the stored session and returned Profile to the signed-out state.
- A 200% font-scale smoke test kept browse, product detail, and comment entry
  usable, but bottom-navigation labels wrapped and clipped
  (`Brows e`, `Check er`, `Guide s`, `Reque sts`) and discussion author metadata
  wrapped every few characters. This is recorded as an accessibility defect.
- A repeat Urdu/RTL pass set `system_locales` to `ur-PK,en-US` and rebooted the
  device, but ADB disconnected before the app could be checked. The pass is not
  verified and the device was left with the Urdu locale setting. TalkBack is
  installed but was not exercised.

## Not run / blockers

- **No hosted Edge Function validation.** Hosted deployment, scheduler
  credentials, production function secrets, deployed `ADMIN_ORIGIN`, and hosted
  logs remain untested. Local invocation used the cached edge-runtime
  `v1.74.3`, not the CLI-selected `v1.77.9`.
- **No release validation.** There is no signing key, Play Console access,
  deployed privacy/deletion URL, production credential, or deployment
  authorization. No signed AAB exists.
- **Remote CI unavailable for v1.2.** The workflow succeeded at `58d30cc`; the
  2026-11-13 push attempt failed because no GitHub HTTPS/SSH credentials are
  configured, so GitHub Actions has not seen the v1.2 commits.
- **Remaining browser coverage.** The public `/privacy` and `/delete-account`
  routes have not been tested against a deployed backend, full
  session-expiry/refresh handling is not browser-tested, and not every
  field-validation or moderation combination was exercised. A signed-in
  contributor remains on the login form until navigating to a protected route;
  this is a UX observation, not an access-control bypass.
- **Remaining device coverage.** TalkBack/switch-access validation,
  native-speaker Urdu review, and a completed Urdu/RTL layout pass remain
  outstanding. The staged Urdu pass was interrupted after reboot and the device
  was left with `ur-PK` configured. The 200% font-scale pass found clipped
  bottom-navigation labels and narrow discussion metadata. The local database
  was not reset after the mutation pass, so synthetic Android rows remain.
- **Disk headroom is low.** About 3.1 GB was free (98% used) at verification
  time; Gradle was run with `--no-daemon --no-configuration-cache
  --max-workers=1`.

## Release readiness

Local validation is coherent: all 13 migrations apply, all 8 SQL test files,
admin typecheck/test/build, Edge typecheck/32 tests, live local Edge HTTP
invocation, the full admin browser sweep, Android build/JVM tests, the v1.2
connected 5/5 device suite, and the live Android mutation pass all pass. This is
**not** a release validation. Release remains blocked by the missing signed
AAB, store declarations, deployed legal/deletion URLs, hosted Edge deployment
and scheduler configuration, production credentials, and authorized remote
deployment. Browser gaps are limited to deployed legal/deletion integration,
session-expiry refresh, and additional validation combinations. Device gaps
remain for TalkBack/switch access, native-speaker Urdu review, a completed
Urdu/RTL pass, and the 200% font-scale layout defect.

## Git state

- Branch: `main`
- Previous HEAD: `58d30cc` (`feat: ship v1.1 social and offline guide features`)
- v1.2 changes: committed as `7f3434c` on top of `58d30cc`, followed by a
  documentation commit. Both remain local: `git push origin main` on 2026-11-13
  failed with `could not read Username for 'https://github.com'` because no
  HTTPS/SSH credentials are configured. The code commit includes the new
  migration, SQL test, Edge HTTP test, two Android source files, one Android
  test file, and this report. Generated build output is ignored.
- Final verification sweep on 2026-11-12: **8/8 SQL test files**, **15/15 admin
  Vitest tests** plus typecheck/build, **32/32 Edge tests** plus typecheck, and
  **28/28 Android JVM tests** plus **5/5 connected tests**; `git diff --check`
  clean.
- Secret/artifact review found no tracked or untracked signing key, `.env`,
  `local.properties`, service-role secret, or private key. Test-only fake
  credentials are confined to test code. No NUL-byte file corruption was found
  in the documentation after the earlier interrupted-write check.
- The 2026-11-13 push attempt failed before contacting the remote
  (`fatal: could not read Username for 'https://github.com': No such device or
  address`). No deployment, remote Supabase mutation, or release action was
  performed. GitHub Actions has not run the v1.2 commits.
