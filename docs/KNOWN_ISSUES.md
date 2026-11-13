# Known Issues

This file records verified limitations and release blockers. Items are not speculative promises.

## Release blockers

1. **Android release readiness remains unverified.** The v1.2 debug build passes
   `:app:assembleDebug` and 28 JVM tests, and the v1.2 connected suite passed
   **5/5** on a physical OnePlus A6010 (Android 11) on 2026-11-12. Live smoke
   testing against the local stack covered catalog, product detail, password
   sign-in, collection create/add/visibility, follow/unfollow, leaderboard,
   notification routing, guide Markdown, offline guide cache, cold and warm deep
   links, light/dark themes, and Urdu/RTL labels. A second live pass on
   2026-11-12/13 covered the new product/rule/evidence/guide contribution forms,
   rule voting, favorites, comments, product request submit/vote, and
   account-deletion request/cancel; session refresh after expiry and signed-out
   cleanup after server-side session revocation were also exercised. At 200%
   font scale the bottom-navigation labels wrapped and clipped and discussion
   author metadata wrapped poorly. Not verified on device: TalkBack/switch
   access and native-speaker Urdu review; a repeat Urdu/RTL pass was interrupted
   after reboot, and the device was left with `ur-PK` configured. The local
   database was not reset after the mutation pass. No signed AAB exists; the
   debug APK is not a release build.
2. **Local full-stack coverage is partial.** A local Supabase stack with the
   pinned `public.ecr.aws/supabase/postgres:17.11.0.011` image was reset and
   exercised on 2026-11-12: all 13 migrations plus seed, Auth/PostgREST/RLS,
   social lifecycles, evidence validation, vote privacy, admin RPCs, Storage
   object policies, and live Edge Function HTTP invocation against the real
   local database. Production headers, hosted configuration, and hosted Edge
   deployment remain unverified. SQL tests still use the PostgreSQL shim.
3. **Edge Functions lack hosted deployment evidence.** Deno typechecking and 32
   tests pass locally, including eight HTTP-level tests over ephemeral
   localhost servers with injected clients. On 2026-11-12 the functions were
   invoked in a local Edge Runtime with a real admin JWT and live database,
   including CSV export, origin rejection, cron-secret enforcement, and an
   account purge. No hosted function, scheduler, production function secret, or
   deployed `ADMIN_ORIGIN` has been tested.
4. **No signed AAB, Play Console access, signing key, deployed privacy/deletion
   URL, or store assets exist.** Release declarations are not complete. The
   public `/privacy` and `/delete-account` routes are implemented but require
   deployment-specific configuration.
5. **Admin runtime coverage is broad but not exhaustive.** On 2026-11-12 the
   browser sweep covered sign-in, dashboard, catalog, atomic product/guide/rule
   saves, rule versioning and rollback, reports, contributions, comments,
   requests, users/roles/status (including suspended-session denial), media,
   CSV export/import and rollback, analytics, audit filtering and before/after
   detail, pagination, the expected negative CSV error, and contributor/trusted
   route gating. No unexpected console errors were recorded; the deliberate
   duplicate-slug import produced the expected HTTP `400`. Remaining gaps: the
   public `/privacy` and `/delete-account` routes have not been tested against a
   deployed backend, full session-expiry/refresh handling is not browser-tested,
   and not every field-validation or moderation combination was exercised.
   During rapid automation, the media registry and users table sometimes
   rendered before the refetch completed; a reload showed the correct state, and
   SQL confirmed the writes.
6. **Account deletion web path is implemented but not deployed or tested against
   a deployed backend.** The admin web app has a public deletion request/cancel
   route; Play still requires an externally reachable HTTPS URL that has been
   tested against the deployed backend.

## Functional limitations

- Android collection, follow, public-profile, and leaderboard screens were
  exercised on a physical device against the local stack for the flows listed
  in `docs/TEST_PLAN.md`. The 2026-11-12/13 mutation pass also exercised
  contributions, rule votes, favorites, comments, product requests/votes, and
  account-deletion request/cancel. Broader error states and offline behavior for
  those mutations remain unverified on device.
- Compose instrumentation coverage currently targets the checker result summary
  (unknown, compatible, incompatible, same-product) and the Room 1-to-2
  migration; 5/5 passed on device on 2026-11-12 under v1.2 source. Browse,
  detail, auth, collections, profiles, contributions, deep links, RTL, and
  dark-theme UI tests are not implemented.
- Android now includes contribution forms for `new_product`, `new_rule`,
  `evidence`, and `guide` alongside the existing `product_edit` correction
  path. Payload builders are unit-tested, and the forms were exercised on device
  against the local stack (pending rows confirmed in SQL). Exhaustive
  invalid-input and moderation permutations were not run on device.
- Guide Markdown rendering is a deliberately small subset: headings,
  paragraphs, lists, quotes, fenced code, bold, italic, inline code, and
  absolute HTTP(S) links. Raw HTML is displayed as text, not interpreted.
- Notification links are parsed for product, guide, rule, profile, and
  collection destinations. Unknown or malformed links show a visible
  "unsupported link" message instead of opening an external browser.
- Offline mode covers previously loaded catalog search results, product
  details, guide lists, and guide details (30-day cache). Requests, comments,
  collections, profiles, leaderboards, and account data require network.
- Offline guide fallback waits for the network request to time out (about nine
  seconds on the test device) before showing cached content.
- Search and guide caching keep records up to 30 days; stale content may be
  shown with an offline indicator. There is no manual cache clear yet.
- No crash reporting or product analytics SDK is present. Server analytics
  exist but are only as complete as deployed event calls.
- The Android app does not expose admin features by design.
- A contributor who signs in to the admin site without moderator privileges
  remains on the login form until navigating to a protected route, where the
  "Administration access denied" screen appears. This is a UX issue, not an
  authorization bypass.
- Email-confirmation and password-recovery deep links are not implemented in
  the mobile app. Local Supabase has confirmations disabled; production auth
  policy must be configured accordingly.
- Urdu resources exist but have not received native-speaker review. Label-level
  Urdu/RTL rendering was smoke-tested earlier; a repeat Urdu/RTL pass on
  2026-11-13 was interrupted after reboot and is not verified.
- TalkBack and switch access have not been validated.
- At 200% font scale the bottom-navigation labels wrap and clip, and discussion
  author metadata wraps into a very narrow column. This needs a layout fix and
  re-test before claiming large-font accessibility.
- The privacy policy and deletion pages use `VITE_DATA_CONTROLLER` and
  `VITE_SUPPORT_EMAIL`; no real controller or support address is configured in
  this repository. Do not publish them with empty placeholders.

## Security and operations

- No penetration test, dependency vulnerability gate, secret scan, or load test
  has been completed.
- Admin MFA, rate-limit tuning, log redaction, backup restoration, and
  monitoring are deployment tasks, not verified here.
- Edge Function CORS allows only exact origins from `ADMIN_ORIGIN`
  (comma-separated). Wildcards are ignored and unset configuration falls back to
  local development origins. Production must set the deployed admin origin;
  otherwise all production browser calls are rejected.
- Purge worker scheduling is not configured in this repository; it requires an
  external authenticated cron caller.
- Remote Supabase must not be reset or modified without explicit owner
  authorization.
- Local Edge tests log a simulated database error message in one failure path.
  The client receives a generic message, but production log review and
  retention still need an operational decision.

## Environment

- The current workspace is Linux (Ubuntu) with JDK 21, Android SDK platform 36,
  build-tools 36.0.0, Node.js 22+, and Docker 29.9.3. Windows remains supported
  by the PowerShell runner and `npm.cmd`, but the Linux commands in this
  documentation are the current verified path.
- The build is intentionally pinned to compileSdk/targetSdk 36 with AGP 8.13.2,
  Compose BOM 2025.09.00, Activity Compose 1.10.1, and Navigation Compose
  2.9.5. Newer AndroidX releases require compileSdk 37 and AGP 9.1; do not bump
  them without a coordinated AGP/Gradle/SDK upgrade.
- The Gradle wrapper is **8.14.5**. Keep it only while the CLI build and tests
  pass; CI is pinned to the same version.
- The development machine has 8 GB of RAM, and root filesystem free space is
  tight (about 3.1 GB free, 98% used). Android Studio/Docker/Gradle compete for
  memory and disk. `apps/android/gradle.properties` caps heap and disables
  parallel workers/configuration cache for this reason. Do not perform global
  Docker cleanup while other local projects are running.
- The pinned Supabase PostgreSQL image and the local stack are present and were
  used successfully. Registry pulls may still be slow; do not replace the
  pinned image version to work around a transient pull failure.
- CI has four jobs (database, admin, Edge Functions, Android). The workflow
  succeeded on GitHub Actions at commit `58d30cc`; the v1.2 commits remain local
  because no GitHub credentials are configured, so their remote result has not
  been observed.

## Resolved during the rebuild

- Removed the fictional demo catalog and client-side compatibility inference.
- Removed Firebase, AI Studio secrets, Foojay, and unrelated plugins from the Android build.
- Replaced legacy corrective migrations with ordered version-controlled migrations.
- Added service-role-only account purge and Edge Functions.
- Removed automatic release signing assumptions; release builds fail fast without signing configuration.
- Replaced wildcard Edge Function CORS with exact configured-origin CORS and local-only fallback.
- Added Deno typechecking and 24 Edge handler tests, including CSV formula neutralization and bounded purge behavior.
- Added public privacy and account-deletion routes and self-service deletion/cancellation UI.
- Added Compose checker-summary instrumentation coverage and pluralized the rule-count message.
- Added v1.1 Android collections (public/private), follows, public profiles,
  leaderboards, brand filtering, offline guide caching, a safe Markdown subset,
  and notification routing for product, guide, rule, profile, and collection
  links.
- Added social read-model RPCs and collection/profile SQL coverage, plus
  server-side evidence URL validation for standalone evidence contributions.
- Added v1.2 atomic admin saves (`admin_save_product`, `admin_save_guide`,
  `admin_save_rule`), single-snapshot rule versioning, exact-evidence rollback,
  and SQL coverage in `80_admin_transactions.sql`.
- Added v1.2 Android contribution forms for new products, new rules, evidence,
  and guides, with builder unit tests and English/Urdu labels.
- Added eight HTTP-level Edge Function tests and updated CI to run them with
  network permission.
