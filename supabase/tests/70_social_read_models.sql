-- 70: collection/profile read models and contribution URL validation.
begin;

-- ---------------------------------------------------------------------------
-- Public collection detail
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', '', false);
select set_config('request.jwt.claim.role', 'anon', false);
set role anon;

select tests.assert_true(
  public.get_collection('60000000-0000-4000-8000-000000000001') is not null,
  'anonymous visitors can read a public collection');
select tests.assert_int(
  jsonb_array_length(
    public.get_collection('60000000-0000-4000-8000-000000000001') -> 'items'
  ), 3, 'public collection exposes its published items');
select tests.assert_text(
  public.get_collection('60000000-0000-4000-8000-000000000001')->>'owner_username',
  'sample_contributor', 'public collection identifies the owner');
select tests.assert_true(
  not (public.get_collection('60000000-0000-4000-8000-000000000001')->>'is_owner')::boolean,
  'anonymous visitor is not the collection owner');
select tests.expect_error(
  'select public.list_my_collections()', '42501',
  'anonymous visitors cannot list private collection indexes');

select tests.assert_int(
  (public.get_public_profile('00000000-0000-4000-8000-000000000003')->>'followers')::integer,
  1, 'public profile reports follower count');
select tests.assert_true(
  not (public.get_public_profile('00000000-0000-4000-8000-000000000003')->>'is_following')::boolean,
  'anonymous visitors are never following a profile');
select tests.assert_true(
  not (public.get_public_profile('00000000-0000-4000-8000-000000000003')->>'is_self')::boolean,
  'anonymous public profile is not self');
reset role;

-- ---------------------------------------------------------------------------
-- Member follow state
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000004', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;

select tests.assert_true(
  (public.get_public_profile('00000000-0000-4000-8000-000000000003')->>'is_following')::boolean,
  'member sees an existing follow state');
select tests.assert_int(
  (public.get_public_profile('00000000-0000-4000-8000-000000000003')->>'following')::integer,
  0, 'followee following count is independent of followers');
select tests.assert_int(
  jsonb_array_length(public.list_my_collections() -> 'items'),
  0, 'new member has no collections');

select tests.assert_true(
  not (public.toggle_follow('00000000-0000-4000-8000-000000000003')->>'following')::boolean,
  'member can unfollow a profile');
select tests.assert_int(
  (public.get_public_profile('00000000-0000-4000-8000-000000000003')->>'followers')::integer,
  0, 'unfollowing decrements follower count');
select tests.assert_true(
  (public.toggle_follow('00000000-0000-4000-8000-000000000003')->>'following')::boolean,
  'member can follow a profile again');
select tests.assert_int(
  (public.get_public_profile('00000000-0000-4000-8000-000000000003')->>'followers')::integer,
  1, 'following restores follower count');
select tests.assert_true(
  (public.get_public_profile('00000000-0000-4000-8000-000000000004')->>'is_self')::boolean,
  'profile self flag is true for the signed-in member');
reset role;

-- ---------------------------------------------------------------------------
-- Collection ownership boundaries
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000003', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;

select tests.assert_int(
  jsonb_array_length(public.list_my_collections() -> 'items'),
  1, 'contributor sees their seeded collection');
select tests.assert_int(
  (public.list_my_collections() -> 'items' -> 0 ->> 'item_count')::integer,
  3, 'collection index includes item counts');

do $$
declare
  v_id uuid;
begin
  v_id := public.create_collection(
    'Contributor private RPC collection',
    'Synthetic private collection for RPC tests.',
    false
  );
  perform public.add_to_collection(
    v_id, '10000000-0000-4000-8000-000000000002', 'Private display note'
  );
  perform set_config('tests.private_collection', v_id::text, false);
end;
$$;

select tests.assert_int(
  jsonb_array_length(public.list_my_collections() -> 'items'),
  2, 'creating a collection updates the owner index');
select tests.assert_true(
  (public.get_collection(current_setting('tests.private_collection')::uuid)
    -> 'items' -> 0 ->> 'note') is not null,
  'private collection item notes are returned to the owner');
reset role;

select set_config('request.jwt.claim.sub', '', false);
select set_config('request.jwt.claim.role', 'anon', false);
set role anon;
select tests.assert_true(
  public.get_collection(current_setting('tests.private_collection')::uuid) is null,
  'anonymous visitors cannot read a private collection');
reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000004', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;
select tests.assert_true(
  public.get_collection(current_setting('tests.private_collection')::uuid) is null,
  'other members cannot read a private collection');
reset role;

-- ---------------------------------------------------------------------------
-- Evidence contributions require real HTTP(S) sources
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000003', false);
select set_config('request.jwt.claim.role', 'authenticated', false);
set role authenticated;

select tests.expect_error(
  $$select public.submit_contribution(
      'evidence',
      '{"rule_id":"20000000-0000-4000-8000-000000000001",'
      '"evidence":{"title":"Placeholder source",'
      '"source_url":"https://example.com/not-real",'
      '"evidence_type":"community_report"}}'::jsonb)$$,
  '22023', 'evidence rejects placeholder example.com sources');
select tests.expect_error(
  $$select public.submit_contribution(
      'evidence',
      '{"rule_id":"20000000-0000-4000-8000-000000000001",'
      '"evidence":{"title":"Unsafe source",'
      '"source_url":"javascript:alert(1)",'
      '"evidence_type":"community_report"}}'::jsonb)$$,
  '22023', 'evidence rejects non-HTTP source URLs');
select tests.assert_true(
  public.submit_contribution(
    'evidence',
    '{"rule_id":"20000000-0000-4000-8000-000000000001",'
    '"evidence":{"title":"Valid local evidence",'
    '"source_url":"https://docs.local/evidence/valid",'
    '"evidence_type":"community_report"}}'::jsonb
  ) is not null,
  'evidence accepts a real HTTP(S) source URL');

rollback;
