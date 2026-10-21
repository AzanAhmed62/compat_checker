# Data Model

Migrations in `supabase/migrations` are the source of truth. The summary below describes the production model, not the test-only shim.

## Identity and governance

| Table / object | Purpose | Key protections |
|---|---|---|
| `auth.users` | Supabase identity records | Managed by Supabase Auth |
| `profiles` | Public identity extension keyed to `auth.users` | Trigger-created; role/status not client-writable; RLS self/admin plus active public fields |
| `audit_logs` | Append-only actor/action/entity history | Admin select only; writes via triggers/functions |
| `points_ledger` | Append-only reputation entries | Deduplicated by `dedupe_key`; totals are aggregates |
| `notifications` | User-owned notifications | RLS own-row select; mutations through RPCs |

Enums: `app_role` (`member`, `contributor`, `trusted`, `admin`), `account_status` (`active`, `suspended`, `banned`, `deletion_pending`, `deleted`).

Reputation tiers are derived from summed ledger deltas. Clients cannot award points.

## Catalog

| Table | Purpose |
|---|---|
| `categories` | Public taxonomy with display order and visibility |
| `brands` | Brand registry |
| `products` | Catalog entries with lifecycle status, search document, identifiers, specs, aliases, and media |
| `product_specs` | Ordered label/value/unit rows with critical flags |
| `product_aliases` | Alternate names and model identifiers used by search |
| `product_media` | Approved media paths with alt text and ordering |

Product statuses: `draft`, `published`, `archived`. Only published rows appear in public views and search.

`products_public` exposes an explicit allow-list: catalog fields, brand/category names and slugs, aliases, specs, and approved media. It never exposes authors, moderation fields, or drafts.

## Compatibility knowledge

| Table | Purpose |
|---|---|
| `compatibility_rules` | Directed relationship between two products, confidence, verification, risk, summaries, lifecycle |
| `rule_evidence` | Source records for a rule |
| `rule_votes` | One confirm/dispute per user per rule |
| `rule_vote_counts` | Aggregate view/table used by public projections |

Rule statuses: `draft`, `pending_review`, `published`, `rejected`, `retired`.

Relationships: `fits`, `works_with`, `same_as`, `variant_of`, `not_compatible_with`.

Verification: `official`, `community_verified`, `needs_verification`, `experimental`.

Risk: `none`, `low`, `moderate`, `high`, `critical`.

**Outcome invariant:** exact-pair published rules only. Any `not_compatible_with` wins. No rules means `unknown`.

Rule edits create version history. Deletion is represented as retirement; `admin_rollback_rule` restores a prior version while preserving audit records.

## Community

| Table | Purpose |
|---|---|
| `favorites` | Per-user product favorites, unique pair |
| `product_requests` | Public, anonymous-by-default requests with status |
| `request_votes` | Private voter rows; public counts are aggregate-only |
| `request_vote_counts` | Aggregate vote totals |
| `comments` | Visible/hidden/deleted comments on product, rule, or guide |
| `reports` | Moderation reports with unique open report per reporter/target |
| `contributions` | Structured JSON contributions with review state |

Contribution kinds: `new_product`, `product_edit`, `new_rule`, `evidence`, `guide`. Required keys and evidence URL rules are validated server-side. `new_rule` requires at least one evidence record and a real HTTP(S) source, not an example domain.

## Social and content

| Table | Purpose |
|---|---|
| `guides` | Published editorial guides with product association and author |
| `guide_steps` | Ordered steps with optional media |
| `follows` | Follower graph; only aggregate counts are public |
| `notifications` | User-targeted events |
| `collections` | Owner collections with public/private flag |
| `collection_items` | Products in a collection, maximum enforced in RPC |
| `media_assets` | Admin-managed storage registry for `media` and `evidence` buckets |

Public guide views expose published rows, author display name only when active, and ordered steps. Evidence bucket objects are private; public media bucket objects are readable.

## Analytics

`analytics_events` stores sanitized event data. `track_event` accepts bounded names/properties and enforces rate limits. Admin analytics returns aggregates. `public_trending_products` uses aggregate activity only and does not expose individual sessions.

## Public API functions

- `search_products(p_query, p_category, p_brand, p_limit, p_offset)` — ranked, paged, published-only.
- `get_product_detail(p_product_id)` — product projection plus published rules, guides, comment count, caller favorite state.
- `check_compatibility(p_product_a, p_product_b)` — exact-pair result with evidence and incompatibility precedence.
- `get_rule_detail(p_rule_id)` — rule, all evidence, caller vote.
- `get_guide_detail(p_guide_id)` — published guide, ordered steps, comment count.
- `product_graph(p_product_id, p_depth)` — exploratory graph, depth capped, not a compatibility verdict.
- `public_trending_products(p_limit)` — aggregate popularity.
- `public_stats` view — counts only.

Member functions include favorites, request creation/voting, comments, reports, contributions, notification reads, profile update, reputation, and account deletion request/cancel. Admin functions cover dashboard, users, roles/status, moderation, contributions, requests, rules/versions/rollback, CSV import, and analytics.

## Storage

Buckets:

- `media`: public read, admin write, 10 MiB limit, JPEG/PNG/WebP.
- `evidence`: private, admin-only, 20 MiB limit, PDF/JPEG/PNG.

Storage policies are created only when `storage.objects` exists; the SQL test shim handles this conditionally.

## Account deletion

`request_account_deletion()` marks the profile `deletion_pending` and hides it from public views. `cancel_account_deletion()` restores active state while pending. The delayed purge worker runs `purge_pending_accounts` after the retention window and is callable only by the service role. The Edge function is guarded by `PURGE_CRON_SECRET`.

## Indexing and integrity

- Unique indexes for aliases, votes, favorites, open requests per user/title, one open report per reporter/target, collection membership, and ledger deduplication.
- Foreign keys use deliberate cascade/set-null behavior to preserve evidence where appropriate.
- Check constraints enforce lengths, valid enum-like values, slug shapes, HTTPS avatars, and bounded payloads.
- Partial indexes cover published/live rows and moderator queues.
