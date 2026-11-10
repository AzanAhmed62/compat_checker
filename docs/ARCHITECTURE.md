# Architecture

## Repository layout

```
apps/android       Kotlin/Compose client (MVVM/UDF)
apps/admin_panel   React 19 + TypeScript + Vite admin console
supabase           ordered migrations, seed, SQL tests, Edge Functions, config
scripts            database test runners (PowerShell and Bash)
docs               product, architecture, security, release, and test records
```

## Runtime topology

```
Android app ─┐
             ├── Supabase Auth / PostgREST / Storage (public anon key)
Admin console┘                 │
                               ├── PostgreSQL schema, RLS, public views, RPCs
                               ├── Edge Function: admin-csv (JWT + role check)
                               └── Edge Function: purge-pending-accounts (shared secret)
```

The clients never hold a service-role key. The admin console uses the same public API surface as the mobile app, with authorization enforced by database RLS and role checks inside functions.

## Backend

### Schema and access

- Twelve ordered migrations build the schema, identity, catalog, rules/evidence, community, reputation, social features, analytics, admin functions, hardening, account purge, and social read models.
- Tables hold source data. Public consumers read explicit-column owner-rights views: `products_public`, `rules_public`, `evidence_public`, `guides_public`, `guide_steps_public`, `requests_public`, `comments_public`, `profiles_public`, `public_stats`, and related collection views.
- RLS is enabled on user-owned and privileged tables. Public views intentionally expose only published/visible rows and never expose user IDs for votes or anonymous requests.
- Mutations run through `SECURITY DEFINER` functions with `SET search_path = ''` and schema-qualified references. Direct write policies are limited to admins where appropriate.
- The points ledger is append-only; totals are aggregates. Account roles, bans, and rule status are server-owned.

### Compatibility evaluation

`check_compatibility(product_a, product_b)` validates that both products are published, loads **only** exact-pair published rules, and applies this outcome:

```
no rules            -> unknown
any not_compatible  -> incompatible
otherwise           -> compatible
```

It does not query the graph, infer missing links, or merge similar products. The primary rule is the first item in the server's deterministic ordering. Evidence is embedded in each returned rule.

### Edge Functions

- `admin-csv`: authenticated admin CSV import/export bridge. It verifies the caller's JWT, resolves the profile role, and calls admin-only RPCs through a **caller-scoped** client so RLS/RPC role checks remain authoritative. It does not use a service-role client. It is configured with `verify_jwt = true`.
- `purge-pending-accounts`: scheduled worker guarded by `PURGE_CRON_SECRET`, invoking the service-role-only purge function. It does not accept user JWTs. It is configured with `verify_jwt = false` because the shared secret is the authentication mechanism.

Docker, the Supabase CLI, and Deno are available in this workspace. Edge Function handlers are typechecked and covered by local execution tests. A local Supabase stack with the pinned PostgreSQL image was started and reset on 2026-11-09, and live Auth/PostgREST/RLS/admin and Storage object-policy checks were exercised over HTTP; live Edge Function invocation was not. Instrumented Compose tests compile, but no usable emulator/device session has run them. See `docs/KNOWN_ISSUES.md` for current gaps.

## Android client

### Layering

```
UI (Compose screens, UDF state)
  └── ViewModels (Coroutines/StateFlow)
        └── domain repositories + models
              ├── SupabaseGateway (Auth/PostgREST)
              ├── Room cache
              └── DataStore preferences
```

- `supabase-kt` is isolated in `data/remote` and repository implementations. UI and ViewModels depend on interfaces.
- `AppContainer` provides manual constructor injection; no service locator inside screens.
- Room caches product search results and product-detail payloads for offline browsing, plus guide lists and guide-detail payloads. Schema version 2 adds brand slugs and the guide tables through an explicit migration.
- Public collections and member profiles are read through `SECURITY DEFINER` JSON RPCs (`get_collection`, `get_public_profile`) instead of direct table or view access. `list_my_collections` requires an authenticated active user and returns only the caller's collections.
- DataStore stores theme choice. AppCompat per-app locales store language; resources include English and Urdu, and the manifest declares RTL support.
- Navigation Compose owns routes and deep links. Screen ViewModels are created with explicit factories and receive only repositories.
- Notification links are parsed by a pure router into product, guide, rule, profile, or collection destinations; unsupported or malformed links never open an external browser.
- Build-time configuration uses `local.properties` or environment variables. A JWT payload containing `service_role` or an `sb_secret_` key is rejected before creating a client.

### Failure handling

- Network is not assumed. Search, product detail, guide lists, and guide details fall back to the Room cache and display an offline marker. A cache miss that cannot be served produces an explicit offline message rather than fabricated data.
- Authentication-dependent controls check the live session and prompt sign-in instead of silently changing local state.
- Server errors are surfaced as bounded text; access tokens and keys are never logged.
- Unknown compatibility has distinct copy and color from both compatible and incompatible results.

## Admin console

- React 19, TypeScript, Vite, TanStack Query, React Router, Supabase JS.
- Auth provider loads the session and the caller's `me()` payload; navigation is filtered by server-provided capabilities, while the server remains authoritative.
- Pages cover dashboard, catalog, rules/evidence and versions, reports, contributions, comments, requests, users/roles, guides, media, analytics, audit logs, and CSV tools.
- TanStack Query handles cache invalidation after mutations. Errors are normalized through a shared error formatter.

## Testing and delivery

- SQL tests run against a disposable local PostgreSQL database using a test-only Supabase shim. They are not run against production.
- Admin tests cover CSV, permission, and search-sanitization helpers with Vitest. The public `/privacy` and `/delete-account` surfaces are typechecked and built but have no live-backend UI test yet.
- Android unit tests target repositories, DTO mapping, Markdown parsing, notification routing, and structured correction payloads. Compose instrumentation tests cover the checker result states; they compile in CI but require an emulator/device to execute.
- CI is defined for database, admin, Edge Function, and Android jobs. It has not run remotely. The database CI job uses a PostgreSQL service container plus the shim runner, not `supabase start`.
- No remote Supabase project, Play signing key, or other production credential is used in this repository.

## Architectural decisions and trade-offs

- **PostgREST views over client joins:** public shapes are stable and least-privilege.
- **RPCs for multi-table operations:** atomicity and server-side authorization are easier to audit.
- **Room cache rather than offline writes:** avoids conflicting writes and moderation bypass while still supporting offline reading.
- **Manual DI:** small surface; no Hilt build cost and explicit dependency graphs.
- **Retire, don't delete, published rules:** preserves evidence and version history; rollback restores prior state.
- **Aggregate votes only:** protects voter privacy, at the cost of not exposing "who voted" even to peers.
