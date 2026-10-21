-- 10: structural invariants and privilege baseline.
\set ON_ERROR_STOP on

select tests.assert_int((select count(*)::integer from public.products), 7, 'seeded products');
select tests.assert_int(
  (select count(*)::integer from public.compatibility_rules where status = 'published'),
  4, 'published seed rules');
select tests.assert_int(
  (select count(*)::integer from public.rule_evidence), 4, 'seed evidence rows');
select tests.assert_int((select count(*)::integer from public.profiles), 4, 'seed profiles');
select tests.assert_int(
  (select count(*)::integer from public.guides where status = 'published'), 1, 'seed guides');

select tests.assert_int((
  select count(*)::integer
  from public.compatibility_rules r
  where r.status = 'published'
    and not exists (select 1 from public.rule_evidence e where e.rule_id = r.id)
), 0, 'every published rule has evidence');

select tests.assert_int((
  select count(*)::integer
  from public.rule_evidence
  where source_url ~* '^https?://(www\.)?example\.(com|org|net)'
), 0, 'no example.com citations');

select tests.assert_int((
  select count(*)::integer
  from public.analytics_events
  where char_length(session_hash) <> 64
), 0, 'analytics hashes are SHA-256 length');

select tests.assert_true(
  (select relrowsecurity from pg_class where oid = 'public.products'::regclass),
  'products RLS enabled');
select tests.assert_true(
  (select relrowsecurity from pg_class where oid = 'public.rule_votes'::regclass),
  'rule_votes RLS enabled');
select tests.assert_true(
  (select relrowsecurity from pg_class where oid = 'public.analytics_events'::regclass),
  'analytics RLS enabled');
select tests.assert_true(
  (select relrowsecurity from pg_class where oid = 'public.points_ledger'::regclass),
  'ledger RLS enabled');

select tests.assert_true(not has_table_privilege('anon', 'public.products', 'select'),
  'anon has no base-table product grant');
select tests.assert_true(not has_table_privilege('anon', 'public.profiles', 'select'),
  'anon has no base-table profile grant');
select tests.assert_true(has_table_privilege('anon', 'public.products_public', 'select'),
  'anon can read the public product projection');
select tests.assert_true(has_table_privilege('anon', 'public.rules_public', 'select'),
  'anon can read the public rule projection');
select tests.assert_true(not has_table_privilege('anon', 'public.rule_votes', 'select'),
  'anon cannot read individual votes');
select tests.assert_true(has_table_privilege('anon', 'public.rule_vote_counts', 'select'),
  'anon can read aggregate vote counts');
select tests.assert_true(
  not has_column_privilege('authenticated', 'public.profiles', 'moderation_note', 'select'),
  'moderation notes are not exposed by column grants');
select tests.assert_true(
  has_column_privilege('authenticated', 'public.profiles', 'display_name', 'select'),
  'safe profile columns remain readable');
select tests.assert_int(
  (select count(*)::integer from storage.buckets where id in ('media', 'evidence')), 2,
  'storage buckets are provisioned by migrations');
select tests.assert_true(not has_table_privilege('authenticated', 'public.analytics_events', 'select'),
  'analytics raw events are not client-readable');

select tests.assert_true(
  has_function_privilege('anon', 'public.check_compatibility(uuid,uuid)', 'execute'),
  'anon can evaluate compatibility');
select tests.assert_true(
  has_function_privilege('authenticated', 'public.admin_dashboard()', 'execute'),
  'admin RPC is callable only to the extent its internal role check allows');
select tests.assert_true(
  not has_function_privilege('authenticated',
    'private.award_points(uuid,integer,text,text,uuid,text)', 'execute'),
  'clients cannot award points directly');
select tests.assert_true(
  not has_function_privilege('authenticated',
    'private.enforce_rate_limit(text,integer,interval)', 'execute'),
  'clients cannot reset rate limits');

select tests.assert_int((
  select count(*)::integer
  from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
  where n.nspname = 'public' and c.relkind = 'r' and not c.relrowsecurity
), 0, 'all public tables have RLS');

select tests.assert_int((
  select count(*)::integer
  from pg_type t
  join pg_enum e on e.enumtypid = t.oid
  where t.typname = 'relationship_type'
), 5, 'five relationship values');

select tests.assert_int((
  select count(*)::integer
  from pg_type t
  join pg_enum e on e.enumtypid = t.oid
  where t.typname = 'verification_level'
), 4, 'four verification levels');

select tests.assert_int(
  (select count(*)::integer from public.rule_versions), 9,
  'rule inserts and publications produced version snapshots');

select tests.assert_true(
  (select count(*) from public.audit_logs) > 0,
  'audit log is being written');
