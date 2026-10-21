-- 008: privacy-preserving product analytics and aggregate admin reporting.
-- No user id, email, IP address, or raw session identifier is stored.

create table public.analytics_events (
  id bigserial primary key,
  event_type text not null check (event_type in (
    'product_view', 'rule_view', 'search', 'checker_run',
    'guide_view', 'request_view', 'collection_view'
  )),
  entity_type text check (entity_type in (
    'product', 'rule', 'guide', 'request', 'collection', 'search'
  )),
  entity_id uuid,
  session_hash text not null check (char_length(session_hash) = 64),
  properties jsonb not null default '{}'::jsonb
    check (jsonb_typeof(properties) = 'object' and pg_column_size(properties) <= 2000),
  created_at timestamptz not null default now()
);

create index analytics_events_created_idx on public.analytics_events (created_at desc);
create index analytics_events_type_idx on public.analytics_events (event_type, created_at desc);
create index analytics_events_entity_idx on public.analytics_events (entity_type, entity_id);

alter table public.analytics_events enable row level security;
-- Intentionally no policies or client grants. Ingestion and reads are RPC-only.

-- pgcrypto lives in `public` on a plain PostgreSQL install and in `extensions`
-- on Supabase. This resolver works in both environments without exposing either
-- schema to clients.
create or replace function private.analytics_hash(p_value text)
returns text
language plpgsql
immutable
set search_path = public, extensions
as $$
begin
  return encode(digest(p_value, 'sha256'), 'hex');
end;
$$;

create or replace function public.track_event(
  p_event_type text,
  p_entity_type text default null,
  p_entity_id uuid default null,
  p_session_id text default null,
  p_properties jsonb default '{}'::jsonb
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_session_hash text;
  v_properties jsonb;
begin
  if p_event_type not in (
    'product_view', 'rule_view', 'search', 'checker_run',
    'guide_view', 'request_view', 'collection_view'
  ) then
    raise exception using errcode = '22023', message = 'Invalid analytics event';
  end if;

  if p_entity_type is not null and p_entity_type not in (
    'product', 'rule', 'guide', 'request', 'collection', 'search'
  ) then
    raise exception using errcode = '22023', message = 'Invalid analytics entity';
  end if;

  if p_session_id is null or char_length(p_session_id) < 16 or char_length(p_session_id) > 128 then
    raise exception using errcode = '22023', message = 'A session identifier is required';
  end if;

  if jsonb_typeof(coalesce(p_properties, '{}'::jsonb)) <> 'object' then
    raise exception using errcode = '22023', message = 'Properties must be an object';
  end if;

  -- Explicitly strip fields that could identify a person.
  v_properties := coalesce(p_properties, '{}'::jsonb)
    - 'email' - 'user_id' - 'userId' - 'ip' - 'token' - 'authorization';
  if pg_column_size(v_properties) > 2000 then
    raise exception using errcode = '22023', message = 'Analytics properties are too large';
  end if;

  v_session_hash := private.analytics_hash(
    coalesce(p_session_id, '') || ':compat-checker-analytics'
  );

  perform private.enforce_rate_limit(
    'analytics:' || v_session_hash, 120, interval '1 minute'
  );

  insert into public.analytics_events (
    event_type, entity_type, entity_id, session_hash, properties
  )
  values (p_event_type, p_entity_type, p_entity_id, v_session_hash, v_properties);

  return true;
end;
$$;

create or replace function public.public_trending_products(p_limit integer default 10)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_limit integer := least(greatest(coalesce(p_limit, 10), 1), 30);
  v_items jsonb;
begin
  select coalesce(jsonb_agg(jsonb_build_object(
    'id', p.id, 'name', p.name, 'slug', p.slug,
    'brand_name', p.brand_name, 'category_slug', p.category_slug,
    'summary', p.summary, 'view_count', t.views
  ) order by t.views desc, p.name), '[]'::jsonb)
  into v_items
  from (
    select entity_id, count(*)::integer as views
    from public.analytics_events
    where event_type = 'product_view'
      and entity_type = 'product'
      and created_at > now() - interval '30 days'
      and entity_id is not null
    group by entity_id
    having count(*) >= 3
    order by views desc
    limit v_limit
  ) t
  join public.products_public p on p.id = t.entity_id;

  return jsonb_build_object('items', v_items, 'window_days', 30, 'minimum_views', 3);
end;
$$;

create or replace function public.admin_analytics(p_days integer default 30)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_days integer := least(greatest(coalesce(p_days, 30), 1), 365);
  v_since timestamptz := now() - make_interval(days => least(greatest(coalesce(p_days, 30), 1), 365));
  v_daily jsonb;
  v_products jsonb;
  v_searches jsonb;
  v_totals jsonb;
begin
  perform private.assert_admin();

  select coalesce(jsonb_agg(jsonb_build_object(
    'date', d.day, 'views', d.views, 'searches', d.searches, 'checker_runs', d.checker_runs
  ) order by d.day), '[]'::jsonb)
  into v_daily
  from (
    select date_trunc('day', e.created_at)::date as day,
           count(*) filter (where e.event_type = 'product_view')::integer as views,
           count(*) filter (where e.event_type = 'search')::integer as searches,
           count(*) filter (where e.event_type = 'checker_run')::integer as checker_runs
    from public.analytics_events e
    where e.created_at >= v_since
    group by 1
  ) d;

  select coalesce(jsonb_agg(jsonb_build_object(
    'entity_id', t.entity_id, 'name', p.name, 'views', t.views
  ) order by t.views desc), '[]'::jsonb)
  into v_products
  from (
    select e.entity_id, count(*)::integer as views
    from public.analytics_events e
    where e.entity_type = 'product' and e.event_type = 'product_view'
      and e.created_at >= v_since and e.entity_id is not null
    group by e.entity_id
    order by views desc
    limit 10
  ) t
  left join public.products p on p.id = t.entity_id;

  select coalesce(jsonb_agg(jsonb_build_object(
    'term', s.term, 'count', s.count
  ) order by s.count desc), '[]'::jsonb)
  into v_searches
  from (
    select lower(coalesce(e.properties ->> 'query', '')) as term, count(*)::integer as count
    from public.analytics_events e
    where e.event_type = 'search' and e.created_at >= v_since
      and coalesce(e.properties ->> 'query', '') <> ''
    group by 1
    order by count desc
    limit 10
  ) s;

  select jsonb_build_object(
    'events', count(*)::integer,
    'product_views', count(*) filter (where event_type = 'product_view')::integer,
    'searches', count(*) filter (where event_type = 'search')::integer,
    'checker_runs', count(*) filter (where event_type = 'checker_run')::integer,
    'unique_sessions', count(distinct session_hash)::integer
  ) into v_totals
  from public.analytics_events
  where created_at >= v_since;

  return jsonb_build_object(
    'window_days', v_days,
    'totals', v_totals,
    'daily', v_daily,
    'top_products', v_products,
    'top_searches', v_searches
  );
end;
$$;

comment on table public.analytics_events is
  'Anonymous aggregate analytics. Session identifiers are salted hashes; no PII.';
comment on function public.track_event(text, text, uuid, text, jsonb) is
  'Anonymous event ingestion with rate limiting and PII key stripping.';
