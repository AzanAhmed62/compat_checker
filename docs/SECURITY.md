# Security

## Trust boundaries

- The Android app and admin console are untrusted clients. They hold only the public anon/publishable key.
- PostgreSQL RLS, owner-rights views, and `SECURITY DEFINER` functions are the authorization boundary.
- Edge Functions are separate trust boundaries. `admin-csv` verifies a user JWT and admin role. `purge-pending-accounts` verifies a high-entropy shared secret and is intended for a scheduler.
- Edge Function CORS allows only exact origins from `ADMIN_ORIGIN` (comma-separated). A wildcard is ignored, and unset configuration falls back to local development origins only. Requests with a disallowed browser `Origin` are rejected before authentication.
- The public `/privacy` and `/delete-account` routes are served by the admin web app but are not admin routes. Deletion calls the same authenticated user RPCs as the app; no service-role client is exposed.
- Supabase service-role keys exist only in server-side Edge Function secrets or the local test environment. They must never enter a client bundle, screenshot, log, or repository file.

## Key handling

- Android reads `SUPABASE_URL` and `SUPABASE_ANON_KEY` at build time from `local.properties` or environment variables.
- `AppConfig` rejects:
  - non-HTTPS URLs in release builds (debug permits HTTP only for emulator/local hosts)
  - keys prefixed `sb_secret_`
  - JWTs whose decoded payload declares `service_role`
- The main network-security config denies all cleartext. Only the debug source set overrides it for `10.0.2.2`, `localhost`, and `127.0.0.1`.
- Admin environment variables use Vite's `VITE_` prefix and must contain only the public key. `VITE_DATA_CONTROLLER` and `VITE_SUPPORT_EMAIL` are public deployment metadata, never secrets.
- Release signing material is read from untracked local properties/environment and is required for release tasks. Keystores and passwords are not committed.
- `.env`, `local.properties`, keystores, and build output are ignored.

## Database authorization

- Every user-owned/privileged table has RLS enabled.
- Public views run with owner rights and explicitly list columns; filters restrict them to published products, published rules/guides, visible comments, active profiles, and aggregate vote counts.
- `requests_public` and vote projections do not expose `created_by` or `user_id`.
- `profiles` is not publicly readable as a table; `profiles_public` exposes only active profiles and safe fields.
- Direct writes are denied except where an explicit admin policy exists. Member writes go through RPCs.
- Roles, account status, verification, confidence, points, and moderation state are server-owned and never accepted from client-controlled profile updates.
- `SECURITY DEFINER` functions use `SET search_path = ''` and schema-qualified identifiers. Public execute grants are explicit; internal helpers are revoked.
- `get_collection` returns a public collection or one owned by the caller; private collections are `null` for other members and anonymous callers. `list_my_collections` requires an active authenticated user. Public collection detail only includes published products.
- `get_public_profile` returns the explicit `profiles_public` projection plus aggregate follower counts, the caller's own follow state, and approved contribution summaries; it never exposes email or private user IDs.
- `toggle_follow` rejects self-follows, requires an active target profile, enforces a rate limit, and creates a notification for the followee.
- Rate limits are enforced for comments, requests, votes, reports, contributions, favorites, and profile deletion requests.
- `assert_active_user()` blocks suspended, banned, deleted, and deletion-pending accounts from writes.

## Compatibility integrity

- Rules must be explicit rows. Search similarity is not used as a compatibility signal.
- `check_compatibility` only reads exact pair rules with `published` status.
- Any `not_compatible_with` rule forces an `incompatible` outcome; weaker positive claims cannot override it.
- Evidence and rule provenance are preserved through versioning and retirement rather than destructive deletion.
- Public rule/evidence views expose published records only. Drafts and rejected rules remain admin-only.

## Privacy

- Public vote data is aggregate. Voter rows are protected by RLS and accessible only to the voter or admins.
- Follower rows are protected by RLS; public profile responses expose aggregate counts and the caller's own follow state only.
- Requests are anonymous by default at the data-model level; public projections omit creators.
- Comments expose display name, username, role, and avatar, not email or user IDs.
- Profile email lives in `auth.users`, not `profiles`.
- Reports and moderation notes are admin-only.
- Account deletion hides the profile immediately and schedules a delayed purge. Cancellation is limited to the pending state.
- Analytics events are sanitized and aggregate-facing; admin-only access.
- The app does not log credentials, tokens, or full server responses.

## Input validation

- Server-side length checks for names, summaries, comments, report details, guides, requests, and profile fields.
- Slugs and usernames have strict character/length constraints.
- Avatar and evidence URLs require HTTP(S); evidence rejects example domains; avatars require HTTPS.
- CSV upload size/row limits are enforced in the Edge/import path. CSV exports prefix spreadsheet formula characters (`=`, `+`, `-`, `@`, tab, carriage return) so exported text is not evaluated as a formula.
- JSON contribution payloads validate required keys, types, enums, ownership, and evidence presence.
- Admin actions validate role/status transitions and require reasons where recorded.

## Moderation and abuse

- Comments have moderation status and an audit trail.
- Reports have a unique open report per reporter/target to reduce spam.
- Comment, report, request, vote, and contribution RPCs apply rate limits.
- Suspended/banned users cannot write even if they retain a valid JWT.
- Admin role changes and account status changes are audited and notify affected users.
- Account purge is delayed, service-role-only, and bounded per invocation.

## Known residual risks

- Live Auth/PostgREST checks were performed against a local Supabase stack on 2026-11-09, including password sign-in, anonymous/public views, RLS denial on private tables, collection/follow lifecycles, evidence URL validation, vote privacy, admin-only RPCs, and Storage object upload/read/delete policies by role. Live Edge Function invocation and deployed production headers were not exercised. These remain deployment verification items, not claims of security.
- SQL tests still use the plain-PostgreSQL shim, and Edge Function tests use in-process handler fakes; neither is a substitute for a hosted environment.
- No penetration test, dependency audit enforcement, rate-limit load test, or production monitoring has been performed.
- A compromised admin account can perform all admin actions by design; MFA and alerting are deployment requirements.
- Client-side secrets are never acceptable. If a service-role key is ever exposed, rotate it immediately and review logs.

## Incident response

1. Revoke/rotate the exposed key or session.
2. Disable the affected account or function.
3. Preserve audit logs and relevant request logs.
4. Notify affected users if personal data or integrity is implicated.
5. Fix the root cause, add a regression test, and document the incident.
6. Never reset or modify a remote Supabase project without explicit owner authorization.
