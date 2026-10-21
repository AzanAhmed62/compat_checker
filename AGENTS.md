# Repository Agent Instructions

## Mission

Maintain Compat Checker as an evidence-backed compatibility platform. Never invent compatibility, evidence, credentials, test results, integrations, or deployment status.

## First steps

1. Read `docs/REPOSITORY_AUDIT.md`, `docs/PRODUCT_SPEC.md`, `docs/ARCHITECTURE.md`, and `NEXT_TASK.md`.
2. Inspect `git status` and the current diff before editing. The original demo is preserved at baseline commit `ac78fbc`.
3. Use `scripts/db_test.ps1` for SQL verification. Docker and Supabase CLI are not available here.
4. Do not connect to, reset, or mutate any remote Supabase project without explicit owner authorization.

## Non-negotiables

- Compatibility comes only from explicit published rules. `not_compatible_with` always wins. Unknown means unknown.
- Keep public data behind explicit-column views and published/visible filters.
- Keep vote details private; expose aggregates only.
- Keep service-role keys server-side. Client builds get only the public anon/publishable key.
- Do not weaken RLS or `search_path = ''` for convenience.
- Do not add client-trusted roles, points, moderation status, or verification.
- Do not run `seed.sql` in production.
- Do not modify applied migrations. Add a new ordered migration and tests.
- Do not delete rule evidence; retire/rollback instead.
- Use `npm.cmd` on this Windows machine.
- Run tests and report exact observed results. If something cannot run, say so.

## Verification commands

Database:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/db_test.ps1 -Keep
```

Admin:

```powershell
cd apps/admin_panel
npm.cmd run typecheck
npm.cmd test
npm.cmd run build
```

Android:

```powershell
cd apps/android
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12'
$env:GRADLE_USER_HOME='D:\DEV\build-cache\gradle-home'
$env:TEMP='D:\DEV\build-cache\temp'
$env:TMP='D:\DEV\build-cache\temp'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

## Style

- Keep repository patterns: ordered SQL files, React query/provider structure, Kotlin package `com.compatchecker.app`, repository interfaces in `domain`, Supabase/Room in `data`, state in ViewModels.
- Changes should be focused and include tests where behavior is security- or compatibility-sensitive.
- Update docs when behavior, dependencies, commands, or deployment steps change.
- Preserve user changes. If you find unfamiliar edits, inspect them before overwriting.

## Definition of done

- The relevant tests/builds pass, or the failure/blocker is documented.
- Public/private access boundaries are unchanged or strengthened.
- No secrets or generated artifacts are staged.
- Docs and `NEXT_TASK.md` reflect reality.
- Report exact commands and counts; use "not run" when applicable.
