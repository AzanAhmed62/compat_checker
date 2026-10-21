# Known Issues

This file records verified limitations and release blockers. Items are not speculative promises.

## Release blockers

1. **Android runtime and release readiness are unverified.** `:app:assembleDebug` and `:app:testDebugUnitTest` pass with 7 JVM tests, but no emulator/device, instrumentation/Compose UI test, live Supabase session, accessibility review, or signed release artifact exists. The debug APK is not a release build.
2. **Full Supabase runtime is unverified.** Docker and Supabase CLI are unavailable. Migrations pass a PostgreSQL/shim SQL suite, but Auth, PostgREST exposure, Storage policies, and HTTP behavior have not been exercised in a full stack.
3. **Edge Functions have never been executed or type-checked.** Deno is unavailable. `admin-csv` and `purge-pending-accounts` require execution tests and deployment review.
4. **No signed AAB, Play Console access, signing key, privacy-policy URL, or store assets exist.** Release declarations are not complete.
5. **No end-to-end app/admin verification against a live API.** Admin behavior is covered by typecheck, unit tests, and build, not runtime integration.
6. **Account deletion web path is missing.** The in-app request and database purge worker exist, but Play's external deletion requirement and support workflow need deployment-specific implementation.

## Functional limitations

- Android coverage of collections, follows, media upload, and full report moderation is absent or minimal. Public browsing, checking, guides, requests, comments, favorites, contributions, notifications, and account settings are the implemented member scope.
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

## Security and operations

- No penetration test, dependency vulnerability gate, secret scan, or load test has been completed.
- Admin MFA, rate-limit tuning, log redaction, backup restoration, and monitoring are deployment tasks, not verified here.
- Edge Function CORS defaults to `*` when `ADMIN_ORIGIN` is unset. Production must set a restrictive origin.
- Purge worker scheduling is not configured in this repository; it requires an external authenticated cron caller.
- Remote Supabase must not be reset or modified without explicit owner authorization.

## Environment

- The workspace Android SDK now has platform 36 and build-tools 36.0.0 installed, and `local.properties` points at it. The build is intentionally pinned to compileSdk/targetSdk 36 with AGP 8.13.2, Compose BOM 2025.09.00, Activity Compose 1.10.1, and Navigation Compose 2.9.5. Newer AndroidX releases require compileSdk 37 and AGP 9.1; do not bump them without a coordinated AGP/Gradle/SDK upgrade.
- Portable Gradle 8.13 is used. Kotlin 2.4.20 warns that Gradle versions below 8.14.4 will be unsupported in Kotlin 2.5. Pin/upgrade Gradle during maintenance.
- The development machine has under 4 GB of RAM. `apps/android/gradle.properties` caps the Gradle heap at 1536 MB, disables parallel workers and configuration cache, and uses Serial GC so the build does not exhaust native memory. Raise these only when more RAM is available.
- Disk space is constrained; keep Gradle caches outside the repository and clean generated outputs rather than source.
- Node.js `npm.ps1` is blocked by execution policy on this machine; use `npm.cmd`.

## Resolved during the rebuild

- Removed the fictional demo catalog and client-side compatibility inference.
- Removed Firebase, AI Studio secrets, Foojay, and unrelated plugins from the Android build.
- Replaced legacy corrective migrations with ordered version-controlled migrations.
- Added service-role-only account purge and Edge Functions.
- Removed automatic release signing assumptions; release builds fail fast without signing configuration.
