# Repository Audit

Audit date: 2026-10-21  
Baseline commit: `ac78fbc` (`chore: preserve audited Android demo baseline`)  
Auditor: implementation agent

## 1. Scope and method

The audit covered every tracked file in the original repository:

- Gradle configuration and dependency catalog.
- Android manifest, resources, Kotlin sources, and tests.
- Room schema, DAOs, remote data source, repositories, view models, and navigation.
- README, `.env.example`, and repository metadata.

Claims were checked against source code rather than the README. No remote Supabase project,
Firebase project, Play Console account, or signing key was contacted or inspected. No database
or service outside this workstation was modified.

The original demo was committed unchanged as `ac78fbc` before restructuring, so the audited
state remains recoverable.

## 2. Verified current state

| Area | State at baseline | Assessment |
|---|---|---|
| Backend | No migrations, schema, RLS, RPCs, Edge Functions, or Supabase project configuration | Absent |
| Admin console | No web application | Absent |
| CI/CD | No workflows or release automation | Absent |
| Documentation | One README plus `.env.example` | Incomplete and partly inaccurate |
| Android UI | Eight Compose screens with Material 3 theme | Useful visual baseline |
| Android architecture | Manual service locator, repositories, view models | Demo-grade, not release-grade |
| Android build | AGP 9.1.1, Gradle wrapper metadata 9.3.1, wrapper JAR missing, no Android SDK platform installed | Not buildable |
| Tests | Three JVM test files asserting demo fixtures | Do not test behavior or security |
| Backend integration | DTOs do not match any schema; no backend exists | Non-functional |
| Data provenance | Fabricated demo catalog and "verified" evidence | Violates product trust model |
| Git | No repository before this work; local `main` initialized for the audit | Baseline preserved |

## 3. Critical product-trust defects

### 3.1 Fabricated verified data

`app/src/main/java/com/example/compatchecker/data/remote/DemoCatalogDataSource.kt` contains
products, compatibility rules, confidence levels, risk levels, and evidence URLs pointing at
`example.com`. Several records are presented as high-confidence verified compatibility. This is
indistinguishable in the UI from real evidence and directly contradicts the stated product
premise. It must not be part of a production data path.

### 3.2 Client invents incompatibility

`CompatibilityCheckerViewModel.reevaluate()` does the following when no rule exists:

- Same brand: emits `UNVERIFIED` with no evidence.
- Different brands: emits `INCOMPATIBLE` and a "high probability" claim.
- Same product: synthesizes a `COMPATIBLE` rule with high confidence.

Compatibility is therefore inferred on the device from brand names and connector strings. The
requirement is the opposite: only explicit, evidence-backed server rules may produce a
relationship, and absence of data must be shown as unknown.

### 3.3 Client-controlled authorization and reputation

- `SupabaseRemoteDataSource` inserts requests, comments, contributions, and products directly
  into tables. Nothing prevents a client from choosing its own status, role, or scores.
- `UserRepositoryImpl` hardcodes `REPAIR_TECHNICIAN`, `CONTRIBUTOR`, badges, contribution counts,
  and fake guest sessions. A user can appear trusted without any server record.
- Banned users are not checked anywhere.
- `requestAccountDeletion()` signs out locally and returns success without deleting or scheduling
  deletion of the account.

### 3.4 Automatic demo seeding

`ProductRepositoryImpl` writes `DemoCatalogDataSource.demoProducts` into Room on first launch.
`refreshCatalog()` re-seeds the demo data when Supabase is not configured. Offline mode
therefore surfaces fabricated catalog content as if it were cached backend data.

### 3.5 Misleading source labels

The UI distinguishes `DEMONSTRATION`, `LIVE_SUPABASE`, and `CACHED_OFFLINE`, but cached data is
not validated against a backend and "live" data uses DTOs that no backend could satisfy. Error
paths silently fall back to local demo content.

## 4. Security and data-integrity findings

1. No database exists, so there are no constraints, RLS policies, role checks, or audit records.
2. The Android client is the only authority for contribution status, points, badges, and roles.
3. Service-role keys are not present in the repository; however, the design would require them
   for several client operations if it were connected naively to a database.
4. `AndroidManifest.xml` enables backups with a broad backup configuration and has no deep links.
5. Room uses `fallbackToDestructiveMigration()`, so schema changes discard all local data.
6. Remote fetches swallow exceptions and return `null`, making authorization errors, network
   failures, and empty catalogs indistinguishable.
7. There is no rate limiting, content moderation, report workflow, or append-only ledger.
8. There are no server-side lifecycle rules for drafts, published rules, retirement, or rollback.

## 5. Functional gaps against requirements

### Backend

- No PostgreSQL schema for profiles, roles, bans, catalog, rules, evidence, votes, requests,
  contributions, comments, reports, favorites, follows, notifications, collections, guides,
  media, audit logs, analytics, or rate limits.
- No aggregate-only public vote projections.
- No atomic moderation or contribution application.
- No search ranking, typo tolerance, filters, or pagination contract.
- No CSV import/export, graph API, or history/rollback.
- No Edge Functions.

### Android

- Navigation is a custom `remember { mutableStateOf<ScreenRoute> }`, not Navigation Compose.
  Deep links, saved-state restoration, and predictive back are absent.
- Product detail opens without passing the product to the checker and cannot seed checker state.
- Search is case-insensitive substring filtering on the local Room cache.
- No remote favorites, voting, contributions, comments, requests, follows, notifications, or
  collections against a real API.
- No localization resources for Urdu; all UI copy is hardcoded English.
- No pagination, pull-to-refresh contract, network monitor, or structured error states.
- No accessibility audit; multiple icon-only controls and custom clickable surfaces need review.
- `supabase-kt` is instantiated in a global config object and called directly from a remote data
  source, rather than being isolated behind a testable gateway.

### Admin

- No authentication, dashboard, catalog management, moderation queues, user/role management,
  analytics, audit log viewer, media management, or import/export.

### Operations

- No `.github/workflows`, environment template for backend/admin, local Supabase config,
  deterministic seed, database tests, deployment runbook, or release checklist.
- README describes demonstration data and AI Studio signing as if they were production features.

## 6. Build and tooling findings

- `gradle/wrapper/gradle-wrapper.jar` was absent; `gradlew` could not run.
- Wrapper metadata referenced Gradle 9.3.1 while the available offline toolchain was Gradle
  8.13. AGP 9.1.1 requires a newer Gradle line and a different Kotlin integration model.
- `settings.gradle.kts` applied the Foojay resolver `1.0.0`, which could not be resolved in the
  original build attempt.
- No Android SDK platform or build-tools were installed (`platform-tools` only), so no source
  compilation had been performed for the baseline.
- Dependencies included unused Firebase, Retrofit, Moshi, camera, location, and credentials
  stacks. Firebase App Check was configured without a Firebase project.
- The release signing block referenced `my-upload-key.jks` and AI Studio environment variables,
  which is unsuitable for an open repository and cannot produce a release build here.
- Application ID `com.aistudio.compatchecker.kpxm` and namespace `com.example` are placeholders.
- Duplicate unused theme files exist under `com.example.ui.theme`.
- Unit tests are fixture assertions, not behavioral tests. The Robolectric test contains no
  meaningful assertions.

## 7. What is reusable

- The Material 3 color/type foundation and most screen layouts.
- Domain vocabulary already close to the target model: products, variants, specifications,
  rules, evidence, confidence, guides, requests, comments, contributions.
- Room/DataStore dependencies and a manual DI pattern that can be retained while contracts are
  replaced.
- Existing Compose component ideas (compatibility badge, evidence card, product card), subject
  to a truthfulness and accessibility rewrite.
- Existing package structure as a starting point, after package/application-ID cleanup.

Everything that determines truth, authorization, moderation, or persistence is being replaced.

## 8. Constraints discovered

- Docker and the Supabase CLI are not installed. Local verification therefore uses a dedicated
  PostgreSQL 18 cluster on port `55432` with Supabase-compatible shims. Supabase CLI commands
  remain the documented path for environments that have the CLI.
- No remote Supabase project is linked. No remote reset, migration, or seed will be run.
- Disk space is limited; build caches are directed to `D:\DEV\build-cache`.
- A Play Store release requires externally owned assets and credentials: application ID domain,
  upload key, Play Console account, privacy policy URL, support contact, and production Supabase
  project. None are invented here.

## 9. Remediation direction

1. Establish an ordered, version-controlled SQL schema with constraints and RLS as the only
   source of truth.
2. Make all identity-dependent and moderation mutations transactional server functions.
3. Publish only aggregate vote data and evidence-backed rule projections.
4. Replace demo seeding and inference with explicit `unknown` results.
5. Build the admin console against RLS/RPC contracts, never a service-role key.
6. Refactor Android around gateway interfaces, real sessions, offline cache, and
   Navigation Compose.
7. Add deterministic local setup, tests, CI, and truthful release documentation.
8. Record every unresolved external dependency in `NEXT_TASK.md` and `KNOWN_ISSUES.md`.
