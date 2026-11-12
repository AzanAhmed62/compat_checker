-- 80: atomic admin saves, rule/evidence snapshot fidelity, and rollback sets.
begin;

reset role;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000001', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;

-- ---------------------------------------------------------------------------
-- Atomic product save
-- ---------------------------------------------------------------------------

select public.admin_save_product(
  null::uuid,
  jsonb_build_object(
    'name', 'Atomic Test Product',
    'slug', 'atomic-test-product',
    'brand_id', (select id from public.brands where slug = 'sample-electronics'),
    'category_id', (select id from public.categories where slug = 'batteries'),
    'model_number', 'AT-1',
    'release_year', 2026,
    'summary', 'Synthetic product fixture created by the atomic save test.',
    'technical_notes', null,
    'connector_type', null,
    'form_factor', null,
    'status', 'draft'
  ),
  '["Atomic Alias", "atomic alias"]'::jsonb,
  '[{"label":"Capacity","value":"1000","unit":"mAh","is_critical":true}]'::jsonb
) as atomic_product_id \gset

select tests.assert_int(
  (select count(*)::integer from public.product_aliases
   where product_id = :'atomic_product_id'::uuid), 1,
  'product aliases are deduplicated case-insensitively');
select tests.assert_int(
  (select count(*)::integer from public.product_specs
   where product_id = :'atomic_product_id'::uuid), 1,
  'product specifications are written in the same transaction');
select tests.assert_text(
  (select status::text from public.products
   where id = :'atomic_product_id'::uuid), 'draft',
  'product status is applied');

select public.admin_save_product(
  :'atomic_product_id'::uuid,
  jsonb_build_object(
    'name', 'Atomic Test Product Revised',
    'slug', 'atomic-test-product',
    'brand_id', (select id from public.brands where slug = 'sample-electronics'),
    'category_id', (select id from public.categories where slug = 'batteries'),
    'model_number', 'AT-2',
    'release_year', 2026,
    'summary', 'Synthetic revised product fixture.',
    'technical_notes', null,
    'connector_type', null,
    'form_factor', null,
    'status', 'draft'
  ),
  '["Replacement Alias"]'::jsonb,
  '[{"label":"Voltage","value":"3.8","unit":"V","is_critical":false}]'::jsonb
) as revised_product_id \gset

select tests.assert_text(
  (select name from public.products where id = :'atomic_product_id'::uuid),
  'Atomic Test Product Revised', 'product fields are updated');
select tests.assert_true(
  not exists (select 1 from public.product_aliases
              where product_id = :'atomic_product_id'::uuid and alias = 'Atomic Alias'),
  'replaced aliases do not survive');
select tests.assert_true(
  exists (select 1 from public.product_aliases
          where product_id = :'atomic_product_id'::uuid and alias = 'Replacement Alias'),
  'new aliases are present');
select tests.assert_true(
  exists (select 1 from public.product_specs
          where product_id = :'atomic_product_id'::uuid and label = 'Voltage'),
  'new specifications are present');
select tests.assert_true(
  not exists (select 1 from public.product_specs
              where product_id = :'atomic_product_id'::uuid and label = 'Capacity'),
  'replaced specifications do not survive');

select tests.expect_error(
  format(
    'select public.admin_save_product(null::uuid, %L::jsonb, %L::jsonb, %L::jsonb)',
    jsonb_build_object(
      'name', 'Duplicate Product',
      'slug', 'aurora-x1',
      'brand_id', (select id from public.brands where slug = 'sample-electronics'),
      'category_id', (select id from public.categories where slug = 'batteries'),
      'model_number', null, 'release_year', null, 'summary', null,
      'technical_notes', null, 'connector_type', null, 'form_factor', null,
      'status', 'draft'
    ),
    '[]'::jsonb, '[]'::jsonb
  ),
  '23505', 'duplicate product slugs are rejected');
select tests.assert_int(
  (select count(*)::integer from public.products where name = 'Duplicate Product'), 0,
  'a rejected product insert leaves nothing behind');

select tests.expect_error(
  format(
    'select public.admin_save_product(%L::uuid, %L::jsonb, %L::jsonb, %L::jsonb)',
    :'atomic_product_id',
    jsonb_build_object(
      'name', 'Should Not Persist',
      'slug', 'aurora-x1',
      'brand_id', (select id from public.brands where slug = 'sample-electronics'),
      'category_id', (select id from public.categories where slug = 'batteries'),
      'model_number', null, 'release_year', null, 'summary', null,
      'technical_notes', null, 'connector_type', null, 'form_factor', null,
      'status', 'draft'
    ),
    '["Broken Alias"]'::jsonb, '[]'::jsonb
  ),
  '23505', 'a failing product update is rejected');
select tests.assert_text(
  (select name from public.products where id = :'atomic_product_id'::uuid),
  'Atomic Test Product Revised', 'failed product updates do not partially persist');
select tests.assert_true(
  exists (select 1 from public.product_aliases
          where product_id = :'atomic_product_id'::uuid and alias = 'Replacement Alias'),
  'failed product updates preserve the previous alias set');

-- ---------------------------------------------------------------------------
-- Atomic guide save
-- ---------------------------------------------------------------------------

select public.admin_save_guide(
  null::uuid,
  jsonb_build_object(
    'slug', 'atomic-test-guide',
    'title', 'Atomic Test Guide',
    'summary', 'Synthetic guide fixture for the atomic transaction test.',
    'body_md', 'This synthetic body exists only to exercise transactional guide saving.',
    'difficulty', 'moderate',
    'status', 'draft',
    'product_id', null
  ),
  '[{"title":"First step","body":"Synthetic first step body.","image_path":null},
    {"title":"Second step","body":"Synthetic second step body.","image_path":null}]'::jsonb
) as atomic_guide_id \gset

select tests.assert_int(
  (select count(*)::integer from public.guide_steps
   where guide_id = :'atomic_guide_id'::uuid), 2,
  'guide creation writes all steps');

select public.admin_save_guide(
  :'atomic_guide_id'::uuid,
  jsonb_build_object(
    'slug', 'atomic-test-guide',
    'title', 'Atomic Test Guide Revised',
    'summary', 'Synthetic revised guide fixture.',
    'body_md', 'This synthetic revised body exists only to exercise guide saving.',
    'difficulty', 'easy',
    'status', 'draft',
    'product_id', null
  ),
  '[{"title":"Replacement step","body":"Synthetic replacement step body.","image_path":null}]'::jsonb
) as revised_guide_id \gset

select tests.assert_text(
  (select title from public.guides where id = :'atomic_guide_id'::uuid),
  'Atomic Test Guide Revised', 'guide fields are updated');
select tests.assert_int(
  (select count(*)::integer from public.guide_steps
   where guide_id = :'atomic_guide_id'::uuid), 1,
  'guide step replacement removes omitted steps');
select tests.assert_text(
  (select title from public.guide_steps
   where guide_id = :'atomic_guide_id'::uuid and position = 1),
  'Replacement step', 'guide step positions restart at one');

select tests.expect_error(
  format(
    'select public.admin_save_guide(%L::uuid, %L::jsonb, %L::jsonb)',
    :'atomic_guide_id',
    jsonb_build_object(
      'slug', 'atomic-test-guide',
      'title', 'Broken Guide Update',
      'summary', 'Synthetic broken guide fixture.',
      'body_md', 'This synthetic broken body exists only to exercise rollback.',
      'difficulty', 'easy',
      'status', 'draft',
      'product_id', null
    ),
    '[{"title":"","body":"Missing title should fail.","image_path":null}]'::jsonb
  ),
  '22023', 'invalid guide steps abort the update');
select tests.assert_text(
  (select title from public.guides where id = :'atomic_guide_id'::uuid),
  'Atomic Test Guide Revised', 'failed guide updates do not change fields');
select tests.assert_int(
  (select count(*)::integer from public.guide_steps
   where guide_id = :'atomic_guide_id'::uuid), 1,
  'failed guide updates preserve the previous step set');
select tests.assert_text(
  (select title from public.guide_steps
   where guide_id = :'atomic_guide_id'::uuid and position = 1),
  'Replacement step', 'failed guide updates preserve step content');

-- ---------------------------------------------------------------------------
-- Rule history: evidence-only and combined edits produce one final snapshot
-- ---------------------------------------------------------------------------

select count(*) as rule_versions_before
from public.rule_versions
where rule_id = '20000000-0000-4000-8000-000000000001' \gset

select jsonb_build_array(jsonb_build_object(
  'id', e.id,
  'evidence_type', e.evidence_type,
  'title', e.title || ' (admin updated)',
  'source_url', e.source_url,
  'publisher', e.publisher,
  'excerpt', coalesce(e.excerpt, ''),
  'captured_at', coalesce(e.captured_at::text, '')
)) as rule_one_evidence
from public.rule_evidence e
where e.rule_id = '20000000-0000-4000-8000-000000000001' \gset

select public.admin_save_rule(
  '20000000-0000-4000-8000-000000000001'::uuid,
  jsonb_build_object(
    'subject_product_id', '10000000-0000-4000-8000-000000000002',
    'object_product_id', '10000000-0000-4000-8000-000000000001',
    'relationship', 'fits',
    'verification', 'experimental',
    'confidence', 20,
    'risk', 'low',
    'summary', 'SEED DATA: synthetic display-fit claim for workflow testing, not a real compatibility finding.',
    'technical_details', 'This claim exists only in the local seed. It has no evidentiary value.',
    'required_modifications', 'None. Do not use for real repairs.',
    'status', 'published'
  ),
  :'rule_one_evidence'::jsonb,
  'Evidence-only admin history test'
) as evidence_only_result \gset

select tests.assert_int(
  (select count(*)::integer from public.rule_versions
   where rule_id = '20000000-0000-4000-8000-000000000001'),
  :rule_versions_before + 1,
  'an evidence-only save creates exactly one version');
select tests.assert_text(
  (select snapshot -> 'evidence' -> 0 ->> 'title'
   from public.rule_versions
   where rule_id = '20000000-0000-4000-8000-000000000001'
   order by version desc limit 1),
  (select title from public.rule_evidence
   where rule_id = '20000000-0000-4000-8000-000000000001' limit 1),
  'the new version contains the final evidence title');
select tests.assert_text(
  (select change_reason from public.rule_versions
   where rule_id = '20000000-0000-4000-8000-000000000001'
   order by version desc limit 1),
  'Evidence-only admin history test',
  'the evidence-only version records its change reason');

select count(*) as rule_versions_before_combined
from public.rule_versions
where rule_id = '20000000-0000-4000-8000-000000000001' \gset

select public.admin_save_rule(
  '20000000-0000-4000-8000-000000000001'::uuid,
  jsonb_build_object(
    'subject_product_id', '10000000-0000-4000-8000-000000000002',
    'object_product_id', '10000000-0000-4000-8000-000000000001',
    'relationship', 'fits',
    'verification', 'experimental',
    'confidence', 25,
    'risk', 'low',
    'summary', 'Atomic combined rule and evidence edit for history testing.',
    'technical_details', 'Synthetic combined edit. No real compatibility claim is made.',
    'required_modifications', 'None. Do not use for real repairs.',
    'status', 'published'
  ),
  :'rule_one_evidence'::jsonb || jsonb_build_array(jsonb_build_object(
    'evidence_type', 'other',
    'title', 'Synthetic atomic evidence two',
    'source_url', 'https://invalid.local/atomic/rule-evidence-2',
    'publisher', 'Compat Checker tests',
    'excerpt', 'Synthetic second citation for transaction testing.',
    'captured_at', ''
  )),
  'Combined rule and evidence edit'
) as combined_result \gset

select tests.assert_int(
  (select count(*)::integer from public.rule_versions
   where rule_id = '20000000-0000-4000-8000-000000000001'),
  :rule_versions_before_combined + 1,
  'a combined rule and evidence save creates exactly one version');
select tests.assert_text(
  (select snapshot ->> 'summary' from public.rule_versions
   where rule_id = '20000000-0000-4000-8000-000000000001'
   order by version desc limit 1),
  'Atomic combined rule and evidence edit for history testing.',
  'the final version contains the updated rule fields');
select tests.assert_int(
  (select jsonb_array_length(snapshot -> 'evidence')
   from public.rule_versions
   where rule_id = '20000000-0000-4000-8000-000000000001'
   order by version desc limit 1),
  2, 'the final version contains the complete evidence set');

select count(*) as versions_before_failure
from public.rule_versions
where rule_id = '20000000-0000-4000-8000-000000000001' \gset

select tests.expect_error(
  format(
    'select public.admin_save_rule(%L::uuid, %L::jsonb, %L::jsonb, %L)',
    '20000000-0000-4000-8000-000000000001',
    jsonb_build_object(
      'subject_product_id', '10000000-0000-4000-8000-000000000002',
      'object_product_id', '10000000-0000-4000-8000-000000000001',
      'relationship', 'fits', 'verification', 'experimental',
      'confidence', 25, 'risk', 'low',
      'summary', 'This summary must not survive the failed transaction.',
      'technical_details', null, 'required_modifications', null,
      'status', 'published'
    ),
    :'rule_one_evidence'::jsonb || jsonb_build_array(jsonb_build_object(
      'id', '30000000-0000-4000-8000-000000000003',
      'evidence_type', 'other',
      'title', 'Evidence from another rule',
      'source_url', 'https://invalid.local/seed/display-incompatibility',
      'publisher', 'Compat Checker local seed',
      'excerpt', '',
      'captured_at', ''
    )),
    'Ownership failure test'
  ),
  '42501', 'evidence cannot be reassigned from another rule');
select tests.assert_int(
  (select count(*)::integer from public.rule_versions
   where rule_id = '20000000-0000-4000-8000-000000000001'),
  :versions_before_failure,
  'a failed evidence save creates no version');
select tests.assert_text(
  (select summary from public.compatibility_rules
   where id = '20000000-0000-4000-8000-000000000001'),
  'Atomic combined rule and evidence edit for history testing.',
  'a failed evidence save leaves the rule unchanged');
select tests.assert_int(
  (select count(*)::integer from public.rule_evidence
   where rule_id = '20000000-0000-4000-8000-000000000001'), 2,
  'a failed evidence save leaves the evidence set unchanged');

-- Removing one of two citations is allowed and snapshotted.
select jsonb_build_array(jsonb_build_object(
  'id', e.id,
  'evidence_type', e.evidence_type,
  'title', e.title,
  'source_url', e.source_url,
  'publisher', e.publisher,
  'excerpt', coalesce(e.excerpt, ''),
  'captured_at', coalesce(e.captured_at::text, '')
)) as one_of_two_evidence
from public.rule_evidence e
where e.rule_id = '20000000-0000-4000-8000-000000000001'
order by e.created_at limit 1 \gset

select public.admin_save_rule(
  '20000000-0000-4000-8000-000000000001'::uuid,
  jsonb_build_object(
    'subject_product_id', '10000000-0000-4000-8000-000000000002',
    'object_product_id', '10000000-0000-4000-8000-000000000001',
    'relationship', 'fits', 'verification', 'experimental',
    'confidence', 25, 'risk', 'low',
    'summary', 'Atomic combined rule and evidence edit for history testing.',
    'technical_details', 'Synthetic combined edit. No real compatibility claim is made.',
    'required_modifications', 'None. Do not use for real repairs.',
    'status', 'published'
  ),
  :'one_of_two_evidence'::jsonb,
  'Remove one non-final citation'
) as remove_one_result \gset

select tests.assert_int(
  (select count(*)::integer from public.rule_evidence
   where rule_id = '20000000-0000-4000-8000-000000000001'), 1,
  'removing a non-final citation is allowed');
select tests.assert_int(
  (select jsonb_array_length(snapshot -> 'evidence')
   from public.rule_versions
   where rule_id = '20000000-0000-4000-8000-000000000001'
   order by version desc limit 1),
  1, 'the removal is reflected in the new snapshot');

select count(*) as versions_before_last_evidence
from public.rule_versions
where rule_id = '20000000-0000-4000-8000-000000000001' \gset

select tests.expect_error(
  format(
    'select public.admin_save_rule(%L::uuid, %L::jsonb, %L::jsonb, %L)',
    '20000000-0000-4000-8000-000000000001',
    jsonb_build_object(
      'subject_product_id', '10000000-0000-4000-8000-000000000002',
      'object_product_id', '10000000-0000-4000-8000-000000000001',
      'relationship', 'fits', 'verification', 'experimental',
      'confidence', 25, 'risk', 'low',
      'summary', 'Attempt to publish without citations must fail.',
      'technical_details', null, 'required_modifications', null,
      'status', 'published'
    ),
    '[]'::jsonb,
    'Last evidence removal test'
  ),
  '23514', 'a published rule cannot lose its last citation');
select tests.assert_int(
  (select count(*)::integer from public.rule_versions
   where rule_id = '20000000-0000-4000-8000-000000000001'),
  :versions_before_last_evidence,
  'the rejected last-evidence removal creates no version');

-- ---------------------------------------------------------------------------
-- Publication and retirement through the atomic rule RPC
-- ---------------------------------------------------------------------------

select count(*) as versions_before_camera_publish
from public.rule_versions
where rule_id = '20000000-0000-4000-8000-000000000005' \gset

select public.admin_save_rule(
  '20000000-0000-4000-8000-000000000005'::uuid,
  jsonb_build_object(
    'subject_product_id', '10000000-0000-4000-8000-000000000007',
    'object_product_id', '10000000-0000-4000-8000-000000000001',
    'relationship', 'fits', 'verification', 'needs_verification',
    'confidence', 10, 'risk', 'moderate',
    'summary', 'Synthetic atomic camera claim used to test publication transitions.',
    'technical_details', 'Synthetic claim. It has no evidentiary value.',
    'required_modifications', null,
    'status', 'published'
  ),
  '[{"evidence_type":"other","title":"Synthetic publication citation",
     "source_url":"https://invalid.local/atomic/publication-citation",
     "publisher":"Compat Checker tests","excerpt":"Synthetic citation.","captured_at":""}]'::jsonb,
  'Publish synthetic camera rule'
) as published_rule_result \gset

select '20000000-0000-4000-8000-000000000005'::uuid as published_rule_id \gset

select tests.assert_text(
  (select status::text from public.compatibility_rules
   where id = :'published_rule_id'::uuid), 'published',
  'a rule with evidence can be published atomically');
select tests.assert_int(
  (select count(*)::integer from public.rule_versions
   where rule_id = :'published_rule_id'::uuid),
  :versions_before_camera_publish + 1,
  'publication creates exactly one authoritative version');
select tests.assert_text(
  (select snapshot -> 'evidence' -> 0 ->> 'title'
   from public.rule_versions
   where rule_id = :'published_rule_id'::uuid
   order by version desc limit 1),
  'Synthetic publication citation',
  'the publication version contains the staged evidence');
select tests.assert_text(
  (select change_reason from public.rule_versions
   where rule_id = :'published_rule_id'::uuid
   order by version desc limit 1),
  'Publish synthetic camera rule',
  'the publication version records the supplied reason');
select tests.assert_text(
  public.check_compatibility(
    '10000000-0000-4000-8000-000000000001',
    '10000000-0000-4000-8000-000000000007')->>'outcome',
  'compatible', 'the newly published rule is publicly evaluable');

select tests.expect_error(
  format(
    'select public.admin_save_rule(null::uuid, %L::jsonb, %L::jsonb, %L)',
    jsonb_build_object(
      'subject_product_id', '10000000-0000-4000-8000-000000000007',
      'object_product_id', '10000000-0000-4000-8000-000000000002',
      'relationship', 'fits', 'verification', 'needs_verification',
      'confidence', 10, 'risk', 'moderate',
      'summary', 'Synthetic rule that must never publish without citations.',
      'technical_details', null, 'required_modifications', null,
      'status', 'published'
    ),
    '[]'::jsonb,
    'Publish without evidence test'
  ),
  '23514', 'publication without evidence is rejected');
select tests.assert_int(
  (select count(*)::integer from public.compatibility_rules
   where summary = 'Synthetic rule that must never publish without citations.'), 0,
  'the failed publication leaves no rule behind');

select public.admin_save_rule(
  :'published_rule_id'::uuid,
  jsonb_build_object(
    'subject_product_id', '10000000-0000-4000-8000-000000000007',
    'object_product_id', '10000000-0000-4000-8000-000000000001',
    'relationship', 'fits', 'verification', 'needs_verification',
    'confidence', 10, 'risk', 'moderate',
    'summary', 'Synthetic atomic camera claim used to test publication transitions.',
    'technical_details', 'Synthetic claim. It has no evidentiary value.',
    'required_modifications', null,
    'status', 'retired'
  ),
  '[{"evidence_type":"other","title":"Synthetic publication citation",
     "source_url":"https://invalid.local/atomic/publication-citation",
     "publisher":"Compat Checker tests","excerpt":"Synthetic citation.","captured_at":""}]'::jsonb,
  'Retire synthetic camera rule'
) as retired_rule_result \gset

select tests.assert_text(
  (select status::text from public.compatibility_rules
   where id = :'published_rule_id'::uuid), 'retired',
  'a rule can be retired atomically');
select tests.assert_text(
  public.check_compatibility(
    '10000000-0000-4000-8000-000000000001',
    '10000000-0000-4000-8000-000000000007')->>'outcome',
  'unknown', 'retired rules do not produce public compatibility results');

-- ---------------------------------------------------------------------------
-- Rollback restores the exact evidence set and writes one new version
-- ---------------------------------------------------------------------------

-- Add a citation that is absent from v2, then roll back to v2.
select jsonb_build_array(jsonb_build_object(
  'id', e.id,
  'evidence_type', e.evidence_type,
  'title', e.title,
  'source_url', e.source_url,
  'publisher', e.publisher,
  'excerpt', coalesce(e.excerpt, ''),
  'captured_at', coalesce(e.captured_at::text, '')
)) || jsonb_build_array(jsonb_build_object(
  'evidence_type', 'other',
  'title', 'Synthetic citation added after v2',
  'source_url', 'https://invalid.local/atomic/post-rollback-evidence',
  'publisher', 'Compat Checker tests',
  'excerpt', 'This citation must disappear when v2 is restored.',
  'captured_at', ''
)) as rollback_extra_evidence
from public.rule_evidence e
where e.rule_id = '20000000-0000-4000-8000-000000000001' \gset

select public.admin_save_rule(
  '20000000-0000-4000-8000-000000000001'::uuid,
  jsonb_build_object(
    'subject_product_id', '10000000-0000-4000-8000-000000000002',
    'object_product_id', '10000000-0000-4000-8000-000000000001',
    'relationship', 'fits', 'verification', 'experimental',
    'confidence', 25, 'risk', 'low',
    'summary', 'Atomic combined rule and evidence edit for history testing.',
    'technical_details', 'Synthetic combined edit. No real compatibility claim is made.',
    'required_modifications', 'None. Do not use for real repairs.',
    'status', 'published'
  ),
  :'rollback_extra_evidence'::jsonb,
  'Add citation before rollback test'
) as pre_rollback_result \gset

select count(*) as versions_before_rollback
from public.rule_versions
where rule_id = '20000000-0000-4000-8000-000000000001' \gset

select public.admin_rollback_rule(
  '20000000-0000-4000-8000-000000000001'::uuid,
  2,
  'Restore seeded citation set'
) as rollback_result \gset

select tests.assert_int(
  (select count(*)::integer from public.rule_versions
   where rule_id = '20000000-0000-4000-8000-000000000001'),
  :versions_before_rollback + 1,
  'rollback creates exactly one new version');
select tests.assert_int(
  (select count(*)::integer from public.rule_evidence
   where rule_id = '20000000-0000-4000-8000-000000000001'), 1,
  'rollback removes citations that are absent from the snapshot');
select tests.assert_text(
  (select source_url from public.rule_evidence
   where rule_id = '20000000-0000-4000-8000-000000000001'),
  'https://invalid.local/seed/display-fit',
  'rollback keeps the citation that existed in the snapshot');
select tests.assert_text(
  (select change_reason from public.rule_versions
   where rule_id = '20000000-0000-4000-8000-000000000001'
   order by version desc limit 1),
  'Rollback to v2: Restore seeded citation set',
  'the new rollback version records the reason');
select tests.assert_int(
  (select count(*)::integer from public.rule_versions
   where rule_id = '20000000-0000-4000-8000-000000000001'
     and version < (select max(version) from public.rule_versions
                    where rule_id = '20000000-0000-4000-8000-000000000001')
     and change_reason like 'Rollback to v2:%'), 0,
  'rollback does not rewrite earlier version reasons');

-- Rolling back to a draft with no evidence must also restore the exact set.
select count(*) as versions_before_draft_rollback
from public.rule_versions
where rule_id = :'published_rule_id'::uuid \gset

select public.admin_rollback_rule(
  :'published_rule_id'::uuid,
  1,
  'Restore original draft state'
) as draft_rollback_result \gset

select tests.assert_text(
  (select status::text from public.compatibility_rules
   where id = :'published_rule_id'::uuid), 'draft',
  'rollback can restore a draft state');
select tests.assert_int(
  (select count(*)::integer from public.rule_evidence
   where rule_id = :'published_rule_id'::uuid), 0,
  'draft rollback removes citations absent from the snapshot');
select tests.assert_int(
  (select count(*)::integer from public.rule_versions
   where rule_id = :'published_rule_id'::uuid),
  :versions_before_draft_rollback + 1,
  'draft rollback creates exactly one new version');
select tests.assert_text(
  (select snapshot ->> 'status' from public.rule_versions
   where rule_id = :'published_rule_id'::uuid
   order by version desc limit 1),
  'draft', 'the draft rollback version records the restored status');

-- ---------------------------------------------------------------------------
-- Authorization: only admins may call the transactional RPCs
-- ---------------------------------------------------------------------------

reset role;
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000004', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;

select tests.expect_error(
  'select public.admin_save_product(null::uuid, ''{}''::jsonb, ''[]''::jsonb, ''[]''::jsonb)',
  '42501', 'members cannot save products');
select tests.expect_error(
  'select public.admin_save_guide(null::uuid, ''{}''::jsonb, ''[]''::jsonb)',
  '42501', 'members cannot save guides');
select tests.expect_error(
  'select public.admin_save_rule(null::uuid, ''{}''::jsonb, ''[]''::jsonb, ''reason'')',
  '42501', 'members cannot save rules');
select tests.expect_error(
  'select public.admin_rollback_rule(''20000000-0000-4000-8000-000000000001'', 2, ''Not allowed'')',
  '42501', 'members cannot roll back rules');

rollback;
