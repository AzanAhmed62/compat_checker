# Next Task

## Current verified state (2026-11-12)

- Database/RLS: **8 SQL test files passed** against a disposable PostgreSQL
  database on port `55322` (`PGUSER=postgres`) after applying the test shim,
  all **13 ordered migrations**, and the updated `seed.sql`.
- Live local stack: reset twice with the pinned
  `public.ecr.aws/supabase/postgres:17.11.0.011` image; all 13 migrations and
  `seed.sql` applied. The final reset left **7 products, 5 rules, 1 guide, and
  4 profiles**.
- Admin: TypeScript typecheck passed; **15 Vitest tests in 3 files** passed; the
  Vite production build passed. The 2026-11-12 browser sweep covered atomic
  product/guide/rule saves, change reasons, version counts, rollback evidence
  fidelity, reports, contributions, comments, requests, users/roles/status and
  suspended-session denial, media, CSV export/import and rollback, analytics,
  audit filtering, pagination, the expected duplicate-slug error, and
  contributor/trusted route gating. No unexpected console errors were recorded.
- Android: `:app:assembleDebug`, `:app:testDebugUnitTest`, and
  `:app:assembleDebugAndroidTest` passed; **28 JVM tests in 7 test classes**
  passed with no failures/errors/skips. The v1.2 connected suite passed **5/5**
  on 2026-11-12 on a OnePlus A6010 (Android 11). Version is `1.2.0` (version
  code 3).
- Edge Functions: Deno typecheck passed; **32 tests passed** with 0 failures,
  including eight HTTP-level tests that bind handlers to ephemeral localhost
  ports with injected auth/database fakes. Live local invocation on 2026-11-12
  used the real Edge Runtime, admin JWT, and database: CORS preflight/origin
  rejection, CSV export, cron-secret enforcement, and an eligible account purge
  all behaved as expected.
- CI: `.github/workflows/ci.yml` defines database, admin, Edge Function, and
  Android jobs and includes the HTTP Edge test file and `--allow-net`. The
  workflow succeeded on GitHub Actions at commit `58d30cc`; the local v1.2
  commit has not run remotely.
- Git: the v1.2 tree is committed locally. Nothing has been pushed or deployed.
- No remote Supabase project, Play Console access, signing key, deployed
  privacy/deletion URL, or store assets are available. None were used.

## v1.2 delivered (local commit)

- Migration `20261021001300_admin_transactions.sql`:
  - `admin_save_product`, `admin_save_guide`, and `admin_save_rule` perform
    complete saves in one transaction.
  - Rule saves suppress intermediate snapshots and write exactly one final
    version with the final evidence set.
  - Direct evidence edits on a published rule create a version; draft evidence
    edits do not.
  - Evidence can only be attached to a rule owned by the acting admin.
  - Symmetric relationships are canonicalized server-side.
  - `admin_rollback_rule` restores the exact snapshot evidence set and writes a
    new version instead of rewriting old history.
- `supabase/tests/80_admin_transactions.sql` covers atomicity, history counts,
  evidence ownership, canonical orientation, and rollback fidelity.
- Admin API/pages call the RPCs; client-supplied actor IDs were removed and
  rule updates require a change reason.
- Android contribution forms for `new_product`, `new_rule`, `evidence`, and
  `guide`, with payload builders, 11 builder unit tests, and English/Urdu
  labels. The evidence action is available from the rule screen.
- Localized enum/status/relationship/difficulty labels and remaining hardcoded
  Android labels.
- Eight HTTP-level Edge tests in `supabase/functions/tests/http_test.ts`.
- Android version `1.2.0` (version code 3).
- Documentation updated for the new writes, tests, CI state, and known gaps.

## Priority 1: Remaining browser and release-surface gaps

The v1.2 live-stack reset, Edge invocation, admin browser sweep, and device
suite are complete. Remaining browser work:

1. Test the public `/privacy` and `/delete-account` routes against an authorized
   deployed backend, including configured controller/support values and a real
   deletion request/cancel cycle.
2. Browser-test full session-expiry and token-refresh handling in the admin app.
3. Exercise the remaining moderation field-validation combinations
   (contribution reject/request-changes, request decline, report investigating)
   and any UI states not covered by the completed sweep.
4. Re-run the live smoke pass after any migration, RPC, or admin-page change.

Never connect to, reset, or mutate a remote Supabase project without explicit
owner authorization.

## Priority 2: Android device and accessibility

- Exercise the new contribution forms on device against the local stack:
  validation errors, successful submission, signed-out prompt, and Urdu/RTL.
- Extend instrumentation coverage beyond the checker summary and Room 1-to-2
  migration: browse, detail, auth prompt, deep links, collections, profiles,
  contributions, offline fallback, RTL, and dark theme.
- Verify favorites, votes, comments, requests, notifications, account-deletion
  request/cancel, and session expiry against the full stack.
- Complete TalkBack, switch access, 200% font-scale, and native-speaker Urdu
  review.

## Priority 3: Edge Functions and deployment

- Deploy to an authorized staging project and invoke both functions with real
  admin JWTs and database RPCs.
- Configure exact `ADMIN_ORIGIN` (comma-separated, no wildcard),
  `PURGE_CRON_SECRET`, and service-role secrets server-side.
- Schedule and monitor the purge worker.
- Re-run live-stack tests after any function or migration change.

## Priority 4: CI and release

1. Review, commit, and push the v1.2 tree; fix any runner-only failures. The
   workflow has not executed on the v1.2 changes.
2. Deploy the admin site with `/privacy` and `/delete-account`, configure the
   data controller and support email, and verify SPA fallback and CORS.
3. Produce a signed AAB with an upload key stored outside the repository.
4. Complete data-safety/content-rating declarations and produce store listing
   assets, including the deployed privacy policy and account-deletion URLs.
5. Validate the release AAB, R8 mapping, deep links, and rollback plan in an
   authorized environment.

## Verification commands

Database (Linux/macOS; adjust the port/user to your disposable server):

```bash
PGPASSWORD=postgres PGUSER=postgres PGPORT=55322 ./scripts/db_test.sh --keep
```

Windows:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/db_test.ps1 -Keep
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
  supabase/functions/tests/purge_test.ts \
  supabase/functions/tests/http_test.ts
deno test --allow-env --allow-net supabase/functions/tests
```

## Do not

- Do not connect to, reset, or mutate a remote Supabase project without explicit owner authorization.
- Do not run `seed.sql` or `supabase/tests/00_shim.sql` against production.
- Do not weaken RLS, expose vote rows, or infer compatibility without explicit published rules.
- Do not put a service-role key, signing password, or other secret in a client build or tracked file.
- Do not modify the applied migration `20261021001300_admin_transactions.sql`; add a new ordered migration and tests for further changes.
- Do not invent test counts, credentials, deployment status, store assets, or runtime results.
