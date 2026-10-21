-- 50: structured contribution validation, atomic application, and rewards.
begin;

select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000003', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;

select tests.assert_int(
  (public.my_reputation()->>'points')::integer, 3,
  'contributor starts with seeded ledger points');
select tests.expect_error(
  'select public.admin_list_contributions()', '42501',
  'members cannot list the moderation queue');

-- A valid structured product contribution.
select tests.assert_true(
  public.submit_contribution(
    'new_product',
    jsonb_build_object(
      'name', 'Contributor sample widget',
      'slug', 'contributor-sample-widget',
      'brand_id', (select id from public.brands where slug = 'sample-electronics'),
      'category_id', (select id from public.categories where slug = 'other-components'),
      'summary', 'Synthetic contributor fixture',
      'aliases', jsonb_build_array('CSW', 'Contributor widget'),
      'specs', jsonb_build_array(
        jsonb_build_object('label', 'Connector', 'value', 'Sample', 'is_critical', true)
      )
    )
  ) is not null, 'contributor submits a product with aliases and specs');

reset role;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000002', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
select tests.assert_int(
  (public.admin_list_contributions('pending', null, 10, 0)->>'total')::integer, 1,
  'trusted reviewer sees the pending contribution');
select tests.assert_text(
  public.admin_review_contribution(
    tests.contribution_id('contributor-sample-widget'),
    'approved', 'Fixture reviewed')->>'status',
  'approved', 'trusted reviewer approves a contribution');
select tests.assert_text(
  (select status::text from public.products where slug = 'contributor-sample-widget'),
  'published', 'approved product contribution is published');
select tests.assert_int(
  (select count(*)::integer from public.product_aliases a
   join public.products p on p.id = a.product_id
   where p.slug = 'contributor-sample-widget'), 2,
  'approved aliases are created');
select tests.assert_int(
  (select count(*)::integer from public.product_specs s
   join public.products p on p.id = s.product_id
   where p.slug = 'contributor-sample-widget'), 1,
  'approved specs are created');
reset role;
select tests.assert_true(
  exists (
    select 1 from public.points_ledger
    where user_id = '00000000-0000-4000-8000-000000000003'
      and reason = 'contribution_approved' and delta = 25),
  'approved product contribution awards 25 points');
select tests.assert_true(
  exists (
    select 1 from public.notifications
    where user_id = '00000000-0000-4000-8000-000000000003'
      and type = 'contribution_review'),
  'approval notifies the contributor');
set role authenticated;
select tests.expect_error(
  'select public.admin_review_contribution('
  'tests.contribution_id(''contributor-sample-widget''), '
  '''approved'', ''Duplicate review'')',
  'P0001', 'contributions cannot be reviewed twice');

-- Product edits.
reset role;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000003', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
select tests.assert_true(
  public.submit_contribution(
    'product_edit',
    jsonb_build_object(
      'product_id', '10000000-0000-4000-8000-000000000001',
      'patch', jsonb_build_object(
        'summary', 'Contributor-updated synthetic summary',
        'aliases', jsonb_build_array('AX1', 'AX1 edited')
      )
    )
  ) is not null, 'contributor submits a product edit');

reset role;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000002', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
select tests.assert_text(
  public.admin_review_contribution(
    tests.pending_contribution_of_kind('product_edit'),
    'approved', 'Edit reviewed')->>'status',
  'approved', 'trusted reviewer approves a product edit');
select tests.assert_text(
  (select summary from public.products
   where id = '10000000-0000-4000-8000-000000000001'),
  'Contributor-updated synthetic summary', 'product edit is applied');
select tests.assert_int(
  (select count(*)::integer from public.product_aliases
   where product_id = '10000000-0000-4000-8000-000000000001'), 2,
  'product edit replaces aliases');
reset role;
select tests.assert_true(
  exists (
    select 1 from public.points_ledger
    where user_id = '00000000-0000-4000-8000-000000000003'
      and reason = 'contribution_approved' and delta = 10),
  'approved edit awards 10 points');

-- Compatibility rules always require evidence and cannot self-declare official.
reset role;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000003', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
select tests.assert_true(
  public.submit_contribution(
    'new_rule',
    '{"subject_product_id":"10000000-0000-4000-8000-000000000001",'
    '"object_product_id":"10000000-0000-4000-8000-000000000005",'
    '"relationship":"fits","verification":"official","confidence":80,'
    '"summary":"Synthetic contributor claim with a local citation",'
    '"evidence":[{"title":"Contributor local citation",'
    '"source_url":"https://docs.local/contributor/rule",'
    '"evidence_type":"other"}]}'::jsonb
  ) is not null, 'contributor submits an evidence-backed rule');

reset role;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000002', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
select tests.assert_true(
  public.admin_review_contribution(
    tests.pending_contribution_of_kind('new_rule'),
    'approved', 'Rule evidence checked') is not null,
  'trusted reviewer approves a rule');
reset role;
select tests.assert_text(
  (select verification::text from public.compatibility_rules
   where summary = 'Synthetic contributor claim with a local citation'),
  'community_verified',
  'contributors cannot self-declare official verification');
select tests.assert_int(
  (select count(*)::integer from public.rule_evidence e
   join public.compatibility_rules r on r.id = e.rule_id
   where r.summary = 'Synthetic contributor claim with a local citation'), 1,
  'approved rule evidence is stored');
select tests.assert_int(
  (select count(*)::integer from public.rule_versions v
   join public.compatibility_rules r on r.id = v.rule_id
   where r.summary = 'Synthetic contributor claim with a local citation'), 2,
  'rule insert and publication produce version snapshots');
select tests.assert_text(
  public.check_compatibility(
    '10000000-0000-4000-8000-000000000001',
    '10000000-0000-4000-8000-000000000005')->>'outcome',
  'incompatible',
  'a weaker positive rule cannot override explicit incompatibility');
reset role;
select tests.assert_true(
  exists (
    select 1 from public.points_ledger
    where user_id = '00000000-0000-4000-8000-000000000003'
      and reason = 'contribution_approved' and delta = 40),
  'approved rule awards 40 points');

-- Evidence contributions.
reset role;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000003', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
select tests.assert_true(
  public.submit_contribution(
    'evidence',
    '{"rule_id":"20000000-0000-4000-8000-000000000001",'
    '"evidence":{"title":"Additional contributor citation",'
    '"source_url":"https://docs.local/contributor/evidence",'
    '"evidence_type":"community_report"}}'::jsonb
  ) is not null, 'contributor submits additional evidence');

reset role;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000002', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
select tests.assert_true(
  public.admin_review_contribution(
    tests.pending_contribution_of_kind('evidence'),
    'approved', 'Evidence checked') is not null,
  'trusted reviewer approves evidence');
select tests.assert_int(
  (select count(*)::integer from public.evidence_public
   where rule_id = '20000000-0000-4000-8000-000000000001'), 2,
  'additional evidence appears on the public rule');
reset role;
select tests.assert_true(
  exists (
    select 1 from public.points_ledger
    where user_id = '00000000-0000-4000-8000-000000000003'
      and reason = 'contribution_approved' and delta = 15),
  'approved evidence awards 15 points');

-- Guides.
reset role;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000003', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
select tests.assert_true(
  public.submit_contribution(
    'guide',
    '{"title":"Contributor synthetic guide","summary":"A local-only contribution fixture.",'
    '"body_md":"# Synthetic guide\n\nThis body exists only for local review testing and is not repair advice.",'
    '"difficulty":"easy","product_id":"10000000-0000-4000-8000-000000000001",'
    '"steps":[{"title":"Step one","body":"Synthetic step one."},'
    '{"title":"Step two","body":"Synthetic step two."}]}'::jsonb
  ) is not null, 'contributor submits a guide');

reset role;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000002', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
select tests.assert_true(
  public.admin_review_contribution(
    tests.pending_contribution_of_kind('guide'),
    'approved', 'Guide reviewed') is not null,
  'trusted reviewer approves a guide');
reset role;
select tests.assert_int(
  (select count(*)::integer from public.guide_steps s
   join public.guides g on g.id = s.guide_id
   where g.title = 'Contributor synthetic guide'), 2,
  'approved guide steps are ordered and stored');
select tests.assert_int(
  (select step_count from public.guides_public
   where title = 'Contributor synthetic guide'), 2,
  'approved guide appears in the public projection');
reset role;
select tests.assert_true(
  exists (
    select 1 from public.points_ledger
    where user_id = '00000000-0000-4000-8000-000000000003'
      and reason = 'contribution_approved' and delta = 20),
  'approved guide awards 20 points');

-- Rejection and needs_changes.
reset role;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000003', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
select tests.assert_true(
  public.submit_contribution(
    'new_product',
    jsonb_build_object(
      'name', 'Rejected sample product',
      'slug', 'rejected-sample-product',
      'brand_id', (select id from public.brands where slug = 'sample-electronics'),
      'category_id', (select id from public.categories where slug = 'other-components')
    )
  ) is not null, 'contributor submits a product for rejection');
select tests.assert_true(
  public.submit_contribution(
    'new_product',
    jsonb_build_object(
      'name', 'Needs changes sample product',
      'slug', 'needs-changes-sample-product',
      'brand_id', (select id from public.brands where slug = 'sample-electronics'),
      'category_id', (select id from public.categories where slug = 'other-components')
    )
  ) is not null, 'contributor submits a product needing changes');

reset role;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000002', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
select tests.assert_text(
  public.admin_review_contribution(
    tests.contribution_id('rejected-sample-product'),
    'rejected', 'Synthetic rejection')->>'status',
  'rejected', 'moderator can reject a contribution');
select tests.assert_text(
  public.admin_review_contribution(
    tests.contribution_id('needs-changes-sample-product'),
    'needs_changes', 'Please add a source')->>'status',
  'needs_changes', 'moderator can request changes');
select tests.assert_true(
  not exists (
    select 1 from public.products where slug in
      ('rejected-sample-product', 'needs-changes-sample-product')),
  'rejected and needs_changes contributions create no products');
select tests.expect_error(
  'select public.admin_review_contribution('
  'tests.contribution_id(''rejected-sample-product''), '
  '''bogus'', ''Bad decision'')',
  '22023', 'invalid review decisions are rejected');

-- Withdrawal is owner-only.
reset role;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000003', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
select tests.assert_true(
  public.withdraw_contribution(
    (select id from public.contributions
     where payload ->> 'slug' = 'needs-changes-sample-product')),
  'contributor can withdraw a needs_changes contribution');
select tests.expect_error(
  'select public.withdraw_contribution('
  '(select id from public.contributions where payload ->> ''slug'' = ''rejected-sample-product''))',
  'P0001', 'reviewed contributions cannot be withdrawn');

-- Approval is atomic: a duplicate slug fails without changing the contribution.
select tests.assert_true(
  public.submit_contribution(
    'new_product',
    jsonb_build_object(
      'name', 'Duplicate slug product',
      'slug', 'aurora-x1',
      'brand_id', (select id from public.brands where slug = 'sample-electronics'),
      'category_id', (select id from public.categories where slug = 'other-components')
    )
  ) is not null, 'contributor submits a duplicate-slug product');

reset role;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000002', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
select tests.expect_error(
  'select public.admin_review_contribution('
  'tests.contribution_id(''aurora-x1''), '
  '''approved'', ''Duplicate slug'')',
  '23505', 'approval fails atomically on a duplicate slug');
select tests.assert_text(
  tests.contribution_status('aurora-x1'),
  'pending', 'failed approval leaves the contribution pending');
select tests.assert_int(
  (select count(*)::integer from public.products where slug = 'aurora-x1'), 1,
  'failed approval creates no duplicate product');

-- Self-review is prohibited.
select tests.assert_true(
  public.submit_contribution(
    'new_product',
    jsonb_build_object(
      'name', 'Trusted self review product',
      'slug', 'trusted-self-review-product',
      'brand_id', (select id from public.brands where slug = 'sample-electronics'),
      'category_id', (select id from public.categories where slug = 'other-components')
    )
  ) is not null, 'trusted reviewer can submit their own contribution');
select tests.expect_error(
  'select public.admin_review_contribution('
  'tests.contribution_id(''trusted-self-review-product''), '
  '''approved'', ''Self review'')',
  'P0001', 'reviewers cannot approve their own contribution');

rollback;
