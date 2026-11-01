# Known Issues

This file records verified limitations and release blockers. Items are not speculative promises.

## Release blockers

1. **Android runtime and release readiness are unverified.** The CLI build passes `:app:assembleDebug` and 7 JVM tests; the checker Compose instrumentation suite compiles (test APK 996,982 bytes), but no emulator/device session has executed it. No live Supabase session, full accessibility review, or signed AAB exists. The debug APK is not a release build.
2. **Full Supabase runtime is unverified.** Docker Desktop is installed, but its Linux engine was unavailable in the latest check. No local `supabase start`/`db reset` has been completed, so Auth, PostgREST exposure, Storage policies, and live HTTP behavior have not been exercised together. SQL tests use the PostgreSQL shim; Edge tests use in-process handlers.
3. **Edge Functions lack live deployment evidence.** Deno typechecking and 24 handler tests pass locally, but no hosted function invocation, real admin JWT/database RPC, scheduler, or function-secret deployment has been tested.
4. **No signed AAB, Play Console access, signing key, deployed privacy/deletion URL, or store assets exist.** Release declarations are not complete. The public `/privacy` and `/delete-account` routes are implemented but require deployment-specific configuration.
5. **No end-to-end app/admin verification against a live API.** Admin behavior is covered by typecheck, unit tests, and production build, not runtime integration.
6. **Account deletion web path is implemented but not deployed or tested.** The admin web app has a public deletion request/cancel route; Play still requires an externally reachable HTTPS URL that has been tested against the deployed backend.

## Functional limitations

- Android coverage of collections, follows, media upload, and full report moderation is absent or minimal. Public browsing, checking, guides, requests, comments, favorites, contributions, notifications, and account settings are the implemented member scope.
- Compose instrumentation coverage currently targets the checker result summary (unknown, compatible, incompatible, same-product). Browse, detail, auth, deep links, RTL, and dark-theme UI tests are not implemented.
- Android structured contributions currently focus on `product_edit` patches. Other contribution kinds exist server-side and in the admin console but have no dedicated mobile form.
- Guide Markdown is displayed as plain text with headings/bullets, not a full Markdown renderer.
- Notification links are displayed; not all server link shapes are mapped to in-app destinations.
- Offline mode covers previously loaded catalog search results and product details. Guides, requests, comments, and account data require network.
- Search caching keeps records up to 30 days; stale content may be shown with an offline indicator. There is no manual cache clear yet.
- No crash reporting or product analytics SDK is present. Server analytics exist but are only as complete as deployed event calls.
- The Android app does not expose admin features by design.
- Email-confirmation and password-recovery deep links are not implemented in the mobile app. Local Supabase has confirmations disabled; production auth policy must be configured accordingly.
- Urdu resources exist but have not received native-speaker or TalkBack review.
- Accessibility has not been validated with TalkBack, switch access, or large-font layouts.
- The privacy policy and deletion pages use `VITE_DATA_CONTROLLER` and `VITE_SUPPORT_EMAIL`; no real controller or support address is configured in this repository. Do not publish them with empty placeholders.

## Security and operations

- No penetration test, dependency vulnerability gate, secret scan, or load test has been completed.
- Admin MFA, rate-limit tuning, log redaction, backup restoration, and monitoring are deployment tasks, not verified here.
- Edge Function CORS allows only exact origins from `ADMIN_ORIGIN` (comma-separated). Wildcards are ignored and unset configuration falls back to local development origins. Production must set the deployed admin origin; otherwise all production browser calls are rejected.
- Purge worker scheduling is not configured in this repository; it requires an external authenticated cron caller.
- Remote Supabase must not be reset or modified without explicit owner authorization.
- Local Edge tests log a simulated database error message in one failure path. The client receives a generic message, but production log review and retention still need an operational decision.

## Environment

- The workspace Android SDK has platform 36 and build-tools 36.0.0 installed, and `local.properties` points at it. The build is intentionally pinned to compileSdk/targetSdk 36 with AGP 8.13.2, Compose BOM 2025.09.00, Activity Compose 1.10.1, and Navigation Compose 2.9.5. Newer AndroidX releases require compileSdk 37 and AGP 9.1; do not bump them without a coordinated AGP/Gradle/SDK upgrade.
- The Gradle wrapper is provisionally **8.14.5**. Keep it only while the CLI build and tests pass; CI is pinned to the same version. Kotlin 2.4.20 warned that Gradle below 8.14.4 would become unsupported in Kotlin 2.5.
- The development machine has under 4 GB of RAM. Android Studio and Docker compete for the same memory; close Android Studio before CLI Gradle builds and avoid running the emulator and full Supabase stack together. `apps/android/gradle.properties` caps heap and disables parallel workers/configuration cache for this reason.
- Docker Desktop is installed, but its Linux engine was not reachable at the last check (`dockerDesktopLinuxEngine` pipe missing). Starting it may require WSL/Docker Desktop recovery and enough free memory.
- Disk space is constrained but currently sufficient for the repository and caches. Keep Gradle caches outside the repository and clean generated outputs rather than source.
- Node.js `npm.ps1` is blocked by execution policy on this machine; use `npm.cmd`.
- CI has four jobs (database, admin, Edge Functions, Android), but has not run on GitHub Actions.

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
