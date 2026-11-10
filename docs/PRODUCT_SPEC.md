# Product Specification

## Product

Compat Checker is a compatibility knowledge platform for repair technicians, hardware engineers, and device owners. It answers one narrow question responsibly: **what is explicitly known about whether two hardware products work together, and what evidence supports that claim?**

The platform has three surfaces:

- **Android app** (`apps/android`): public browsing, search, evidence-backed compatibility checks, guides, requests, favorites, contributions, notifications, and account settings.
- **Admin console** (`apps/admin_panel`): role-gated catalog, rules, evidence, moderation, users, guides, media, imports, analytics, and audit history.
- **Supabase/PostgreSQL backend** (`supabase`): schema, RLS, public projections, RPCs, Edge Functions, seed data, and SQL/security tests.

## Non-negotiable principles

1. **No inferred compatibility.** A product pair is never presented as compatible because connectors, dimensions, or marketing text look similar. An explicit published rule is required.
2. **Incompatibility wins.** If any explicit `not_compatible_with` rule exists for a pair, the pair outcome is incompatible even when weaker positive rules also exist.
3. **Unknown is a valid result.** No rules means `unknown`, never a guessed answer.
4. **Evidence is visible.** Every rule carries an evidence count and source records. Users can inspect the source before acting.
5. **Public browsing requires no account.** Authentication is requested only for actions that need identity: favorites, votes, comments, requests, contributions, notifications, and account management.
6. **Vote data is aggregate-only in public responses.** Public clients do not receive voter identities.
7. **Least privilege.** Public data is exposed through explicit-column views. Privileged mutations run in owner-rights functions with locked `search_path`.
8. **No client-owned trust.** Roles, bans, points, verification levels, and rule status remain server-side.

## Compatibility semantics

Relationships: `fits`, `works_with`, `same_as`, `variant_of`, `not_compatible_with`.

Verification levels: `official`, `community_verified`, `needs_verification`, `experimental`.

Confidence is an integer from 0 to 100. The compatibility result returns:

- `known`: whether any published rule exists for the pair
- `outcome`: `compatible`, `incompatible`, or `unknown`
- all published rules for the pair, ordered with incompatibilities first and stronger verification ahead of weaker claims
- the primary rule and its evidence
- `has_incompatibility` and a server timestamp

A pairwise check never uses transitive rules or graph inference. The graph endpoint is exploratory only and is not a compatibility verdict.

## User capabilities

### Public

- Browse and search published products by query, category, and brand through server-side ranking and paging.
- Open product details with specifications, aliases, media, published rules, guides, and comment count.
- Compare any two published products and inspect evidence.
- Read published rules, evidence, guides, and public requests.
- Read visible comments and aggregate vote counts.
- Open public collections and public member profiles with aggregate reputation, follower, and contribution counts.
- Open product, guide, checker, collection, and profile deep links.

### Authenticated member

- Favorite and unfavorite products; view favorites.
- Vote to confirm or dispute a rule; view own vote and aggregate counts.
- Submit product requests and toggle request votes.
- Post comments on products, rules, and guides.
- Submit structured product-edit contributions with a JSON patch and withdraw pending contributions.
- Submit reports for spam, harassment, false claims, unsafe advice, copyright, or other reasons.
- Create public or private collections, add and remove published products, change visibility, and delete owned collections.
- Follow and unfollow other active members; view the reputation leaderboard.
- View reputation points/tier, ledger-derived contribution history, notifications, and account state.
- Request account deletion with a 30-day delayed purge, or cancel while pending.

### Trusted moderator / admin

Trusted users and admins moderate according to server capabilities. Admins have the full console:

- dashboard and analytics
- catalog CRUD and bulk CSV import/export
- rules, evidence, version history, and rollback
- reports, comments, requests, and contributions
- users, roles, and account status
- guides and media registry
- audit logs

Role or status changes are validated and recorded server-side. The Android client exposes no admin operations.

## UX requirements

- Material 3, light/dark themes, system or explicit theme choice.
- English and Urdu resources with RTL support and in-app language selection via AppCompat per-app locales.
- Bottom navigation for Browse, Checker, Guides, Requests, and Profile.
- Loading, empty, offline, and error states with retry.
- Offline cache for previously loaded catalog results, product details, guide
  lists, and guide details.
- Guide bodies rendered as a safe Markdown subset: headings, paragraphs, lists,
  quotes, fenced code, bold, italic, inline code, and absolute HTTP(S) links.
  Raw HTML is displayed as text.
- Explicit "unknown" and "incompatible" states; no green compatibility styling for unknown.
- Accessible touch targets, content descriptions, and scalable text.
- Deep links: `compatchecker://product/{id}`, `compatchecker://checker`,
  `compatchecker://guide/{id}`, `compatchecker://rule/{id}`,
  `compatchecker://collection/{id}`, `compatchecker://profile/{id}`.
- Notification links route to products, guides, rules, profiles, and
  collections; unsupported or malformed links show a visible message.

## Out of scope

- LLM or similarity-based compatibility inference.
- Commerce, payments, or parts sales.
- Medical, legal, or safety certification.
- Anonymous write access.
- Client access to service-role credentials.
- Promising that a rule is complete for every regional variant.

## Success criteria

- A public user can find a product, compare two products, and read the supporting evidence without signing in.
- A member can favorite, vote, comment, request, and contribute without any client-side role checks being trusted.
- An admin can complete routine catalog and moderation operations with audit history and pagination.
- An anonymous caller cannot read voter identities, private profiles, reports, moderation notes, or draft data.
- Tests demonstrate the incompatibility precedence rule, public projections, RLS boundaries, moderation, contributions, rollback, CSV, media, and account purge.
