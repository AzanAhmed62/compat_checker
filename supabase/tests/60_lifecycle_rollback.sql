-- 60: account lifecycle, rule history/rollback, media, guides, and CSV import.
begin;

-- ---------------------------------------------------------------------------
-- Account lifecycle
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000004', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;

select tests.assert_int(
  (select count(*)::integer from public.comments_public), 2,
  'member comments are public before deletion');
select tests.assert_text(
  public.request_account_deletion()->>'status', 'deletion_pending',
  'member requests deletion');
select tests.assert_int(
  (select count(*)::integer from public.comments_public), 1,
  'deletion-pending accounts disappear from public comments');
select tests.assert_int(
  (select count(*)::integer from public.follow_counts
   where user_id = auth.uid()), 0,
  'deletion-pending accounts disappear from follow counts');
select tests.assert_int(
  (select count(*)::integer from public.follows), 1,
  'lifecycle changes do not corrupt the follower graph');
select tests.assert_true(
  public.cancel_account_deletion(),
  'deletion-pending accounts can cancel');
select tests.assert_int(
  (select count(*)::integer from public.comments_public), 2,
  'cancelling restores public content');
select tests.expect_error(
  'select public.cancel_account_deletion()', 'P0001',
  'active accounts have no deletion to cancel');

-- ---------------------------------------------------------------------------
-- Rule history and rollback
-- ---------------------------------------------------------------------------
reset role;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000001', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;

select tests.assert_int(
  jsonb_array_length(public.admin_rule_versions(
    '20000000-0000-4000-8000-000000000001')->'items'), 2,
  'seeded rule has insert and publication snapshots');
select tests.assert_int(
  (public.admin_rule_versions(
    '20000000-0000-4000-8000-000000000001')->'items'->0->>'version')::integer, 2,
  'newest rule version is listed first');

update public.compatibility_rules
set summary = 'Admin-edited synthetic summary for rollback testing'
where id = '20000000-0000-4000-8000-000000000001';
select tests.assert_int(
  jsonb_array_length(public.admin_rule_versions(
    '20000000-0000-4000-8000-000000000001')->'items'), 3,
  'direct admin edits create a new snapshot');
select tests.assert_text(
  (select summary from public.compatibility_rules
   where id = '20000000-0000-4000-8000-000000000001'),
  'Admin-edited synthetic summary for rollback testing',
  'admin edit is applied');

select tests.assert_int(
  (public.admin_rollback_rule(
    '20000000-0000-4000-8000-000000000001', 2,
    'Restore reviewed seed wording')->>'new_version')::integer, 4,
  'rollback creates a new version');
select tests.assert_text(
  (select summary from public.compatibility_rules
   where id = '20000000-0000-4000-8000-000000000001'),
  'SEED DATA: synthetic display-fit claim for workflow testing, not a real compatibility finding.',
  'rollback restores the snapshotted wording');
select tests.assert_text(
  (select status::text from public.compatibility_rules
   where id = '20000000-0000-4000-8000-000000000001'),
  'published', 'rollback to a published snapshot keeps the rule published');
select tests.assert_true(
  (select change_reason from public.rule_versions
   where rule_id = '20000000-0000-4000-8000-000000000001' and version = 4)
  like 'Rollback to v2:%',
  'rollback reason is recorded on the new snapshot');
select tests.assert_true(
  exists (
    select 1 from public.audit_logs
    where action = 'rule.rolled_back'
      and entity_id = '20000000-0000-4000-8000-000000000001'),
  'rollback is audit logged');
select tests.expect_error(
  'select public.admin_rollback_rule(''20000000-0000-4000-8000-000000000001'', 999, ''Missing version'')',
  '23503', 'unknown versions cannot be restored');
select tests.expect_error(
  'select public.admin_rollback_rule(''20000000-0000-4000-8000-000000000001'', 2, ''x'')',
  '22023', 'rollback requires a meaningful reason');

-- ---------------------------------------------------------------------------
-- CSV-style bulk import
-- ---------------------------------------------------------------------------
select tests.assert_int(
  (public.admin_bulk_import_products(
    '[{"name":"Imported One","slug":"imported-one","brand_slug":"sample-electronics",'
    '"category_slug":"other-components","aliases":["IO"]},'
    '{"name":"Imported Two","slug":"imported-two","brand_slug":"generic-oem",'
    '"category_slug":"batteries"}]'::jsonb, false)->>'imported')::integer,
  2, 'admin imports two draft products');
select tests.assert_int(
  (select count(*)::integer from public.products
   where slug in ('imported-one', 'imported-two') and status = 'draft'), 2,
  'imported products are drafts by default');
select tests.assert_int(
  (select count(*)::integer from public.product_aliases a
   join public.products p on p.id = a.product_id
   where p.slug = 'imported-one'), 1,
  'imported aliases are created');

select tests.assert_int(
  (public.admin_bulk_import_products(
    '[{"name":"Imported Published","slug":"imported-published",'
    '"brand_slug":"sample-electronics","category_slug":"cameras"}]'::jsonb,
    true)->>'imported')::integer,
  1, 'admin can publish during import');
select tests.assert_text(
  (select status::text from public.products where slug = 'imported-published'),
  'published', 'published import is visible to the catalog');

select tests.expect_error(
  'select public.admin_bulk_import_products(''[]''::jsonb, false)',
  '22023', 'empty imports are rejected');
select tests.expect_error(
  'select public.admin_bulk_import_products(''{"not":"an array"}''::jsonb, false)',
  '22023', 'non-array imports are rejected');
select tests.expect_error(
  'select public.admin_bulk_import_products(''[{"name":"Bad Row","slug":"bad-row","brand_slug":"no-such-brand","category_slug":"smartphones"}]''::jsonb, false)',
  '22023', 'unknown brands are rejected with a row error');
select tests.expect_error(
  'select public.admin_bulk_import_products('
  '''[{"name":"Carry Brand","slug":"carry-brand","brand_slug":"sample-electronics","category_slug":"smartphones"},'
  '{"name":"Missing Brand","slug":"missing-brand","category_slug":"smartphones"}]''::jsonb, false)',
  '22023', 'imports do not leak brand state between rows');
select tests.assert_int(
  (select count(*)::integer from public.products where slug in ('carry-brand', 'missing-brand')),
  0, 'failed imports are atomic and leave no rows');
select tests.expect_error(
  'select public.admin_bulk_import_products(''[{"name":"Dup","slug":"aurora-x1","brand_slug":"sample-electronics","category_slug":"smartphones"}]''::jsonb, false)',
  '22023', 'duplicate slugs fail the whole import');
select tests.assert_int(
  (select count(*)::integer from public.products where slug = 'aurora-x1'), 1,
  'failed duplicate import creates no product');

-- ---------------------------------------------------------------------------
-- Media registry
-- ---------------------------------------------------------------------------
insert into public.product_media (
  product_id, storage_path, alt_text, status, sort_order, created_by
)
values (
  '10000000-0000-4000-8000-000000000001',
  'products/aurora-x1/front.webp', 'Synthetic product image',
  'approved', 1, '00000000-0000-4000-8000-000000000001'
);
select tests.assert_text(
  public.search_products('Aurora X1')->'items'->0->>'image_path',
  'products/aurora-x1/front.webp',
  'approved media is projected through search');

-- ---------------------------------------------------------------------------
-- Guides are admin-managed and publish atomically with steps
-- ---------------------------------------------------------------------------
insert into public.guides (slug, title, summary, body_md, difficulty, status, created_by)
values (
  'admin-synthetic-guide', 'Admin synthetic guide',
  'A local-only guide created by the admin test.',
  '# Admin guide

This synthetic guide exists only for tests and is not repair advice.',
  'easy', 'draft', '00000000-0000-4000-8000-000000000001'
);
insert into public.guide_steps (guide_id, position, title, body)
values (
  (select id from public.guides where slug = 'admin-synthetic-guide'),
  1, 'Admin step', 'Synthetic admin step.'
);
select tests.assert_int(
  (select count(*)::integer from public.guides_public
   where slug = 'admin-synthetic-guide'), 0,
  'draft guides are not public');
update public.guides
set status = 'published'
where slug = 'admin-synthetic-guide';
select tests.assert_int(
  (public.get_guide_detail(
    (select id from public.guides where slug = 'admin-synthetic-guide'))->'steps'->0->>'position')::integer,
  1, 'published guide steps remain ordered');
select tests.assert_true(
  exists (select 1 from public.audit_logs where entity_type = 'guides'),
  'guide mutations are audited');

reset role;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000003', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
select tests.expect_error(
  'select public.admin_bulk_import_products(''[{"name":"Nope","slug":"nope","brand_slug":"sample-electronics","category_slug":"smartphones"}]''::jsonb, false)',
  '42501', 'non-admins cannot import products');
select tests.expect_error(
  'select public.admin_rule_versions(''20000000-0000-4000-8000-000000000001'')',
  '42501', 'non-admins cannot inspect rule history');
select tests.expect_error(
  'insert into public.guides (slug, title, summary, body_md) '
  'values (''forbidden-guide'', ''Forbidden'', ''Synthetic forbidden guide'', ''# No'')',
  '42501', 'non-admins cannot create guides directly');
select tests.expect_error(
  'insert into public.product_media (product_id, storage_path, alt_text) '
  'values (''10000000-0000-4000-8000-000000000001'', ''x/y.webp'', ''x'')',
  '42501', 'non-admins cannot register media');

-- ---------------------------------------------------------------------------
-- Privileged delayed account purge
-- ---------------------------------------------------------------------------
reset role;
select set_config('request.jwt.claim.sub', '', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
select tests.expect_error(
  'select public.purge_pending_accounts(10)', '42501',
  'clients cannot run the account purge worker');
reset role;

insert into auth.users (id, email, raw_user_meta_data)
values (
  '00000000-0000-4000-8000-0000000000aa',
  'purge-fixture@compatchecker.test',
  '{"display_name":"Purge Fixture"}'::jsonb
);
update public.profiles
set account_status = 'deletion_pending',
    deletion_requested_at = now() - interval '31 days'
where id = '00000000-0000-4000-8000-0000000000aa';

select tests.assert_int(
  (public.purge_pending_accounts(10)->>'purged')::integer, 1,
  'service worker purges an elapsed deletion request');
select tests.assert_int(
  (select count(*)::integer from auth.users
   where id = '00000000-0000-4000-8000-0000000000aa'), 0,
  'purged auth user is removed');
select tests.assert_int(
  (select count(*)::integer from public.profiles
   where id = '00000000-0000-4000-8000-0000000000aa'), 0,
  'purged profile cascades');

rollback;
