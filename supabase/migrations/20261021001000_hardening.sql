-- 010: least-privilege grants, public API surface, and RLS verification.
-- This migration runs last. It revokes Supabase's default wide grants and then
-- re-grants only the documented surface.

revoke all on all tables in schema public from anon, authenticated;
revoke all on all sequences in schema public from anon, authenticated;
revoke create on schema public from public, anon, authenticated;

grant usage on schema public to anon, authenticated;

-- Public projections (owner-rights views with explicit column allow-lists).
grant select on table
  public.brands_public,
  public.categories_public,
  public.collection_items_public,
  public.collections_public,
  public.comment_counts,
  public.comments_public,
  public.evidence_public,
  public.follow_counts,
  public.guide_steps_public,
  public.guides_public,
  public.products_public,
  public.profiles_public,
  public.public_stats,
  public.requests_public,
  public.rules_public
to anon, authenticated;

-- Aggregate vote counters contain no user identifiers.
grant select on table public.rule_vote_counts, public.request_vote_counts
to anon, authenticated;

-- Authenticated base-table access is always narrowed by RLS. Admin-only write
-- tables still grant DML so the admin console can use ordinary PostgREST CRUD;
-- the policies above reject non-admin callers.
-- Profile columns are granted individually so moderation fields never leak to
-- other signed-in users, even though the row is publicly visible.
grant select (
  id, username, display_name, bio, avatar_url, role, account_status, locale,
  created_at, updated_at
) on table public.profiles to authenticated;
grant select on table public.audit_logs to authenticated;
grant select on table public.rule_versions to authenticated;

grant select, insert, update, delete on table
  public.categories,
  public.brands,
  public.products,
  public.product_aliases,
  public.product_specs,
  public.product_media,
  public.guides,
  public.guide_steps,
  public.media_assets
to authenticated;

grant select, insert, update on table public.compatibility_rules to authenticated;
grant select, insert, update, delete on table public.rule_evidence to authenticated;

grant select on table public.favorites to authenticated;
grant select on table public.rule_votes to authenticated;
grant select on table public.product_requests to authenticated;
grant select on table public.request_votes to authenticated;
grant select on table public.comments to authenticated;
grant select on table public.reports to authenticated;
grant select on table public.contributions to authenticated;
grant select on table public.points_ledger to authenticated;
grant select on table public.follows to authenticated;
grant select on table public.notifications to authenticated;
grant select on table public.collections to authenticated;
grant select on table public.collection_items to authenticated;

-- RLS policies evaluate private.is_admin() in the caller's security context.
-- Therefore authenticated and anon (for defence in depth) need USAGE on the
-- private schema and EXECUTE on that single helper. No other private helper is
-- callable by clients.
grant usage on schema private to anon, authenticated;

do $revoke_private$
declare
  r record;
begin
  for r in
    select p.oid::regprocedure as signature
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'private'
  loop
    execute format('revoke all on function %s from public, anon, authenticated', r.signature);
  end loop;
end;
$revoke_private$;

grant execute on function private.is_admin() to anon, authenticated;

-- Revoke Supabase's default EXECUTE grant from every public function that is
-- not owned by an extension, then grant the intended surface explicitly.
do $revoke_functions$
declare
  r record;
begin
  for r in
    select p.oid::regprocedure as signature
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and not exists (
        select 1 from pg_depend d
        where d.objid = p.oid and d.deptype = 'e'
      )
  loop
    execute format('revoke all on function %s from public, anon, authenticated', r.signature);
  end loop;
end;
$revoke_functions$;

-- Public read/compute RPCs.
grant execute on function public.search_products(text, text, text, integer, integer)
  to anon, authenticated;
grant execute on function public.get_product_detail(uuid) to anon, authenticated;
grant execute on function public.get_rule_detail(uuid) to anon, authenticated;
grant execute on function public.check_compatibility(uuid, uuid) to anon, authenticated;
grant execute on function public.product_graph(uuid, integer) to anon, authenticated;
grant execute on function public.get_guide_detail(uuid) to anon, authenticated;
grant execute on function public.get_public_profile(uuid) to anon, authenticated;
grant execute on function public.reputation_leaderboard(integer) to anon, authenticated;
grant execute on function public.public_trending_products(integer) to anon, authenticated;
grant execute on function public.track_event(text, text, uuid, text, jsonb)
  to anon, authenticated;

-- Authenticated member RPCs.
grant execute on function public.update_my_profile(text, text, text, text, text)
  to authenticated;
grant execute on function public.me() to authenticated;
grant execute on function public.my_reputation() to authenticated;
grant execute on function public.cast_rule_vote(uuid, text) to authenticated;
grant execute on function public.remove_rule_vote(uuid) to authenticated;
grant execute on function public.toggle_favorite(uuid) to authenticated;
grant execute on function public.list_my_favorites() to authenticated;
grant execute on function public.create_product_request(text, text, uuid) to authenticated;
grant execute on function public.toggle_request_vote(uuid) to authenticated;
grant execute on function public.create_comment(text, uuid, text, uuid) to authenticated;
grant execute on function public.delete_my_comment(uuid) to authenticated;
grant execute on function public.submit_report(text, uuid, text, text) to authenticated;
grant execute on function public.submit_contribution(public.contribution_kind, jsonb)
  to authenticated;
grant execute on function public.withdraw_contribution(uuid) to authenticated;
grant execute on function public.list_my_contributions() to authenticated;
grant execute on function public.list_my_notifications(integer, integer) to authenticated;
grant execute on function public.mark_notification_read(uuid) to authenticated;
grant execute on function public.mark_all_notifications_read() to authenticated;
grant execute on function public.toggle_follow(uuid) to authenticated;
grant execute on function public.create_collection(text, text, boolean) to authenticated;
grant execute on function public.update_collection(uuid, text, text, boolean) to authenticated;
grant execute on function public.delete_collection(uuid) to authenticated;
grant execute on function public.add_to_collection(uuid, uuid, text) to authenticated;
grant execute on function public.remove_from_collection(uuid, uuid) to authenticated;
grant execute on function public.request_account_deletion() to authenticated;
grant execute on function public.cancel_account_deletion() to authenticated;

-- Admin/moderation RPCs. Each function performs its own role check.
grant execute on function public.admin_dashboard() to authenticated;
grant execute on function public.admin_list_users(text, text, text, integer, integer)
  to authenticated;
grant execute on function public.admin_set_user_role(uuid, public.app_role, text)
  to authenticated;
grant execute on function public.admin_set_user_status(uuid, text, text) to authenticated;
grant execute on function public.admin_list_contributions(text, text, integer, integer)
  to authenticated;
grant execute on function public.admin_review_contribution(uuid, text, text)
  to authenticated;
grant execute on function public.admin_list_reports(text, integer, integer)
  to authenticated;
grant execute on function public.admin_resolve_report(uuid, text, text, text)
  to authenticated;
grant execute on function public.admin_moderate_comment(uuid, text, text)
  to authenticated;
grant execute on function public.admin_update_request(uuid, public.request_status, uuid)
  to authenticated;
grant execute on function public.admin_rule_versions(uuid) to authenticated;
grant execute on function public.admin_rollback_rule(uuid, integer, text)
  to authenticated;
grant execute on function public.admin_bulk_import_products(jsonb, boolean)
  to authenticated;
grant execute on function public.admin_analytics(integer) to authenticated;

-- Runtime check: every public table must have RLS enabled.
do $rls_check$
declare
  r record;
begin
  for r in
    select c.relname
    from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public'
      and c.relkind = 'r'
      and not c.relrowsecurity
  loop
    raise exception 'RLS is not enabled on public.%', r.relname;
  end loop;
end;
$rls_check$;

comment on schema public is
  'Public API schema. Base-table access is RLS-narrowed; public reads use views/RPCs.';
