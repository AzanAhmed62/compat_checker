-- 003: catalog, public projections, search, and detail RPCs.

create table public.categories (
  id uuid primary key default gen_random_uuid(),
  slug citext not null unique check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  name text not null check (char_length(btrim(name)) between 1 and 80),
  description text check (description is null or char_length(description) <= 1000),
  icon text,
  sort_order integer not null default 0,
  is_public boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.brands (
  id uuid primary key default gen_random_uuid(),
  slug citext not null unique check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  name text not null check (char_length(btrim(name)) between 1 and 120),
  logo_path text,
  website_url text check (website_url is null or website_url ~ '^https://'),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.products (
  id uuid primary key default gen_random_uuid(),
  slug citext not null unique check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  name text not null check (char_length(btrim(name)) between 1 and 200),
  brand_id uuid not null references public.brands(id) on delete restrict,
  category_id uuid not null references public.categories(id) on delete restrict,
  model_number text check (model_number is null or char_length(model_number) <= 80),
  release_year integer check (release_year is null or release_year between 1990 and 2100),
  summary text check (summary is null or char_length(summary) <= 2000),
  technical_notes text check (technical_notes is null or char_length(technical_notes) <= 8000),
  connector_type text check (connector_type is null or char_length(connector_type) <= 200),
  form_factor text check (form_factor is null or char_length(form_factor) <= 300),
  status public.product_status not null default 'draft',
  created_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  published_at timestamptz,
  search_document tsvector generated always as (
    setweight(to_tsvector('simple'::regconfig, coalesce(name, '')), 'A') ||
    setweight(to_tsvector('simple'::regconfig, coalesce(model_number, '')), 'A') ||
    setweight(to_tsvector('simple'::regconfig, coalesce(summary, '')), 'B')
  ) stored
);

create index products_brand_idx on public.products (brand_id);
create index products_category_idx on public.products (category_id);
create index products_status_idx on public.products (status);
create index products_search_document_idx on public.products using gin (search_document);
create index products_name_trgm_idx on public.products using gin (name gin_trgm_ops);
create index products_model_trgm_idx on public.products using gin (model_number gin_trgm_ops);

create table public.product_aliases (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.products(id) on delete cascade,
  alias citext not null check (char_length(btrim(alias)) between 1 and 120),
  unique (product_id, alias)
);

create index product_aliases_alias_trgm_idx
  on public.product_aliases using gin (alias gin_trgm_ops);

create table public.product_specs (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.products(id) on delete cascade,
  label text not null check (char_length(btrim(label)) between 1 and 100),
  value text not null check (char_length(btrim(value)) between 1 and 300),
  unit text check (unit is null or char_length(unit) <= 30),
  is_critical boolean not null default false,
  sort_order integer not null default 0,
  unique (product_id, label)
);

create table public.product_media (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.products(id) on delete cascade,
  storage_path text not null check (char_length(storage_path) between 3 and 500),
  alt_text text not null check (char_length(btrim(alt_text)) between 1 and 250),
  status public.media_status not null default 'pending',
  sort_order integer not null default 0,
  created_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now()
);

create index product_media_product_idx on public.product_media (product_id, sort_order);

create trigger categories_set_updated_at
before update on public.categories
for each row execute function private.set_updated_at();

create trigger brands_set_updated_at
before update on public.brands
for each row execute function private.set_updated_at();

create trigger products_set_updated_at
before update on public.products
for each row execute function private.set_updated_at();

create or replace function private.sync_product_published_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.status = 'published' and new.published_at is null then
    new.published_at := now();
  end if;
  return new;
end;
$$;

create trigger products_sync_published_at
before insert or update of status on public.products
for each row execute function private.sync_product_published_at();

alter table public.categories enable row level security;
alter table public.brands enable row level security;
alter table public.products enable row level security;
alter table public.product_aliases enable row level security;
alter table public.product_specs enable row level security;
alter table public.product_media enable row level security;

create policy categories_select_public
on public.categories for select to anon, authenticated
using (is_public or private.is_admin());

create policy categories_admin_write
on public.categories for all to authenticated
using (private.is_admin()) with check (private.is_admin());

create policy brands_select_public
on public.brands for select to anon, authenticated
using (true);

create policy brands_admin_write
on public.brands for all to authenticated
using (private.is_admin()) with check (private.is_admin());

create policy products_select_public
on public.products for select to anon, authenticated
using (status = 'published' or private.is_admin());

create policy products_admin_insert
on public.products for insert to authenticated
with check (private.is_admin());

create policy products_admin_update
on public.products for update to authenticated
using (private.is_admin()) with check (private.is_admin());

create policy products_admin_delete
on public.products for delete to authenticated
using (private.is_admin());

create policy aliases_select_public
on public.product_aliases for select to anon, authenticated
using (
  private.is_admin()
  or exists (
    select 1 from public.products p
    where p.id = product_id and p.status = 'published'
  )
);

create policy aliases_admin_write
on public.product_aliases for all to authenticated
using (private.is_admin()) with check (private.is_admin());

create policy specs_select_public
on public.product_specs for select to anon, authenticated
using (
  private.is_admin()
  or exists (
    select 1 from public.products p
    where p.id = product_id and p.status = 'published'
  )
);

create policy specs_admin_write
on public.product_specs for all to authenticated
using (private.is_admin()) with check (private.is_admin());

create policy media_select_public
on public.product_media for select to anon, authenticated
using (
  private.is_admin()
  or (
    status = 'approved'
    and exists (
      select 1 from public.products p
      where p.id = product_id and p.status = 'published'
    )
  )
);

create policy media_admin_write
on public.product_media for all to authenticated
using (private.is_admin()) with check (private.is_admin());

-- Public projections are owner-rights views with an explicit allow-list of
-- columns and published-only filters. They never expose user identifiers.
create view public.categories_public
with (security_invoker = false)
as
select id, slug, name, description, icon, sort_order
from public.categories
where is_public;

create view public.brands_public
with (security_invoker = false)
as
select id, slug, name, logo_path, website_url
from public.brands;

create view public.products_public
with (security_invoker = false)
as
select
  p.id,
  p.slug,
  p.name,
  p.model_number,
  p.release_year,
  p.summary,
  p.technical_notes,
  p.connector_type,
  p.form_factor,
  p.published_at,
  p.updated_at,
  b.id as brand_id,
  b.slug as brand_slug,
  b.name as brand_name,
  c.id as category_id,
  c.slug as category_slug,
  c.name as category_name,
  coalesce((
    select jsonb_agg(a.alias order by a.alias)
    from public.product_aliases a where a.product_id = p.id
  ), '[]'::jsonb) as aliases,
  coalesce((
    select jsonb_agg(jsonb_build_object(
      'label', s.label, 'value', s.value, 'unit', s.unit,
      'is_critical', s.is_critical
    ) order by s.sort_order, s.label)
    from public.product_specs s where s.product_id = p.id
  ), '[]'::jsonb) as specs,
  coalesce((
    select jsonb_agg(jsonb_build_object(
      'path', m.storage_path, 'alt', m.alt_text
    ) order by m.sort_order)
    from public.product_media m
    where m.product_id = p.id and m.status = 'approved'
  ), '[]'::jsonb) as media
from public.products p
join public.brands b on b.id = p.brand_id
join public.categories c on c.id = p.category_id
where p.status = 'published';

-- Search is server-ranked and paginated. It never synthesises relationships.
create or replace function public.search_products(
  p_query text default '',
  p_category text default null,
  p_brand text default null,
  p_limit integer default 20,
  p_offset integer default 0
)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_term text := btrim(coalesce(p_query, ''));
  v_limit integer := least(greatest(coalesce(p_limit, 20), 1), 50);
  v_offset integer := greatest(coalesce(p_offset, 0), 0);
  v_total integer;
  v_items jsonb;
begin
  if char_length(v_term) > 120 then
    raise exception using errcode = '22023', message = 'Search query is too long';
  end if;

  select count(*)::integer into v_total
  from public.products p
  join public.brands b on b.id = p.brand_id
  join public.categories c on c.id = p.category_id
  where p.status = 'published'
    and (p_category is null or c.slug = p_category)
    and (p_brand is null or b.slug = p_brand)
    and (
      v_term = ''
      or p.search_document @@ plainto_tsquery('simple'::regconfig, v_term)
      or p.name ilike '%' || v_term || '%'
      or coalesce(p.model_number, '') ilike '%' || v_term || '%'
      or exists (
        select 1 from public.product_aliases a
        where a.product_id = p.id and a.alias ilike '%' || v_term || '%'
      )
      or (v_term <> '' and (
        private.text_word_similarity(v_term, p.name) > 0.5
        or private.text_similarity(p.model_number, v_term) > 0.3
        or exists (
          select 1 from public.product_aliases a
          where a.product_id = p.id and private.text_word_similarity(v_term, a.alias::text) > 0.6
        )
      ))
    );

  select coalesce(jsonb_agg(to_jsonb(s) order by s.score desc, s.name), '[]'::jsonb)
  into v_items
  from (
    select
      p.id,
      p.slug,
      p.name,
      p.model_number,
      p.release_year,
      p.summary,
      b.slug as brand_slug,
      b.name as brand_name,
      c.slug as category_slug,
      c.name as category_name,
      (
        select m.storage_path from public.product_media m
        where m.product_id = p.id and m.status = 'approved'
        order by m.sort_order limit 1
      ) as image_path,
      (
        select count(*)::integer from public.compatibility_rules r
        where r.status = 'published'
          and (r.subject_product_id = p.id or r.object_product_id = p.id)
      ) as rule_count,
      case
        when v_term = '' then 0::real
        else
          ts_rank(p.search_document, plainto_tsquery('simple'::regconfig, v_term)) * 4
          + greatest(
              private.text_similarity(p.name, v_term),
              private.text_word_similarity(v_term, p.name),
              private.text_similarity(p.model_number, v_term),
              coalesce((
                select max(private.text_similarity(a.alias::text, v_term))
                from public.product_aliases a where a.product_id = p.id
              ), 0)
            )
      end as score
    from public.products p
    join public.brands b on b.id = p.brand_id
    join public.categories c on c.id = p.category_id
    where p.status = 'published'
      and (p_category is null or c.slug = p_category)
      and (p_brand is null or b.slug = p_brand)
      and (
        v_term = ''
        or p.search_document @@ plainto_tsquery('simple'::regconfig, v_term)
        or p.name ilike '%' || v_term || '%'
        or coalesce(p.model_number, '') ilike '%' || v_term || '%'
        or exists (
          select 1 from public.product_aliases a
          where a.product_id = p.id and a.alias ilike '%' || v_term || '%'
        )
        or (v_term <> '' and (
          private.text_word_similarity(v_term, p.name) > 0.5
          or private.text_similarity(p.model_number, v_term) > 0.3
          or exists (
            select 1 from public.product_aliases a
            where a.product_id = p.id and private.text_word_similarity(v_term, a.alias::text) > 0.6
          )
        ))
      )
    order by score desc, p.name
    limit v_limit offset v_offset
  ) s;

  return jsonb_build_object(
    'items', v_items,
    'total', v_total,
    'limit', v_limit,
    'offset', v_offset,
    'query', v_term
  );
end;
$$;

-- Product detail: public catalog fields plus evidence-backed rules, guides,
-- comments summary, and the caller's favorite state. Unknown rules stay absent.
create or replace function public.get_product_detail(p_product_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_product jsonb;
  v_rules jsonb;
  v_guides jsonb;
  v_comment_count integer;
  v_is_favorite boolean := false;
begin
  select to_jsonb(p) into v_product
  from public.products_public p
  where p.id = p_product_id;

  if v_product is null then
    return null;
  end if;

  select coalesce(jsonb_agg(jsonb_build_object(
    'id', r.id,
    'relationship', r.relationship,
    'verification', r.verification,
    'confidence', r.confidence,
    'risk_level', r.risk,
    'summary', r.summary,
    'direction', case
      when r.subject_product_id = p_product_id then 'outgoing'
      else 'incoming'
    end,
    'other_product_id', case
      when r.subject_product_id = p_product_id then r.object_product_id
      else r.subject_product_id
    end,
    'other_product_name', case
      when r.subject_product_id = p_product_id then op.name
      else sp.name
    end,
    'evidence_count', (
      select count(*)::integer from public.rule_evidence e where e.rule_id = r.id
    ),
    'confirm_count', coalesce(vc.confirms, 0),
    'dispute_count', coalesce(vc.disputes, 0)
  ) order by
    case r.relationship when 'not_compatible_with' then 0 else 1 end,
    r.confidence desc
  ), '[]'::jsonb)
  into v_rules
  from public.compatibility_rules r
  join public.products sp on sp.id = r.subject_product_id
  join public.products op on op.id = r.object_product_id
  left join public.rule_vote_counts vc on vc.rule_id = r.id
  where r.status = 'published'
    and (r.subject_product_id = p_product_id or r.object_product_id = p_product_id);

  select coalesce(jsonb_agg(jsonb_build_object(
    'id', g.id, 'slug', g.slug, 'title', g.title,
    'summary', g.summary, 'difficulty', g.difficulty
  ) order by g.published_at desc), '[]'::jsonb)
  into v_guides
  from public.guides g
  where g.status = 'published' and g.product_id = p_product_id;

  select count(*)::integer into v_comment_count
  from public.comments c
  where c.target_type = 'product'
    and c.target_id = p_product_id
    and c.status = 'visible';

  if auth.uid() is not null then
    select exists (
      select 1 from public.favorites f
      where f.user_id = auth.uid() and f.product_id = p_product_id
    ) into v_is_favorite;
  end if;

  return v_product || jsonb_build_object(
    'rules', v_rules,
    'guides', v_guides,
    'comment_count', v_comment_count,
    'is_favorite', v_is_favorite
  );
end;
$$;

insert into public.categories (slug, name, description, icon, sort_order)
values
  ('smartphones', 'Smartphones', 'Phones and their regional hardware variants.', 'smartphone', 10),
  ('replacement-displays', 'Replacement displays', 'LCD and OLED assemblies, flex cables, and calibration notes.', 'display', 20),
  ('batteries', 'Batteries', 'Packs, cells, BMS boards, and charging constraints.', 'battery', 30),
  ('cameras', 'Camera modules', 'Rear and front camera assemblies and connectors.', 'camera', 40),
  ('charging-ports', 'Charging ports', 'USB-C, Lightning, and dock connector sub-boards.', 'usb', 50),
  ('other-components', 'Other components', 'Speakers, sensors, buttons, and small parts.', 'component', 60)
on conflict (slug) do nothing;

-- Seed brands are neutral and may be extended by administrators.
insert into public.brands (slug, name)
values
  ('generic-oem', 'Generic OEM'),
  ('sample-electronics', 'Sample Electronics')
on conflict (slug) do nothing;

comment on table public.products is
  'Catalog entries. Only published rows are public; drafts stay admin-only.';
comment on view public.products_public is
  'Allow-listed public catalog projection. No author identifiers or drafts.';
comment on function public.search_products(text, text, text, integer, integer) is
  'Server-side ranked search over name, model, and aliases with pagination.';
