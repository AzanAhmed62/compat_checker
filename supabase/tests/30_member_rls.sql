-- 30: authenticated member behavior, RLS, privacy, and self-service RPCs.
begin;

-- A private draft fixture proves that non-published rows stay inaccessible.
insert into public.products (
  id, slug, name, brand_id, category_id, status, created_by
)
select
  '10000000-0000-4000-8000-0000000000ff', 'draft-fixture',
  'Draft fixture (not published)', b.id, c.id, 'draft',
  '00000000-0000-4000-8000-000000000001'
from public.brands b, public.categories c
where b.slug = 'sample-electronics' and c.slug = 'other-components';

select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000004', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;

select tests.assert_text(public.me()->>'role', 'member', 'member sees own role');
select tests.assert_text(public.me()->>'account_status', 'active', 'member account is active');
select tests.assert_true(
  not (public.me()->'capabilities'->>'can_moderate')::boolean,
  'member cannot moderate');
select tests.assert_true(
  not (public.me()->'capabilities'->>'can_admin')::boolean,
  'member cannot administer');

select tests.expect_error('select public.admin_dashboard()', '42501',
  'member cannot open the admin dashboard');
select tests.expect_error('select * from public.analytics_events', '42501',
  'member cannot read raw analytics');
select tests.assert_int(
  (select count(*)::integer from public.audit_logs), 0,
  'audit logs are hidden by RLS');
select tests.assert_int(
  (select count(*)::integer from public.rule_votes), 0,
  'members see no votes other than their own');
select tests.expect_error(
  'select moderation_note from public.profiles limit 1', '42501',
  'moderation notes are not client-readable');
select tests.expect_error(
  'insert into public.products (slug, name, brand_id, category_id) '
  'select ''hacked-product'', ''Hacked'', b.id, c.id from public.brands b, public.categories c limit 1',
  '42501', 'member cannot insert catalog products');

-- The public profile projection is public; the base table is narrowed by RLS.
select tests.assert_true(
  (select count(*) from public.profiles where id <> auth.uid()) = 3,
  'active profiles are visible to signed-in members');
select tests.assert_true(
  (select count(*) from public.profiles_public where id = auth.uid()) = 1,
  'member appears in the public profile projection');

-- Profile self-service.
select tests.assert_text(
  public.update_my_profile('Updated Member', 'Local test bio', null, 'ur', null)->>'locale',
  'ur', 'member can set an Urdu locale');
select tests.assert_text(public.me()->>'display_name', 'Updated Member',
  'profile update is reflected in me()');
select tests.expect_error(
  'select public.update_my_profile(null, null, null, ''not_a_locale'', null)',
  '22023', 'invalid locale is rejected');
select tests.expect_error(
  'select public.update_my_profile(null, null, null, null, ''local_admin'')',
  '23505', 'username uniqueness is enforced');
select tests.expect_error(
  'select public.update_my_profile(null, null, ''http://insecure.test/a.png'', null, null)',
  '22023', 'avatars must use HTTPS');

-- Favorites.
select tests.assert_true(
  (public.toggle_favorite('10000000-0000-4000-8000-000000000001')->>'is_favorite')::boolean,
  'member can favorite a published product');
select tests.assert_true(
  (public.get_product_detail('10000000-0000-4000-8000-000000000001')->>'is_favorite')::boolean,
  'favorite state is visible on product detail');
select tests.assert_int(
  jsonb_array_length(public.list_my_favorites()->'items'), 1,
  'favorites list contains the product');
select tests.assert_true(
  not (public.toggle_favorite('10000000-0000-4000-8000-000000000001')->>'is_favorite')::boolean,
  'toggling twice removes the favorite');
select tests.expect_error(
  'select public.toggle_favorite(''10000000-0000-4000-8000-0000000000ff'')',
  'P0001', 'draft products cannot be favorited');

-- Rule voting: aggregate public, individual private.
select tests.assert_text(
  public.cast_rule_vote('20000000-0000-4000-8000-000000000001', 'confirm')->>'my_vote',
  'confirm', 'member can confirm a published rule');
select tests.assert_int(
  (public.cast_rule_vote('20000000-0000-4000-8000-000000000001', 'confirm')->>'confirms')::integer,
  1, 'confirm aggregate is updated');
select tests.assert_text(
  public.get_rule_detail('20000000-0000-4000-8000-000000000001')->>'my_vote',
  'confirm', 'member sees only their own vote in rule detail');
select tests.expect_error(
  'select public.cast_rule_vote(''20000000-0000-4000-8000-000000000005'', ''confirm'')',
  'P0001', 'draft rules cannot be voted on');
select tests.expect_error(
  'select public.cast_rule_vote(''20000000-0000-4000-8000-000000000001'', ''like'')',
  '22023', 'invalid vote values are rejected');
select tests.assert_int(
  (public.remove_rule_vote('20000000-0000-4000-8000-000000000001')->>'confirms')::integer,
  0, 'removing a vote updates the aggregate');

-- Requests: identity is private, aggregates are public.
select tests.assert_int(
  (select count(*)::integer from public.product_requests), 1,
  'request base table is restricted to the owner');
select tests.assert_int(
  (select count(*)::integer from public.requests_public), 1,
  'requests project publicly without requester identity');
select tests.expect_error(
  'select created_by from public.requests_public limit 1', '42703',
  'requester identity is absent from the public projection');
select tests.assert_true(
  (public.toggle_request_vote('50000000-0000-4000-8000-000000000001')->>'voted')::boolean,
  'member can vote for a request');
select tests.assert_int(
  (select votes from public.request_vote_counts
   where request_id = '50000000-0000-4000-8000-000000000001'),
  3, 'request vote aggregate is updated');
select tests.assert_true(
  not (public.toggle_request_vote('50000000-0000-4000-8000-000000000001')->>'voted')::boolean,
  'toggling removes the request vote');
select tests.expect_error(
  'select public.toggle_request_vote(''50000000-0000-4000-8000-000000000099'')',
  'P0001', 'unknown requests cannot be voted on');

-- Comments.
select tests.assert_text(
  public.create_comment(
    'product', '10000000-0000-4000-8000-000000000001',
    'Member test reply', '70000000-0000-4000-8000-000000000002')->>'status',
  'visible', 'member can create a comment');
select tests.assert_int(
  (select count(*)::integer from public.comments_public), 3,
  'visible comments increase the public count');
select tests.expect_error(
  'select public.create_comment(''product'', ''10000000-0000-4000-8000-0000000000ff'', ''Draft target'')',
  '23503', 'comments on unpublished products are rejected');
select tests.expect_error(
  'select public.create_comment(''guide'', ''40000000-0000-4000-8000-000000000001'', ''Wrong parent'', '
  '''70000000-0000-4000-8000-000000000002'')',
  '22023', 'a parent comment must share the target');
select tests.expect_error(
  'select public.create_comment(''user'', ''00000000-0000-4000-8000-000000000003'', ''Bad target'')',
  '22023', 'invalid comment target types are rejected');
select tests.assert_true(
  public.delete_my_comment(
    (select id from public.comments where user_id = auth.uid()
     and body = 'Member test reply')),
  'member can delete their own comment');
select tests.assert_int(
  (select count(*)::integer from public.comments_public), 2,
  'deleted comments disappear from the public count');

-- Reports.
select tests.assert_true(
  public.submit_report('product', '10000000-0000-4000-8000-000000000001',
                       'false_claim', 'Local synthetic report') is not null,
  'member can submit a report');
select tests.assert_int(
  (select count(*)::integer from public.reports), 1,
  'report base table is restricted to the reporter');
select tests.expect_error(
  'select public.submit_report(''product'', ''10000000-0000-4000-8000-000000000001'', ''spam'', null)',
  '23505', 'duplicate open reports are rejected');
select tests.expect_error(
  'select public.submit_report(''product'', ''10000000-0000-4000-8000-000000000099'', ''spam'', null)',
  '23503', 'reports require an existing target');

-- Structured contributions.
select tests.assert_true(
  public.submit_contribution(
    'new_product',
    jsonb_build_object(
      'name', 'Member sample product',
      'slug', 'member-sample-product',
      'brand_id', (select id from public.brands where slug = 'sample-electronics'),
      'category_id', (select id from public.categories where slug = 'other-components')
    )
  ) is not null, 'member can submit a structured product contribution');
select tests.assert_int(
  jsonb_array_length(public.list_my_contributions()->'items'), 1,
  'member lists their own contribution');
select tests.expect_error(
  'select public.submit_contribution(''new_rule'', ''{"subject_product_id":"10000000-0000-4000-8000-000000000001","object_product_id":"10000000-0000-4000-8000-000000000004","relationship":"fits","confidence":50,"summary":"Missing evidence","evidence":[]}''::jsonb)',
  '22023', 'compatibility contributions require evidence');
select tests.expect_error(
  'select public.submit_contribution(''new_rule'', ''{"subject_product_id":"10000000-0000-4000-8000-000000000001","object_product_id":"10000000-0000-4000-8000-000000000004","relationship":"fits","confidence":101,"summary":"Bad confidence","evidence":[{"title":"Source","source_url":"https://docs.local/source","evidence_type":"other"}]}''::jsonb)',
  '22023', 'confidence must be between 0 and 100');
select tests.expect_error(
  'select public.submit_contribution(''new_rule'', ''{"subject_product_id":"10000000-0000-4000-8000-000000000001","object_product_id":"10000000-0000-4000-8000-000000000004","relationship":"fits","confidence":50,"summary":"Example URL","evidence":[{"title":"Source","source_url":"https://example.com/nope","evidence_type":"other"}]}''::jsonb)',
  '22023', 'placeholder example URLs are rejected');
select tests.expect_error(
  'select public.submit_contribution(''new_product'', ''{"name":"X","slug":"Bad Slug","brand_id":"00000000-0000-4000-8000-000000000001","category_id":"00000000-0000-4000-8000-000000000001"}''::jsonb)',
  '22023', 'invalid contribution slugs are rejected');
select tests.assert_true(
  public.withdraw_contribution(
    (select (item->>'id')::uuid
     from jsonb_array_elements(public.list_my_contributions()->'items') item
     limit 1)),
  'member can withdraw a pending contribution');

-- Collections.
select tests.assert_true(
  public.create_collection('Member private collection', 'Synthetic list', false) is not null,
  'member can create a collection');
select tests.assert_true(
  public.add_to_collection(
    (select id from public.collections where owner_id = auth.uid()
     and title = 'Member private collection'),
    '10000000-0000-4000-8000-000000000002', 'Display'),
  'member can add a published product');
select tests.assert_int(
  (select count(*)::integer from public.collections_public
   where owner_username = 'sample_member'
     and title = 'Member private collection'), 0,
  'private collections are absent from the public projection');
select tests.assert_true(
  public.update_collection(
    (select id from public.collections where owner_id = auth.uid()
     and title = 'Member private collection'),
    'Member public collection', null, true),
  'member can publish a collection');
select tests.assert_int(
  (select count(*)::integer from public.collections_public
   where title = 'Member public collection'), 1,
  'published collections appear publicly');
select tests.assert_true(
  public.remove_from_collection(
    (select id from public.collections where owner_id = auth.uid()
     and title = 'Member public collection'),
    '10000000-0000-4000-8000-000000000002'),
  'member can remove a collection item');
select tests.assert_true(
  public.delete_collection(
    (select id from public.collections where owner_id = auth.uid()
     and title = 'Member public collection')),
  'member can delete their collection');

-- Account lifecycle: hidden immediately, cancellable.
select tests.assert_text(
  public.request_account_deletion()->>'status', 'deletion_pending',
  'member can request account deletion');
select tests.assert_text(public.me()->>'account_status', 'deletion_pending',
  'deletion request changes account status');
select tests.assert_int(
  (select count(*)::integer from public.profiles_public where id = auth.uid()), 0,
  'deletion-pending profiles disappear from public projections');
select tests.expect_error(
  'select public.update_my_profile(''Should Fail'', null, null, null, null)',
  '42501', 'deletion-pending accounts cannot mutate data');
select tests.assert_true(public.cancel_account_deletion(),
  'member can cancel a pending deletion');
select tests.assert_text(public.me()->>'account_status', 'active',
  'cancelling restores the account');

-- Updates against admin-only tables affect no rows under RLS.
update public.products
set name = 'HACKED'
where id = '10000000-0000-4000-8000-000000000001';
select tests.assert_text(
  public.get_product_detail('10000000-0000-4000-8000-000000000001')->>'name',
  'Aurora X1 (sample phone)', 'member cannot update catalog products');

rollback;
