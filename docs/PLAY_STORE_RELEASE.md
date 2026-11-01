# Play Store Release

## Release status

No signed Android App Bundle has been produced. No Play Console account, signing key, upload key, or production Supabase project is available in this workspace. The steps below are requirements, not claims of completion.

## Signing

Release tasks fail unless these values are configured in untracked `apps/android/local.properties` or the environment:

```properties
RELEASE_STORE_FILE=C:\\secure\\compat-checker-upload.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=upload
RELEASE_KEY_PASSWORD=...
```

- Use a Play App Signing upload key; keep the keystore outside the repository.
- Back up the keystore and passwords in an approved secret manager.
- Never commit keystores, `local.properties`, passwords, or Play service-account JSON.
- The build script configures the release signing config only when `RELEASE_STORE_FILE` is present and fails release assembly/bundle tasks when any required value is missing.

## Build

```powershell
cd apps/android
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12'
.\gradlew.bat clean :app:bundleRelease
```

Expected output: `app/build/outputs/bundle/release/app-release.aab`.

Before uploading:

- [ ] Configure production `SUPABASE_URL`/`SUPABASE_ANON_KEY` (public anon/publishable key only).
- [ ] Verify the build does not contain `local.properties` or secrets.
- [ ] Confirm `versionCode` increments and `versionName` is correct.
- [ ] Run `:app:testDebugUnitTest` and an instrumentation suite on a release-like build.
- [ ] Test install from the signed AAB using bundletool or Play internal testing.
- [ ] Verify deep links, sign-in, favorites, voting, comments, requests, contributions, account deletion, RTL, dark theme, and offline cache.
- [ ] Confirm minSdk 26 / targetSdk 36 and compileSdk 36.
- [ ] Review R8 mapping upload; preserve the mapping file for crash deobfuscation.

## Play Console declarations

Prepare accurate declarations; do not copy generic claims:

- **Data safety:** account identifiers (email), user-generated content (comments, requests, contributions), favorites, votes (aggregate to others), reports, diagnostics if crash reporting is added. State encryption in transit, deletion request process, and retention. Update after adding any analytics/crash SDK.
- **Content rating:** user-generated content and moderation exist. Complete the questionnaire honestly.
- **Ads:** the app contains no ads and no ad SDK.
- **Target audience:** likely general/repair-professional audience; complete the families policy assessment.
- **News/app status:** not a news app.
- **Government app:** no.
- **Financial features:** none.
- **Privacy policy:** required. The admin web app now ships `/privacy` and `/delete-account`, but no deployment URL is configured. Before publishing, set `VITE_DATA_CONTROLLER` and `VITE_SUPPORT_EMAIL`, deploy the site, and use the resulting HTTPS `/privacy` URL. The policy covers Supabase auth, contributions, moderation, reports, deletion, and retention. Do not invent a controller or support address.
- **Account deletion:** the Android app provides an in-app request/cancel path, and the admin web app provides a public `/delete-account` route. Play also requires an externally reachable web deletion URL. Both routes require the deployed admin URL, public Supabase configuration, and `ADMIN_ORIGIN` coverage. Do not declare completion until that URL exists and is tested.

## Store listing assets

Required items are not present and must be produced:

- app title and short/full description reviewed for accuracy
- 512×512 icon
- feature graphic 1024×500
- phone screenshots (multiple sizes)
- privacy policy URL
- support email/URL
- release notes

Avoid implying certification, complete coverage, or guaranteed repair outcomes. Describe evidence-backed rules and the explicit `unknown` state.

## Release checklist

1. Freeze schema and run the full SQL suite.
2. Deploy and verify backend/migrations in an authorized staging project.
3. Deploy admin console, including `/privacy` and `/delete-account`; configure controller/support values and restrict CORS.
4. Deploy Edge Functions; verify secrets and scheduler.
5. Build signed AAB with production config.
6. Internal testing track with seed-free production data.
7. Complete data safety, content rating, and account deletion declarations.
8. Resolve all `docs/KNOWN_ISSUES.md` release blockers or obtain written risk acceptance.
9. Staged rollout with crash/ANR monitoring.
10. Preserve mapping file, build metadata, and migration checksums/version tags.

## Rollback

Use Play staged rollout halt and upload a higher-version fix. Keep backend changes backward-compatible during the rollout window. Never reset a production database to roll back application code.
