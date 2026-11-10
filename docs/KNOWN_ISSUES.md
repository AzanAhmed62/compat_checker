# Known Issues

This file records verified limitations and release blockers. Items are not speculative promises.

## Release blockers

1. **Android release readiness remains unverified.** The debug build passes
   `:app:assembleDebug` and 17 JVM tests. On 2026-11-10 the connected suite
   passed **5/5** on a physical OnePlus 6T (Android 11), and live smoke testing
   against the local stack covered catalog, product detail, password sign-in,
   collection create/add/visibility, follow/unfollow, leaderboard, notification
   routing, guide Markdown, offline guide cache, cold and warm deep links,
   light/dark themes, and Urdu/RTL. Not verified on device:
   favorites/votes/comments/request/contribution mutations, account-deletion
   request/cancel, full session-expiry handling, 200% font scale,
   TalkBack/switch access, and native-speaker Urdu review. No signed AAB
   exists; the debug APK is not a release build.
2. **Local full-stack coverage is partial.** A local Supabase stack with the
   pinned `public.ecr.aws/supabase/postgres:17.11.0.011` image started and
   reset successfully on 2026-11-09, and Auth/PostgREST/RLS, social lifecycles,
   evidence validation, vote privacy, admin RPCs, and Storage object
   upload/read/delete policies by role were exercised over HTTP. Live Edge
   Function invocation and production headers/configuration remain unverified.
   SQL tests still use the PostgreSQL shim; Edge tests use in-process
   handlers.
3. **Edge Functions lack live deployment evidence.** Deno typechecking and 24
   handler tests pass locally, but no local or hosted HTTP invocation, real
   admin JWT/database RPC, scheduler, or function-secret deployment has been
   tested.
4. **No signed AAB, Play Console access, signing key, deployed privacy/deletion
   URL, or store assets exist.** Release declarations are not complete. The
   public `/privacy` and `/delete-account` routes are implemented but require
   deployment-specific configuration.
5. **Admin runtime coverage is partial.** Sign-in, dashboard, catalog, and
   users pages were exercised against the local stack with no browser console
   errors. Rule/evidence editing, rollback, reports, contributions, comments,
   requests, guides, media, CSV, analytics, and error-state workflows have not
   been exercised against the live backend.
6. **Account deletion web path is implemented but not deployed or tested against
   a deployed backend.** The admin web app has a public deletion request/cancel
   route; Play still requires an externally reachable HTTPS URL that has been
   tested against the deployed backend.

## Functional limitations

- Android collection, follow, public-profile, and leaderboard screens were
  exercised on a physical device against the local stack for the flows listed
  in `docs/TEST_PLAN.md`. Broader social mutations and error states remain
  unverified on device.
- Compose instrumentation coverage currently targets the checker result summary
  (unknown, compatible, incompatible, same-product) and the Room 1-to-2
  migration; 5/5 passed on device on 2026-11-10. Browse, detail, auth,
  collections, profiles, deep links, RTL, and dark-theme UI tests are not
  implemented.
- Android structured contributions currently focus on `product_edit` patches.
  Other contribution kinds exist server-side and in the admin console but have
  no dedicated mobile form.
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
- Email-confirmation and password-recovery deep links are not implemented in
  the mobile app. Local Supabase has confirmations disabled; production auth
  policy must be configured accordingly.
- Urdu resources exist but have not received native-speaker or TalkBack review.
- Accessibility has not been validated with TalkBack, switch access, or
  large-font layouts.
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
  tight (about 4.6 GB free, 96% used). Android Studio/Docker/Gradle compete for
  memory and disk. `apps/android/gradle.properties` caps heap and disables
  parallel workers/configuration cache for this reason. Do not perform global
  Docker cleanup while other local projects are running.
- The pinned Supabase PostgreSQL image and the local stack are present and were
  used successfully. Registry pulls may still be slow; do not replace the
  pinned image version to work around a transient pull failure.
- CI has four jobs (database, admin, Edge Functions, Android), but has not run
  on GitHub Actions.

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
                                                                                                                                                                                                                                                                                    