-- 40: administrator and moderator enforcement, bans, reports, and audit trail.
begin;

select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000001', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;

select tests.assert_int(
  (public.admin_dashboard()->'counts'->>'users_active')::integer, 4,
  'dashboard counts active users');
select tests.assert_int(
  (public.admin_dashboard()->'counts'->>'products_published')::integer, 7,
  'dashboard counts published products');
select tests.assert_int(
  (public.admin_list_users()->>'total')::integer, 4,
  'admin lists all users');
select tests.assert_int(
  (public.admin_list_users('trusted', null, null, 10, 0)->>'total')::integer, 1,
  'admin user search filters by role');
select tests.assert_int(
  (public.admin_list_users('member@compatchecker.test', null, null, 10, 0)->>'total')::integer, 1,
  'admin user search matches private email');
select tests.assert_int(
  (public.admin_list_users(null, null, null, 2, 0)->>'total')::integer, 4,
  'user pagination reports the full total');
select tests.assert_int(
  jsonb_array_length(public.admin_list_users(null, null, null, 2, 0)->'items'), 2,
  'user pagination limits rows');

-- Role changes are audited and notified.
select tests.assert_text(
  public.admin_set_user_role(
    '00000000-0000-4000-8000-000000000004', 'contributor',
    'Promote active community member')->>'role',
  'contributor', 'admin can promote a member');
select tests.assert_text(
  (select role::text from public.profiles
   where id = '00000000-0000-4000-8000-000000000004'),
  'contributor', 'role change is persisted');
reset role;
select tests.assert_true(
  exists (
    select 1 from public.notifications
    where user_id = '00000000-0000-4000-8000-000000000004'
      and type = 'role_changed' and read_at is null),
  'role change creates an unread notification');
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000001', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
select tests.assert_true(
  exists (
    select 1 from public.audit_logs
    where action = 'user.role_changed'
      and entity_id = '00000000-0000-4000-8000-000000000004'),
  'role change is audit logged');
select tests.expect_error(
  'select public.admin_set_user_role(''00000000-0000-4000-8000-000000000001'', ''trusted'', ''Demote the only admin'')',
  'P0001', 'the last active administrator cannot be demoted');
select tests.expect_error(
  'select public.admin_set_user_role(''00000000-0000-4000-8000-000000000004'', ''member'', ''no'')',
  '22023', 'role changes require a meaningful reason');
select tests.expect_error(
  'select public.admin_set_user_role(''00000000-0000-4000-8000-000000000099'', ''member'', ''Unknown user test'')',
  '23503', 'unknown users cannot be modified');

-- Bans are enforced in both the application profile and auth.users.
select tests.assert_text(
  public.admin_set_user_status(
    '00000000-0000-4000-8000-000000000004', 'banned',
    'Repeated synthetic policy violations')->>'account_status',
  'banned', 'admin can ban a user');
reset role;
select tests.assert_true(
  (select banned_until from auth.users
   where id = '00000000-0000-4000-8000-000000000004') = 'infinity'::timestamptz,
  'bans set banned_until=infinity in auth.users');
select tests.assert_int(
  (select count(*)::integer from public.profiles_public
   where id = '00000000-0000-4000-8000-000000000004'),
  0, 'banned profiles disappear from public projections');
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000004', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
select tests.expect_error(
  'select public.toggle_favorite(''10000000-0000-4000-8000-000000000001'')',
  '42501', 'banned users cannot use member RPCs');
select tests.expect_error(
  'select public.create_comment(''product'', ''10000000-0000-4000-8000-000000000001'', ''Banned comment'')',
  '42501', 'banned users cannot comment');
select tests.assert_text(public.me()->>'account_status', 'banned',
  'banned users can still inspect their own status');

reset role;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000001', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
select tests.assert_text(
  public.admin_set_user_status(
    '00000000-0000-4000-8000-000000000004', 'active',
    'Appeal accepted after review')->>'account_status',
  'active', 'admin can restore a banned account');
reset role;
select tests.assert_true(
  (select banned_until from auth.users
   where id = '00000000-0000-4000-8000-000000000004') is null,
  'unban clears auth.users.banned_until');
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000001', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
select tests.expect_error(
  'select public.admin_set_user_status(''00000000-0000-4000-8000-000000000001'', ''suspended'', ''Self suspension'')',
  'P0001', 'administrators cannot suspend themselves');

-- Reports and atomic moderation.
reset role;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000004', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
select tests.assert_true(
  public.submit_report(
    'comment', '70000000-0000-4000-8000-000000000001',
    'harassment', 'Synthetic moderation test') is not null,
  'member can report a comment');

reset role;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000002', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
select tests.assert_int(
  (public.admin_list_reports('open', 10, 0)->>'total')::integer, 1,
  'trusted reviewer can list open reports');
select tests.expect_error(
  'select public.admin_set_user_role(''00000000-0000-4000-8000-000000000004'', ''trusted'', ''Trusted cannot promote'')',
  '42501', 'trusted reviewers cannot change roles');
select tests.expect_error(
  'select public.admin_dashboard()', '42501',
  'trusted reviewers cannot open the admin dashboard');
select tests.expect_error(
  'select public.admin_rollback_rule(''20000000-0000-4000-8000-000000000001'', 1, ''Trusted cannot rollback'')',
  '42501', 'trusted reviewers cannot roll back rules');

reset role;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000001', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
select tests.assert_text(
  public.admin_resolve_report(
    (select id from public.reports where target_id = '70000000-0000-4000-8000-000000000001'),
    'resolved', 'Confirmed synthetic abuse', 'hide_comment')->>'status',
  'resolved', 'admin can resolve a report with an action');
select tests.assert_text(
  (select status::text from public.comments
   where id = '70000000-0000-4000-8000-000000000001'),
  'hidden', 'hide_comment action hides the comment atomically');
select tests.assert_int(
  (select count(*)::integer from public.comments_public), 1,
  'hidden comments disappear from public projections');
select tests.assert_true(
  exists (
    select 1 from public.points_ledger
    where user_id = '00000000-0000-4000-8000-000000000004'
      and reason = 'report_accepted' and delta = 5),
  'accepted reports award points exactly once');
select tests.assert_true(
  exists (
    select 1 from public.audit_logs
    where action = 'report.resolved'
      and entity_id = (select id from public.reports
                       where target_id = '70000000-0000-4000-8000-000000000001')),
  'report resolution is audited');
reset role;
select tests.assert_true(
  exists (
    select 1 from public.notifications
    where user_id = '00000000-0000-4000-8000-000000000004'
      and type = 'report_update'),
  'report resolution notifies the reporter');
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000001', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
select tests.assert_true(
  public.admin_moderate_comment(
    '70000000-0000-4000-8000-000000000001', 'visible',
    'Restored after synthetic appeal'),
  'moderator can restore a hidden comment');
select tests.assert_int(
  (select count(*)::integer from public.comments_public), 2,
  'restored comments return to the public projection');
select tests.expect_error(
  'select public.admin_moderate_comment(''70000000-0000-4000-8000-000000000001'', ''visible'', ''x'')',
  '22023', 'comment moderation requires a reason');

-- Request triage.
insert into public.products (
  id, slug, name, brand_id, category_id, status, created_by
)
select
  '10000000-0000-4000-8000-0000000000fe', 'draft-request-fulfilment',
  'Draft fulfilment fixture', b.id, c.id, 'draft',
  '00000000-0000-4000-8000-000000000001'
from public.brands b, public.categories c
where b.slug = 'sample-electronics' and c.slug = 'other-components';
select tests.assert_text(
  public.admin_update_request(
    '50000000-0000-4000-8000-000000000001', 'planned', null)->>'status',
  'planned', 'moderator can plan a request');
select tests.assert_int(
  (select count(*)::integer from public.product_requests
   where id = '50000000-0000-4000-8000-000000000001' and status = 'planned'),
  1, 'request status is persisted');
select tests.assert_text(
  public.admin_update_request(
    '50000000-0000-4000-8000-000000000001', 'fulfilled',
    '10000000-0000-4000-8000-000000000001')->>'fulfilled_product_id',
  '10000000-0000-4000-8000-000000000001',
  'fulfilled requests record the fulfilling product');
select tests.expect_error(
  'select public.admin_update_request(''50000000-0000-4000-8000-000000000001'', ''declined'', ''10000000-0000-4000-8000-0000000000fe'')',
  '23503', 'draft products cannot fulfil requests');

-- Analytics is aggregate-only and admin-only.
select tests.assert_int(
  (public.admin_analytics(30)->'totals'->>'events')::integer, 4,
  'admin analytics counts seeded events');
select tests.assert_int(
  (public.admin_analytics(30)->'totals'->>'unique_sessions')::integer, 4,
  'admin analytics counts distinct hashed sessions');
select tests.assert_true(
  not (public.admin_analytics(30)->'totals' ? 'session_hash'),
  'admin analytics never exposes session hashes');
select tests.assert_int(
  jsonb_array_length(public.admin_analytics(30)->'top_searches'), 1,
  'admin analytics aggregates search terms');

-- Administrative mutations are append-only audit entries.
select tests.assert_true(
  (select count(*) from public.audit_logs
   where action in ('user.role_changed', 'user.status_changed',
                    'report.resolved', 'comment.moderated', 'request.updated')) >= 7,
  'administrative mutations are audit logged');

rollback;
