-- 20: public, unauthenticated API behavior.
begin;
select set_config('request.jwt.claim.sub', '', false);
select set_config('request.jwt.claim.role', 'anon', false);
set role anon;

select tests.assert_int((public.search_products('Aurora')->>'total')::integer, 7,
  'search finds all synthetic products');
select tests.assert_true((public.search_products('Auroa')->>'total')::integer > 0,
  'typo-tolerant search finds Aurora');
select tests.assert_int(
  (public.search_products('', 'smartphones', null, 20, 0)->>'total')::integer, 2,
  'category filter');
select tests.assert_int(
  (public.search_products('', null, 'sample-electronics', 20, 0)->>'total')::integer, 7,
  'brand filter');

select tests.assert_int(
  jsonb_array_length(public.search_products('', null, null, 2, 0) -> 'items'), 2,
  'search pagination limit');
select tests.assert_int(
  (public.search_products('', null, null, 2, 0)->>'total')::integer, 7,
  'search total is independent of page size');

select tests.assert_text(
  public.check_compatibility(
    '10000000-0000-4000-8000-000000000002',
    '10000000-0000-4000-8000-000000000001'
  )->>'outcome',
  'compatible',
  'explicit display fit returns compatible');
select tests.assert_text(
  public.check_compatibility(
    '10000000-0000-4000-8000-000000000002',
    '10000000-0000-4000-8000-000000000001'
  )->'primary_rule'->>'relationship',
  'fits',
  'primary relationship is explicit');
select tests.assert_text(
  public.check_compatibility(
    '10000000-0000-4000-8000-000000000001',
    '10000000-0000-4000-8000-000000000005'
  )->>'outcome',
  'incompatible',
  'explicit negative rule wins');
select tests.assert_true(
  (public.check_compatibility(
    '10000000-0000-4000-8000-000000000001',
    '10000000-0000-4000-8000-000000000005'
  )->>'has_incompatibility')::boolean,
  'incompatibility flag is true');
select tests.assert_text(
  public.check_compatibility(
    '10000000-0000-4000-8000-000000000005',
    '10000000-0000-4000-8000-000000000001'
  )->>'outcome',
  'incompatible',
  'symmetric rule is found in reverse orientation');
select tests.assert_text(
  public.check_compatibility(
    '10000000-0000-4000-8000-000000000001',
    '10000000-0000-4000-8000-000000000007'
  )->>'outcome',
  'unknown',
  'draft rule never leaks into public evaluation');
select tests.assert_true(
  not (public.check_compatibility(
    '10000000-0000-4000-8000-000000000001',
    '10000000-0000-4000-8000-000000000007'
  )->>'known')::boolean,
  'unknown result is explicit');
select tests.assert_text(
  public.check_compatibility(
    '10000000-0000-4000-8000-000000000001',
    '10000000-0000-4000-8000-000000000001'
  )->>'outcome',
  'unknown',
  'same product is not synthesized into compatibility');

select tests.assert_int(
  jsonb_array_length(public.get_product_detail(
    '10000000-0000-4000-8000-000000000001'
  )->'rules'),
  4,
  'product detail exposes published rules only');
select tests.assert_int(
  (public.get_product_detail(
    '10000000-0000-4000-8000-000000000001'
  )->>'comment_count')::integer,
  2,
  'product detail comment count');
select tests.assert_true(
  not (public.get_product_detail(
    '10000000-0000-4000-8000-000000000001'
  )->>'is_favorite')::boolean,
  'anonymous product detail is not favorited');

select tests.assert_int(
  jsonb_array_length(public.get_rule_detail(
    '20000000-0000-4000-8000-000000000001'
  )->'evidence'),
  1,
  'rule detail includes evidence');
select tests.assert_int(
  (public.get_rule_detail(
    '20000000-0000-4000-8000-000000000001'
  )->>'confirm_count')::integer,
  0,
  'rule detail exposes aggregate confirms');

select tests.assert_true(
  jsonb_array_length(public.product_graph(
    '10000000-0000-4000-8000-000000000001', 1
  )->'nodes') >= 5,
  'graph includes explicit neighbors');
select tests.assert_int(
  jsonb_array_length(public.product_graph(
    '10000000-0000-4000-8000-000000000001', 1
  )->'edges'),
  4,
  'graph edges are published rules only');

select tests.assert_int(
  jsonb_array_length(public.get_guide_detail(
    '40000000-0000-4000-8000-000000000001'
  )->'steps'),
  3,
  'guide detail includes ordered steps');

select tests.assert_int((select products from public.public_stats), 7, 'public product stat');
select tests.assert_int((select rules from public.public_stats), 4, 'public rule stat');
select tests.assert_int((select guides from public.public_stats), 1, 'public guide stat');
select tests.assert_int((select evidence_items from public.public_stats), 4, 'public evidence stat');

select tests.assert_int(
  (select count(*)::integer from public.requests_public), 1, 'public request projection');
select tests.assert_int(
  (select vote_count from public.requests_public
   where id = '50000000-0000-4000-8000-000000000001'),
  2, 'request votes are aggregate-only and public');
select tests.assert_int(
  (select count(*)::integer from public.collections_public), 1, 'public collection projection');
select tests.assert_int(
  (select item_count from public.collections_public
   where id = '60000000-0000-4000-8000-000000000001'),
  3, 'collection item count');
select tests.assert_int(
  (select count(*)::integer from public.comments_public), 2, 'public comments projection');
select tests.assert_int(
  (select count(*)::integer from public.evidence_public), 4, 'public evidence projection');
select tests.assert_int(
  (select count(*)::integer from public.profiles_public), 4, 'public profiles projection');
select tests.assert_true(
  (select reputation_points from public.profiles_public
   where id = '00000000-0000-4000-8000-000000000003') = 3,
  'public reputation comes from the ledger');
select tests.assert_int(
  jsonb_array_length(public.public_trending_products(10)->'items'), 1,
  'trending starts at the privacy threshold from seed fixtures');
select tests.assert_int(
  (public.public_trending_products(10)->'items'->0->>'view_count')::integer, 3,
  'seed trending exposes aggregate view counts only');

select tests.assert_true(public.track_event(
  'product_view', 'product', '10000000-0000-4000-8000-000000000002',
  'anonymous-test-session-0001', '{"query":"should not matter"}'::jsonb
), 'anonymous analytics ingestion');
select tests.assert_true(public.track_event(
  'product_view', 'product', '10000000-0000-4000-8000-000000000002',
  'anonymous-test-session-0002', '{}'::jsonb
), 'anonymous analytics ingestion is repeatable');
select tests.assert_int(
  jsonb_array_length(public.public_trending_products(10)->'items'), 1,
  'trending hides products below the privacy threshold');
select tests.assert_true(public.track_event(
  'product_view', 'product', '10000000-0000-4000-8000-000000000002',
  'anonymous-test-session-0003', '{}'::jsonb
), 'anonymous analytics ingestion reaches the threshold');

select tests.assert_int(
  jsonb_array_length(public.public_trending_products(10)->'items'), 2,
  'trending publishes products at the privacy threshold');
select tests.assert_true(
  public.public_trending_products(10)->'items' @>
    '[{"id":"10000000-0000-4000-8000-000000000002","view_count":3}]'::jsonb,
  'trending reports the thresholded aggregate');
select tests.expect_error(
  'select public.track_event(''product_view'', ''product'', null, repeat(''x'', 200), ''{}''::jsonb)',
  '22023', 'oversized analytics session is rejected');

select tests.expect_error('select public.admin_dashboard()', '42501',
  'anonymous cannot open admin dashboard');
select tests.expect_error('select public.me()', '42501',
  'anonymous cannot read current profile');
select tests.expect_error('select * from public.products limit 1', '42501',
  'anonymous has no base product table access');
select tests.expect_error('select * from public.rule_votes limit 1', '42501',
  'anonymous cannot read individual votes');
select tests.expect_error('select * from public.analytics_events limit 1', '42501',
  'anonymous cannot read raw analytics');
select tests.expect_error(
  'select public.search_products(repeat(''a'', 200))', '22023',
  'oversized search is rejected');

rollback;
