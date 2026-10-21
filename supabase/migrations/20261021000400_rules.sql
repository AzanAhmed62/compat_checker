-- 004: explicit compatibility rules, evidence, and immutable version history.
-- A published rule requires evidence and published products. Directional
-- relationships (fits, variant_of) retain orientation; symmetric relationships
-- are stored once in canonical UUID order.

create table public.compatibility_rules (
  id uuid primary key default gen_random_uuid(),
  subject_product_id uuid not null references public.products(id) on delete restrict,
  object_product_id uuid not null references public.products(id) on delete restrict,
  relationship public.relationship_type not null,
  verification public.verification_level not null default 'needs_verification',
  confidence smallint not null default 0 check (confidence between 0 and 100),
  risk public.risk_level not null default 'none',
  summary text not null check (char_length(btrim(summary)) between 10 and 300),
  technical_details text check (technical_details is null or char_length(technical_details) <= 8000),
  required_modifications text check (required_modifications is null or char_length(required_modifications) <= 2000),
  status public.rule_status not null default 'draft',
  created_by uuid references public.profiles(id) on delete set null,
  reviewed_by uuid references public.profiles(id) on delete set null,
  published_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint rule_distinct_products check (subject_product_id <> object_product_id),
  constraint rule_symmetric_canonical check (
    relationship in ('fits', 'variant_of')
    or subject_product_id < object_product_id
  ),
  unique (subject_product_id, object_product_id, relationship)
);

create index rules_subject_idx
  on public.compatibility_rules (subject_product_id, status);
create index rules_object_idx
  on public.compatibility_rules (object_product_id, status);
create index rules_status_idx on public.compatibility_rules (status);
create index rules_pair_idx
  on public.compatibility_rules (least(subject_product_id, object_product_id),
                                  greatest(subject_product_id, object_product_id));

create table public.rule_evidence (
  id uuid primary key default gen_random_uuid(),
  rule_id uuid not null references public.compatibility_rules(id) on delete restrict,
  evidence_type public.evidence_type not null,
  title text not null check (char_length(btrim(title)) between 3 and 250),
  source_url text not null
    check (source_url ~ '^https?://' and source_url !~* '^https?://(www\.)?example\.(com|org|net)'),
  publisher text check (publisher is null or char_length(publisher) <= 150),
  excerpt text check (excerpt is null or char_length(excerpt) <= 4000),
  captured_at date,
  archived_path text,
  added_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  unique (rule_id, source_url)
);

create index rule_evidence_rule_idx on public.rule_evidence (rule_id, created_at);

create table public.rule_versions (
  id uuid primary key default gen_random_uuid(),
  rule_id uuid not null references public.compatibility_rules(id) on delete restrict,
  version integer not null check (version > 0),
  snapshot jsonb not null,
  changed_by uuid,
  change_reason text,
  created_at timestamptz not null default now(),
  unique (rule_id, version)
);

create index rule_versions_rule_idx on public.rule_versions (rule_id, version desc);

create trigger rules_set_updated_at
before update on public.compatibility_rules
for each row execute function private.set_updated_at();

create or replace function private.rule_snapshot(p_rule public.compatibility_rules)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select to_jsonb(p_rule) || jsonb_build_object(
    'evidence', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', e.id,
        'evidence_type', e.evidence_type,
        'title', e.title,
        'source_url', e.source_url,
        'publisher', e.publisher,
        'excerpt', e.excerpt,
        'captured_at', e.captured_at,
        'archived_path', e.archived_path
      ) order by e.created_at)
      from public.rule_evidence e
      where e.rule_id = p_rule.id
    ), '[]'::jsonb)
  );
$$;

create or replace function private.snapshot_rule_version()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_rule public.compatibility_rules;
  v_version integer;
begin
  v_rule := case when tg_op = 'DELETE' then old else new end;

  select coalesce(max(v.version), 0) + 1 into v_version
  from public.rule_versions v where v.rule_id = v_rule.id;

  insert into public.rule_versions (rule_id, version, snapshot, changed_by, change_reason)
  values (
    v_rule.id,
    v_version,
    private.rule_snapshot(v_rule),
    auth.uid(),
    null
  );

  return coalesce(new, old);
end;
$$;

-- Evidence must exist before a rule may be published, and both products must be
-- published. This blocks direct client publication of unsupported claims.
create or replace function private.validate_rule_publication()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.status = 'published' then
    if not exists (select 1 from public.rule_evidence e where e.rule_id = new.id) then
      raise exception using errcode = '23514',
        message = 'A rule cannot be published without at least one evidence item';
    end if;

    if exists (
      select 1 from public.products p
      where p.id in (new.subject_product_id, new.object_product_id)
        and p.status <> 'published'
    ) then
      raise exception using errcode = '23514',
        message = 'Both products must be published before the rule can be published';
    end if;

    if new.reviewed_by is null then
      new.reviewed_by := auth.uid();
    end if;
    if new.published_at is null then
      new.published_at := now();
    end if;
  end if;

  return new;
end;
$$;

create trigger rules_validate_publication
before insert or update on public.compatibility_rules
for each row execute function private.validate_rule_publication();

create trigger rules_snapshot_on_insert
after insert on public.compatibility_rules
for each row execute function private.snapshot_rule_version();

create trigger rules_snapshot_on_update
after update on public.compatibility_rules
for each row execute function private.snapshot_rule_version();

create or replace function private.protect_last_evidence()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_status public.rule_status;
  v_remaining integer;
begin
  select r.status, count(*)::integer - 1
  into v_status, v_remaining
  from public.compatibility_rules r
  join public.rule_evidence e on e.rule_id = r.id
  where r.id = old.rule_id
  group by r.status;

  if v_status = 'published' and v_remaining < 1 then
    raise exception using errcode = '23514',
      message = 'Cannot remove the last evidence item from a published rule';
  end if;

  return old;
end;
$$;

create trigger evidence_protect_last
before delete on public.rule_evidence
for each row execute function private.protect_last_evidence();

alter table public.compatibility_rules enable row level security;
alter table public.rule_evidence enable row level security;
alter table public.rule_versions enable row level security;

create policy rules_select_admin
on public.compatibility_rules for select to authenticated
using (private.is_admin());

create policy rules_admin_insert
on public.compatibility_rules for insert to authenticated
with check (private.is_admin());

create policy rules_admin_update
on public.compatibility_rules for update to authenticated
using (private.is_admin()) with check (private.is_admin());

create policy evidence_select_admin
on public.rule_evidence for select to authenticated
using (private.is_admin());

create policy evidence_admin_insert
on public.rule_evidence for insert to authenticated
with check (private.is_admin());

create policy evidence_admin_update
on public.rule_evidence for update to authenticated
using (private.is_admin()) with check (private.is_admin());

create policy evidence_admin_delete
on public.rule_evidence for delete to authenticated
using (private.is_admin());

create policy versions_select_admin
on public.rule_versions for select to authenticated
using (private.is_admin());

-- Version rows are written only by SECURITY DEFINER triggers.
create policy versions_insert_trigger
on public.rule_versions for insert to authenticated
with check (false);

create trigger rules_audit
after insert or update on public.compatibility_rules
for each row execute function private.audit_row_change();

create trigger evidence_audit
after insert or update or delete on public.rule_evidence
for each row execute function private.audit_row_change();

comment on table public.compatibility_rules is
  'Explicit compatibility claims only. No client-side inference is permitted.';
comment on table public.rule_evidence is
  'Source citations. A published rule must retain at least one evidence item.';
comment on table public.rule_versions is
  'Append-only snapshots used for history and admin rollback.';
