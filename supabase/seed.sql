-- Deterministic local development seed.
-- NEVER run this against production. All product names, compatibility claims,
-- and evidence rows are synthetic fixtures whose titles say so explicitly.
-- Local accounts use the password: CompatLocal!2026

begin;

-- Resolve pgcrypto in either `public` (plain PostgreSQL tests) or `extensions`
-- (Supabase) without hard-coding one environment.
create or replace function pg_temp.local_password(p_password text)
returns text
language plpgsql
set search_path = public, extensions
as $$
begin
  return crypt(p_password, gen_salt('bf'));
end;
$$;

-- ---------------------------------------------------------------------------
-- Local accounts and profiles
-- ---------------------------------------------------------------------------

insert into auth.users (
  id, instance_id, aud, role, email, encrypted_password, email_confirmed_at,
  raw_app_meta_data, raw_user_meta_data, created_at, updated_at
)
values
  ('00000000-0000-4000-8000-000000000001', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'admin@compatchecker.test',
   pg_temp.local_password('CompatLocal!2026'), now(),
   '{"provider":"email","providers":["email"]}'::jsonb,
   '{"display_name":"Local Administrator"}'::jsonb, now(), now()),
  ('00000000-0000-4000-8000-000000000002', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'trusted@compatchecker.test',
   pg_temp.local_password('CompatLocal!2026'), now(),
   '{"provider":"email","providers":["email"]}'::jsonb,
   '{"display_name":"Trusted Reviewer"}'::jsonb, now(), now()),
  ('00000000-0000-4000-8000-000000000003', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'contributor@compatchecker.test',
   pg_temp.local_password('CompatLocal!2026'), now(),
   '{"provider":"email","providers":["email"]}'::jsonb,
   '{"display_name":"Sample Contributor"}'::jsonb, now(), now()),
  ('00000000-0000-4000-8000-000000000004', '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated', 'member@compatchecker.test',
   pg_temp.local_password('CompatLocal!2026'), now(),
   '{"provider":"email","providers":["email"]}'::jsonb,
   '{"display_name":"Sample Member"}'::jsonb, now(), now())
on conflict (id) do nothing;

insert into auth.identities (user_id, identity_data, provider, provider_id, last_sign_in_at)
select u.id,
       jsonb_build_object('sub', u.id::text, 'email', u.email, 'email_verified', true),
       'email', u.id::text, now()
from auth.users u
where u.id in (
  '00000000-0000-4000-8000-000000000001',
  '00000000-0000-4000-8000-000000000002',
  '00000000-0000-4000-8000-000000000003',
  '00000000-0000-4000-8000-000000000004'
)
on conflict (provider_id, provider) do nothing;

update public.profiles p
set username = v.username,
    display_name = v.display_name,
    role = v.role::public.app_role,
    locale = v.locale
from (values
  ('00000000-0000-4000-8000-000000000001'::uuid, 'local_admin'::text, 'Local Administrator', 'admin', 'en'),
  ('00000000-0000-4000-8000-000000000002'::uuid, 'trusted_reviewer'::text, 'Trusted Reviewer', 'trusted', 'en'),
  ('00000000-0000-4000-8000-000000000003'::uuid, 'sample_contributor'::text, 'Sample Contributor', 'contributor', 'ur'),
  ('00000000-0000-4000-8000-000000000004'::uuid, 'sample_member'::text, 'Sample Member', 'member', 'en')
) as v(id, username, display_name, role, locale)
where p.id = v.id;

-- ---------------------------------------------------------------------------
-- Synthetic catalog fixtures
-- ---------------------------------------------------------------------------

insert into public.products (
  id, slug, name, brand_id, category_id, model_number, release_year,
  summary, technical_notes, connector_type, form_factor, status, created_by
)
select v.id, v.slug, v.name, b.id, c.id, v.model_number, v.release_year,
       v.summary, v.technical_notes, v.connector_type, v.form_factor,
       'published', '00000000-0000-4000-8000-000000000001'
from (values
  ('10000000-0000-4000-8000-000000000001'::uuid, 'aurora-x1'::text,
   'Aurora X1 (sample phone)', 'sample-electronics'::text, 'smartphones'::text,
   'AX1-100', 2026,
   'Synthetic seed phone used to exercise the catalog and compatibility workflows. Not a real device.',
   'All hardware details in this record are invented for local testing.',
   'USB-C (sample)', 'Sample slab phone'),
  ('10000000-0000-4000-8000-000000000002'::uuid, 'aurora-x1-display'::text,
   'Aurora X1 display assembly (sample)', 'sample-electronics'::text, 'replacement-displays'::text,
   'AX1-DSP', 2026,
   'Synthetic replacement display fixture. Do not use for purchasing or repair decisions.',
   'Seed-only connector and calibration notes.', 'Sample 40-pin FPC', 'Display assembly'),
  ('10000000-0000-4000-8000-000000000003'::uuid, 'aurora-x1-battery'::text,
   'Aurora X1 battery pack (sample)', 'sample-electronics'::text, 'batteries'::text,
   'AX1-BAT', 2026,
   'Synthetic battery fixture for local development.', 'Seed-only capacity and voltage.',
   'Sample battery flex', 'Pouch cell'),
  ('10000000-0000-4000-8000-000000000004'::uuid, 'aurora-y2'::text,
   'Aurora Y2 (sample phone)', 'sample-electronics'::text, 'smartphones'::text,
   'AY2-200', 2026,
   'Synthetic second phone fixture used to demonstrate explicit incompatibility.',
   'Seed-only chassis and connector details.', 'USB-C (sample)', 'Sample compact phone'),
  ('10000000-0000-4000-8000-000000000005'::uuid, 'aurora-y2-display'::text,
   'Aurora Y2 display assembly (sample)', 'sample-electronics'::text, 'replacement-displays'::text,
   'AY2-DSP', 2026,
   'Synthetic Y2 display fixture. Its seed rule demonstrates explicit incompatibility.',
   'Seed-only connector details.', 'Sample 36-pin FPC', 'Display assembly'),
  ('10000000-0000-4000-8000-000000000006'::uuid, 'aurora-x1-usb-c-port'::text,
   'Aurora X1 USB-C port board (sample)', 'sample-electronics'::text, 'charging-ports'::text,
   'AX1-USB', 2026,
   'Synthetic charging-port fixture for local development.', 'Seed-only board details.',
   'Sample USB-C daughterboard', 'Sub-board'),
  ('10000000-0000-4000-8000-000000000007'::uuid, 'aurora-x1-camera'::text,
   'Aurora X1 rear camera (sample)', 'sample-electronics'::text, 'cameras'::text,
   'AX1-CAM', 2026,
   'Synthetic camera fixture. Its rule is intentionally left in draft.',
   'Seed-only camera details.', 'Sample camera flex', 'Camera module')
) as v(id, slug, name, brand_slug, category_slug, model_number, release_year,
       summary, technical_notes, connector_type, form_factor)
join public.brands b on b.slug = v.brand_slug
join public.categories c on c.slug = v.category_slug
on conflict (id) do nothing;

insert into public.product_aliases (product_id, alias)
values
  ('10000000-0000-4000-8000-000000000001', 'AX1'),
  ('10000000-0000-4000-8000-000000000001', 'Aurora X1 sample'),
  ('10000000-0000-4000-8000-000000000002', 'AX1 display'),
  ('10000000-0000-4000-8000-000000000003', 'AX1 battery'),
  ('10000000-0000-4000-8000-000000000004', 'AY2'),
  ('10000000-0000-4000-8000-000000000005', 'AY2 display')
on conflict do nothing;

insert into public.product_specs (product_id, label, value, unit, is_critical, sort_order)
values
  ('10000000-0000-4000-8000-000000000001', 'Display connector', 'Sample 40-pin FPC', null, true, 10),
  ('10000000-0000-4000-8000-000000000001', 'Battery model', 'AX1-BAT', null, true, 20),
  ('10000000-0000-4000-8000-000000000002', 'Panel type', 'Sample OLED', null, true, 10),
  ('10000000-0000-4000-8000-000000000002', 'Connector', 'Sample 40-pin FPC', null, true, 20),
  ('10000000-0000-4000-8000-000000000003', 'Capacity', '4200', 'mAh', false, 10),
  ('10000000-0000-4000-8000-000000000003', 'Nominal voltage', '3.87', 'V', true, 20),
  ('10000000-0000-4000-8000-000000000005', 'Connector', 'Sample 36-pin FPC', null, true, 10),
  ('10000000-0000-4000-8000-000000000006', 'Connector', 'Sample USB-C daughterboard', null, true, 10),
  ('10000000-0000-4000-8000-000000000007', 'Sensor', 'Sample 50 MP sensor', null, false, 10)
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- Synthetic compatibility rules and evidence
-- ---------------------------------------------------------------------------

insert into public.compatibility_rules (
  id, subject_product_id, object_product_id, relationship, verification,
  confidence, risk, summary, technical_details, required_modifications,
  status, created_by
)
values
  ('20000000-0000-4000-8000-000000000001',
   '10000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001',
   'fits', 'experimental', 20, 'low',
   'SEED DATA: synthetic display-fit claim for workflow testing, not a real compatibility finding.',
   'This claim exists only in the local seed. It has no evidentiary value.',
   'None. Do not use for real repairs.', 'draft',
   '00000000-0000-4000-8000-000000000001'),
  ('20000000-0000-4000-8000-000000000002',
   '10000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001',
   'fits', 'experimental', 20, 'moderate',
   'SEED DATA: synthetic battery-fit claim for workflow testing, not a real compatibility finding.',
   'This claim exists only in the local seed. It has no evidentiary value.',
   'None. Do not use for real repairs.', 'draft',
   '00000000-0000-4000-8000-000000000001'),
  ('20000000-0000-4000-8000-000000000003',
   '10000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000005',
   'not_compatible_with', 'experimental', 15, 'high',
   'SEED DATA: synthetic incompatibility used to test precedence of explicit negative rules.',
   'This claim exists only in the local seed. It has no evidentiary value.',
   null, 'draft',
   '00000000-0000-4000-8000-000000000001'),
  ('20000000-0000-4000-8000-000000000004',
   '10000000-0000-4000-8000-000000000006', '10000000-0000-4000-8000-000000000001',
   'fits', 'experimental', 20, 'low',
   'SEED DATA: synthetic charging-port claim for workflow testing, not a real finding.',
   'This claim exists only in the local seed. It has no evidentiary value.',
   'None. Do not use for real repairs.', 'draft',
   '00000000-0000-4000-8000-000000000001'),
  ('20000000-0000-4000-8000-000000000005',
   '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000001',
   'fits', 'needs_verification', 10, 'moderate',
   'SEED DATA: draft camera claim awaiting evidence and review.',
   'No evidence is attached. It must remain unpublished.',
   null, 'draft',
   '00000000-0000-4000-8000-000000000001')
on conflict (id) do nothing;

insert into public.rule_evidence (
  id, rule_id, evidence_type, title, source_url, publisher, excerpt, added_by
)
values
  ('30000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001',
   'other', 'SYNTHETIC SEED FIXTURE - not factual evidence',
   'https://invalid.local/seed/display-fit', 'Compat Checker local seed',
   'This row exists only to exercise the evidence workflow. The URL is intentionally invalid.',
   '00000000-0000-4000-8000-000000000001'),
  ('30000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000002',
   'other', 'SYNTHETIC SEED FIXTURE - not factual evidence',
   'https://invalid.local/seed/battery-fit', 'Compat Checker local seed',
   'This row exists only to exercise the evidence workflow. The URL is intentionally invalid.',
   '00000000-0000-4000-8000-000000000001'),
  ('30000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000003',
   'other', 'SYNTHETIC SEED FIXTURE - not factual evidence',
   'https://invalid.local/seed/display-incompatibility', 'Compat Checker local seed',
   'This row exists only to exercise the evidence workflow. The URL is intentionally invalid.',
   '00000000-0000-4000-8000-000000000001'),
  ('30000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000004',
   'other', 'SYNTHETIC SEED FIXTURE - not factual evidence',
   'https://invalid.local/seed/port-fit', 'Compat Checker local seed',
   'This row exists only to exercise the evidence workflow. The URL is intentionally invalid.',
   '00000000-0000-4000-8000-000000000001')
on conflict (id) do nothing;

update public.compatibility_rules r
set status = 'published',
    reviewed_by = '00000000-0000-4000-8000-000000000001',
    published_at = now()
where r.id in (
  '20000000-0000-4000-8000-000000000001',
  '20000000-0000-4000-8000-000000000002',
  '20000000-0000-4000-8000-000000000003',
  '20000000-0000-4000-8000-000000000004'
)
and r.status = 'draft';

-- ---------------------------------------------------------------------------
-- Guides
-- ---------------------------------------------------------------------------

insert into public.guides (
  id, slug, title, summary, body_md, difficulty, status, product_id, created_by, published_at
)
values (
  '40000000-0000-4000-8000-000000000001',
  'aurora-x1-display-removal-sample',
  'Aurora X1 display removal (synthetic sample)',
  'A local-only walkthrough that demonstrates the guide structure. It is not repair advice.',
  '# Synthetic sample guide

This guide exists only to test formatting, steps, comments, and localization. It does not describe a real device.

Always consult an official service manual for real hardware.',
  'moderate', 'published', '10000000-0000-4000-8000-000000000001',
  '00000000-0000-4000-8000-000000000001', now()
)
on conflict (id) do nothing;

insert into public.guide_steps (guide_id, position, title, body, image_path)
values
  ('40000000-0000-4000-8000-000000000001', 1, 'Prepare a sample workspace',
   'This is placeholder text for local UI testing only.', null),
  ('40000000-0000-4000-8000-000000000001', 2, 'Review the sample connector',
   'No real hardware should be opened using this guide.', null),
  ('40000000-0000-4000-8000-000000000001', 3, 'Record a synthetic result',
   'Use the contribution form to see how structured review works.', null)
on conflict (guide_id, position) do nothing;

-- ---------------------------------------------------------------------------
-- Community fixtures
-- ---------------------------------------------------------------------------

insert into public.product_requests (
  id, title, description, category_id, status, created_by, is_anonymous
)
select '50000000-0000-4000-8000-000000000001', 'Add an Aurora X1 speaker module (sample)',
       'Synthetic local request used to demonstrate request voting and admin triage.',
       c.id, 'open', '00000000-0000-4000-8000-000000000004', true
from public.categories c
where c.slug = 'other-components'
  and not exists (select 1 from public.product_requests r where r.id = '50000000-0000-4000-8000-000000000001');

insert into public.request_votes (request_id, user_id)
values
  ('50000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000003'),
  ('50000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000002')
on conflict do nothing;

insert into public.comments (id, target_type, target_id, user_id, body)
values
  ('70000000-0000-4000-8000-000000000001', 'product',
   '10000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000004',
   'Seed comment: I am testing whether the product discussion renders correctly.'),
  ('70000000-0000-4000-8000-000000000002', 'product',
   '10000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000003',
   'Seed reply: the sample workflow is working as expected.')
on conflict (id) do nothing;

update public.comments
set parent_id = '70000000-0000-4000-8000-000000000001'
where id = '70000000-0000-4000-8000-000000000002';

insert into public.collections (id, owner_id, title, description, is_public)
values (
  '60000000-0000-4000-8000-000000000001',
  '00000000-0000-4000-8000-000000000003',
  'Aurora X1 sample teardown',
  'Synthetic local collection used to test public collection rendering.',
  true
)
on conflict (id) do nothing;

insert into public.collection_items (collection_id, product_id, note)
values
  ('60000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'Sample phone'),
  ('60000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000002', 'Sample display'),
  ('60000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000003', 'Sample battery')
on conflict do nothing;

insert into public.follows (follower_id, followee_id)
values ('00000000-0000-4000-8000-000000000004', '00000000-0000-4000-8000-000000000003')
on conflict do nothing;

insert into public.notifications (user_id, type, title, body, link)
values (
  '00000000-0000-4000-8000-000000000003',
  'system', 'New follower', 'Sample Member started following you.', '/profile'
)
on conflict do nothing;

-- ---------------------------------------------------------------------------
-- Reputation and analytics fixtures
-- ---------------------------------------------------------------------------

insert into public.points_ledger (user_id, delta, reason, reference_type, reference_id, dedupe_key)
values
  ('00000000-0000-4000-8000-000000000003', 2, 'comment_created', 'comment',
   '70000000-0000-4000-8000-000000000002', 'seed:comment:70000000-0000-4000-8000-000000000002'),
  ('00000000-0000-4000-8000-000000000003', 1, 'vote_cast', 'product_request',
   '50000000-0000-4000-8000-000000000001', 'seed:request-vote:contributor'),
  ('00000000-0000-4000-8000-000000000004', 2, 'comment_created', 'comment',
   '70000000-0000-4000-8000-000000000001', 'seed:comment:70000000-0000-4000-8000-000000000001')
on conflict (dedupe_key) do nothing;

insert into public.analytics_events (
  event_type, entity_type, entity_id, session_hash, properties
)
values
  ('product_view', 'product', '10000000-0000-4000-8000-000000000001',
   repeat('a', 64), '{}'::jsonb),
  ('product_view', 'product', '10000000-0000-4000-8000-000000000001',
   repeat('b', 64), '{}'::jsonb),
  ('product_view', 'product', '10000000-0000-4000-8000-000000000001',
   repeat('c', 64), '{}'::jsonb),
  ('search', 'search', null, repeat('d', 64), '{"query":"Aurora X1"}'::jsonb)
on conflict do nothing;

commit;
