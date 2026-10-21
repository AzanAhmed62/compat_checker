# Compat Checker — Native Android Client Foundation

**Compat Checker** is an evidence-based native Android client application built with **Kotlin** and **Jetpack Compose**. It helps repair technicians, hardware engineers, and device owners verify physical, electrical, and cryptographic compatibility between smartphones, regional variants, displays, batteries, camera modules, and sub-assemblies.

Compat Checker never hallucinates or invents compatibility claims using an LLM. Every status is explicitly classified using teardown evidence, OEM service bulletins, laboratory break-out tests, and connector pinout inspections.

---

## 🛠 Tech Stack & Dependencies

- **Language:** Kotlin 2.2.10
- **UI Toolkit:** Jetpack Compose with Material Design 3 (M3)
- **Architecture:** Clean MVVM with Unidirectional Data Flow (UDF) & Service Locator (`AppContainer`)
- **Reactive Concurrency:** Kotlin Coroutines & `StateFlow`
- **Primary Backend:** Supabase via **Supabase Kotlin SDK (`supabase-kt` 3.1.1)**
  - `postgrest-kt` (PostgreSQL / PostgREST)
  - `auth-kt` (Authentication & User Sessions)
  - `realtime-kt` (Realtime Subscriptions)
  - `storage-kt` (Evidence Photos & Teardown Documents)
  - `ktor-client-okhttp` 3.0.3 HTTP engine
- **Local Persistence & Caching:** Room Database 2.7.0 (with KSP code generation)
- **User Preferences:** Jetpack DataStore Preferences 1.1.7
- **Serialization:** `kotlinx.serialization` 1.8.0
- **Image Loading:** Coil Compose 2.7.0
- **Android Target:** API 36 (Android 15+) with minimum SDK 24 (Android 7.0+)

---

## 🏛 Architectural Structure

```
com.example.compatchecker/
├── CompatApplication.kt           # Application class initializing global AppContainer
├── core/
│   ├── config/                    # SupabaseConfig, DataSourceType, DataEnvelope
│   ├── di/                        # AppContainer (pure constructor injection)
│   └── preferences/               # UserPreferencesRepository (DataStore)
├── domain/
│   └── model/
│       ├── Product.kt             # Product, ProductCategory, HardwareVariant, ProductSpecification
│       ├── Compatibility.kt       # CompatibilityRule, CompatibilityStatus, RiskLevel, EvidenceSource
│       └── Community.kt           # ProductRequest, Comment, Contribution, RepairGuide, UserProfile
├── data/
│   ├── local/                     # Room Entities, DAOs, TypeConverters, CompatDatabase
│   ├── remote/                    # SupabaseRemoteDataSource, DemoCatalogDataSource
│   └── repository/                # ProductRepository, CompatibilityRepository, CommunityRepository,
│                                  # FavoritesRepository, RepairGuideRepository, UserRepository
└── ui/
    ├── components/                # DataSourceBanner, CompatibilityBadge, ProductCard, EvidenceCard
    ├── navigation/                # AppNavigation, NavDestination, ScreenRoute, BackHandler
    ├── screens/                   # HomeScreen, SearchScreen, ProductDetailScreen,
    │                              # CompatibilityCheckerScreen, GuidesScreen, CommunityHubScreen,
    │                              # ProfileScreen, SettingsScreen
    ├── theme/                     # Color.kt, Theme.kt, Type.kt
    └── viewmodels/                # HomeViewModel, SearchViewModel, ProductDetailViewModel,
                                   # CompatibilityCheckerViewModel, CommunityViewModel,
                                   # GuidesViewModel, ProfileViewModel, SettingsViewModel
```

---

## 🔒 Supabase Backend & Credential Configuration

Compat Checker accesses Supabase using the public anonymous API key and project URL.
**Never store service-role secret keys or database passwords in the client application.**

### 1. Development / Demonstration Benchmark Mode (Default)
When `SUPABASE_URL` is empty or left as placeholder, Compat Checker runs automatically in **Demonstration Benchmark Mode**:
- Loads verified technical hardware datasets (Galaxy S24 Ultra, iPhone 15 Pro, Pixel 8 Pro, OEM panels, batteries, BMS cryptographic pairing rules).
- Caches data into Room for complete offline operation.
- Shows a clear top banner: `[Demonstration Benchmark Mode • Unconnected to Supabase]` with an instant link to the Backend Inspector.

### 2. Live Supabase Connection
To connect to your own Supabase project:
1. In the AI Studio Secrets panel, or in your local `.env` file (copied from `.env.example`), provide:
   ```properties
   SUPABASE_URL=https://your-project.supabase.co
   SUPABASE_ANON_KEY=eyJhbGciOi...your-public-anon-key...
   ```
2. Build the app. The Secrets Gradle Plugin injects these securely into `BuildConfig.SUPABASE_URL` and `BuildConfig.SUPABASE_ANON_KEY`.
3. Open **Settings & Backend Inspector** in the app and tap **Test Supabase Connection** to verify live connectivity.

---

## 📱 Core Features & User Flows

1. **Home & Discovery:** Featured hardware categories, recent verified rules, trending smartphones, and quick comparator launch.
2. **Fast Search:** Live query matching against names, exact model numbers (e.g. `SM-S928B`, `A2849`), internal aliases (`Husky`, `Eureka`), and recent search query history.
3. **Hardware Specifications:** Pin counts, connector types (Hirose BM28, Panasonic AXT4), battery voltages, and regional variant breakdowns (US eSIM vs Global nano-SIM).
4. **Compatibility Comparator:** 2-slot interactive matrix comparing Host Device and Replacement Part. Evaluates Direct Fit, Conditional Fit (requiring micro-soldering or software calibration), and Incompatible physical/electrical mismatches.
5. **Cryptographic & Safety Risk Badges:** Highlights serialized component locks (e.g. Apple Secure Enclave display/battery pairing, Samsung camera calibration).
6. **Technical Evidence & Citations:** Direct citations and links to OEM service manuals, iFixit teardowns, and community laboratory reports.
7. **Repair Guides & Teardowns:** Step-by-step procedures with tool manifests and safety warnings.
8. **Community Requests & Voting:** Upvote needed compatibility research for new phone releases.
9. **Technician Warnings & Notes:** Post fitment alerts (e.g., ribbon cable fragility or short-circuit risks).
10. **Offline First:** All browsed catalog entries are persisted to SQLite via Room. Users can toggle **Force Offline Mode** in Settings at any time.

---

## 🧪 Testing & Verification

Unit tests and Robolectric JVM tests are located in `app/src/test/`:
- `ExampleRobolectricTest.kt`: Validates app string resources and Room DAO persistence.
- `CompatibilityEvaluationUnitTest.kt`: Validates domain logic, compatibility rule parsing, risk level categorization, and search model matching.

Run unit tests via:
```bash
gradle :app:testDebugUnitTest
```
