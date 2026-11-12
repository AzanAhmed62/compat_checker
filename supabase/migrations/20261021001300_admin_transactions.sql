-- 013: atomic administrative writes and faithful rule/evidence history.
--
-- The admin console previously saved products, guides, and rules through
-- several independent PostgREST requests. A failure part-way through left
-- partial rows behind. Rule evidence was edited after the rule snapshot was
-- taken, so a published rule that stayed published never received a final
-- snapshot containing its new evidence.
--
-- This migration adds server-authorized, single-transaction RPCs and makes the
-- rule snapshot represent the final resulting state exactly once. Applied
-- migrations remain untouched; the functions below replace earlier bodies.

-- ---------------------------------------------------------------------------
-- Snapshot control
-- ---------------------------------------------------------------------------

-- The RPCs suppress intermediate snapshots while they stage evidence, then
-- perform one final rule update that creates the authoritative version row.
create or replace function private.snapshot_rule_version()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_rule public.compatibility_rules;
  v_version integer;
  v_reason text;
begin
  if coalesce(current_setting('compat.suppress_rule_snapshot', true), '') = 'on' then
    return coalesce(new, old);
  end if;

  v_rule := case when tg_op = 'DELETE' then old else new end;
  v_reason := nullif(current_setting('compat.rule_change_reason', true), '');

  select coalesce(max(v.version), 0) + 1 into v_version
  from public.rule_versions v where v.rule_id = v_rule.id;

  insert into public.rule_versions (rule_id, version, snapshot, changed_by, change_reason)
  values (
    v_rule.id,
    v_version,
    private.rule_snapshot(v_rule),
    auth.uid(),
    v_reason
  );

  return coalesce(new, old);
end;
$$;

-- Evidence can never be moved between rules. Without this, an administrator
-- could reassign a citation and silently change the history of both rules.
create or replace function private.prevent_evidence_rule_move()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.rule_id <> old.rule_id then
    raise exception using errcode = '42501',
      message = 'Evidence cannot be moved to a different rule';
  end if;
  return new;
end;
$$;

create trigger evidence_rule_immutable
before update of rule_id on public.rule_evidence
for each row execute function private.prevent_evidence_rule_move();

-- Direct evidence edits to published rules must leave a version trail. Draft
-- evidence is intentionally not versioned: the publication snapshot captures
-- the complete citation set without creating a misleading intermediate row.
-- The atomic RPCs suppress this trigger and write one final snapshot instead.
create or replace function private.snapshot_rule_on_evidence_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_rule_id uuid;
  v_status public.rule_status;
begin
  if coalesce(current_setting('compat.suppress_rule_snapshot', true), '') = 'on' then
    return coalesce(new, old);
  end if;

  v_rule_id := coalesce(new.rule_id, old.rule_id);

  select r.status into v_status
  from public.compatibility_rules r
  where r.id = v_rule_id;

  if v_status is distinct from 'published' then
    return coalesce(new, old);
  end if;

  -- The update fires the rules snapshot trigger with the evidence already in
  -- its final state for this statement.
  update public.compatibility_rules
  set reviewed_by = reviewed_by
  where id = v_rule_id;

  return coalesce(new, old);
end;
$$;

create trigger evidence_snapshot_rule
after insert or update or delete on public.rule_evidence
for each row execute function private.snapshot_rule_on_evidence_change();

-- ---------------------------------------------------------------------------
-- Product save
-- ---------------------------------------------------------------------------

create or replace function public.admin_save_product(
  p_id uuid,
  p_fields jsonb,
  p_aliases jsonb default '[]'::jsonb,
  p_specs jsonb default '[]'::jsonb
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_id uuid := p_id;
  v_name text;
  v_slug text;
  v_alias text;
  v_label text;
  v_value text;
  v_item jsonb;
  v_seen_aliases text[] := '{}';
  v_seen_labels text[] := '{}';
  v_sort integer := 0;
begin
  perform private.assert_admin();

  if p_fields is null or jsonb_typeof(p_fields) <> 'object' then
    raise exception using errcode = '22023', message = 'Product fields must be an object';
  end if;
  if p_aliases is null or jsonb_typeof(p_aliases) <> 'array' then
    raise exception using errcode = '22023', message = 'Product aliases must be an array';
  end if;
  if p_specs is null or jsonb_typeof(p_specs) <> 'array' then
    raise exception using errcode = '22023', message = 'Product specifications must be an array';
  end if;

  perform private.require_json_keys(
    p_fields, array['name', 'slug', 'brand_id', 'category_id', 'status']
  );

  v_name := btrim(coalesce(p_fields ->> 'name', ''));
  v_slug := btrim(coalesce(p_fields ->> 'slug', ''));

  if char_length(v_name) not between 1 and 200 then
    raise exception using errcode = '22023', message = 'Product name must contain 1-200 characters';
  end if;
  if v_slug !~ '^[a-z0-9]+(-[a-z0-9]+)*$' then
    raise exception using errcode = '22023', message = 'Invalid product slug';
  end if;
  if nullif(p_fields ->> 'release_year', '') is not null
     and (p_fields ->> 'release_year')::integer not between 1990 and 2100 then
    raise exception using errcode = '22023', message = 'Release year must be between 1990 and 2100';
  end if;

  if v_id is null then
    insert into public.products (
      name, slug, brand_id, category_id, model_number, release_year, summary,
      technical_notes, connector_type, form_factor, status, created_by
    ) values (
      v_name,
      v_slug,
      (p_fields ->> 'brand_id')::uuid,
      (p_fields ->> 'category_id')::uuid,
      nullif(btrim(coalesce(p_fields ->> 'model_number', '')), ''),
      nullif(p_fields ->> 'release_year', '')::integer,
      nullif(btrim(coalesce(p_fields ->> 'summary', '')), ''),
      nullif(btrim(coalesce(p_fields ->> 'technical_notes', '')), ''),
      nullif(btrim(coalesce(p_fields ->> 'connector_type', '')), ''),
      nullif(btrim(coalesce(p_fields ->> 'form_factor', '')), ''),
      (p_fields ->> 'status')::public.product_status,
      auth.uid()
    )
    returning id into v_id;
  else
    update public.products p
    set name = v_name,
        slug = v_slug,
        brand_id = (p_fields ->> 'brand_id')::uuid,
        category_id = (p_fields ->> 'category_id')::uuid,
        model_number = nullif(btrim(coalesce(p_fields ->> 'model_number', '')), ''),
        release_year = nullif(p_fields ->> 'release_year', '')::integer,
        summary = nullif(btrim(coalesce(p_fields ->> 'summary', '')), ''),
        technical_notes = nullif(btrim(coalesce(p_fields ->> 'technical_notes', '')), ''),
        connector_type = nullif(btrim(coalesce(p_fields ->> 'connector_type', '')), ''),
        form_factor = nullif(btrim(coalesce(p_fields ->> 'form_factor', '')), ''),
        status = (p_fields ->> 'status')::public.product_status
    where p.id = v_id;

    if not found then
      raise exception using errcode = '23503', message = 'Product not found';
    end if;
  end if;

  -- Media rows are intentionally untouched: the transaction preserves images
  -- and their approval status while replacing identity and specifications.
  delete from public.product_aliases a where a.product_id = v_id;
  for v_item in select value from jsonb_array_elements(p_aliases) loop
    v_alias := btrim(case
      when jsonb_typeof(v_item) = 'string' then v_item #>> '{}'
      else coalesce(v_item ->> 'alias', '')
    end);
    if v_alias <> '' and not (lower(v_alias) = any(v_seen_aliases)) then
      insert into public.product_aliases (product_id, alias) values (v_id, v_alias);
      v_seen_aliases := v_seen_aliases || lower(v_alias);
    end if;
  end loop;

  delete from public.product_specs s where s.product_id = v_id;
  for v_item in select value from jsonb_array_elements(p_specs) loop
    v_label := btrim(coalesce(v_item ->> 'label', ''));
    v_value := btrim(coalesce(v_item ->> 'value', ''));
    if v_label = '' or v_value = '' then
      raise exception using errcode = '22023',
        message = 'Every specification requires a label and value';
    end if;
    if not (lower(v_label) = any(v_seen_labels)) then
      insert into public.product_specs (
        product_id, label, value, unit, is_critical, sort_order
      ) values (
        v_id,
        v_label,
        v_value,
        nullif(btrim(coalesce(v_item ->> 'unit', '')), ''),
        coalesce((v_item ->> 'is_critical')::boolean, false),
        v_sort
      );
      v_seen_labels := v_seen_labels || lower(v_label);
      v_sort := v_sort + 1;
    end if;
  end loop;

  return v_id;
end;
$$;

-- ---------------------------------------------------------------------------
-- Guide save
-- ---------------------------------------------------------------------------

create or replace function public.admin_save_guide(
  p_id uuid,
  p_fields jsonb,
  p_steps jsonb default '[]'::jsonb
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_id uuid := p_id;
  v_step jsonb;
  v_position integer := 0;
  v_difficulty text;
begin
  perform private.assert_admin();

  if p_fields is null or jsonb_typeof(p_fields) <> 'object' then
    raise exception using errcode = '22023', message = 'Guide fields must be an object';
  end if;
  if p_steps is null or jsonb_typeof(p_steps) <> 'array' then
    raise exception using errcode = '22023', message = 'Guide steps must be an array';
  end if;

  perform private.require_json_keys(
    p_fields, array['slug', 'title', 'summary', 'body_md', 'difficulty', 'status']
  );

  v_difficulty := p_fields ->> 'difficulty';
  if v_difficulty not in ('easy', 'moderate', 'difficult', 'expert') then
    raise exception using errcode = '22023', message = 'Invalid guide difficulty';
  end if;
  if coalesce(p_fields ->> 'slug', '') !~ '^[a-z0-9]+(-[a-z0-9]+)*$' then
    raise exception using errcode = '22023', message = 'Invalid guide slug';
  end if;
  if char_length(btrim(coalesce(p_fields ->> 'title', ''))) not between 3 and 200 then
    raise exception using errcode = '22023', message = 'Guide title must contain 3-200 characters';
  end if;
  if char_length(btrim(coalesce(p_fields ->> 'summary', ''))) not between 10 and 1000 then
    raise exception using errcode = '22023', message = 'Guide summary must contain 10-1000 characters';
  end if;
  if char_length(btrim(coalesce(p_fields ->> 'body_md', ''))) not between 20 and 50000 then
    raise exception using errcode = '22023', message = 'Guide body must contain 20-50000 characters';
  end if;
  if jsonb_array_length(p_steps) > 100 then
    raise exception using errcode = '22023', message = 'A guide cannot exceed 100 steps';
  end if;

  if v_id is null then
    insert into public.guides (
      slug, title, summary, body_md, difficulty, status, product_id, created_by
    ) values (
      p_fields ->> 'slug',
      btrim(p_fields ->> 'title'),
      btrim(p_fields ->> 'summary'),
      btrim(p_fields ->> 'body_md'),
      v_difficulty,
      (p_fields ->> 'status')::public.guide_status,
      nullif(p_fields ->> 'product_id', '')::uuid,
      auth.uid()
    )
    returning id into v_id;
  else
    update public.guides g
    set slug = p_fields ->> 'slug',
        title = btrim(p_fields ->> 'title'),
        summary = btrim(p_fields ->> 'summary'),
        body_md = btrim(p_fields ->> 'body_md'),
        difficulty = v_difficulty,
        status = (p_fields ->> 'status')::public.guide_status,
        product_id = nullif(p_fields ->> 'product_id', '')::uuid
    where g.id = v_id;

    if not found then
      raise exception using errcode = '23503', message = 'Guide not found';
    end if;
  end if;

  -- One transaction: either every step is replaced or the old steps survive.
  delete from public.guide_steps s where s.guide_id = v_id;

  for v_step in select value from jsonb_array_elements(p_steps) loop
    v_position := v_position + 1;
    if char_length(btrim(coalesce(v_step ->> 'title', ''))) = 0
       or char_length(btrim(coalesce(v_step ->> 'body', ''))) = 0 then
      raise exception using errcode = '22023',
        message = format('Guide step %s requires a title and body', v_position);
    end if;

    insert into public.guide_steps (guide_id, position, title, body, image_path)
    values (
      v_id,
      v_position,
      btrim(v_step ->> 'title'),
      btrim(v_step ->> 'body'),
      nullif(btrim(coalesce(v_step ->> 'image_path', '')), '')
    );
  end loop;

  return v_id;
end;
$$;

-- ---------------------------------------------------------------------------
-- Compatibility rule and evidence save
-- ---------------------------------------------------------------------------

create or replace function public.admin_save_rule(
  p_id uuid,
  p_fields jsonb,
  p_evidence jsonb,
  p_reason text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_id uuid := p_id;
  v_reason text;
  v_relationship public.relationship_type;
  v_subject uuid;
  v_object uuid;
  v_status public.rule_status;
  v_evidence jsonb := coalesce(p_evidence, '[]'::jsonb);
  v_item jsonb;
  v_evidence_id uuid;
  v_owner uuid;
  v_kept uuid[] := '{}';
  v_source_url text;
  v_title text;
  v_version integer;
begin
  perform private.assert_admin();

  if p_fields is null or jsonb_typeof(p_fields) <> 'object' then
    raise exception using errcode = '22023', message = 'Rule fields must be an object';
  end if;
  if jsonb_typeof(v_evidence) <> 'array' then
    raise exception using errcode = '22023', message = 'Rule evidence must be an array';
  end if;

  perform private.require_json_keys(
    p_fields,
    array[
      'subject_product_id', 'object_product_id', 'relationship', 'verification',
      'confidence', 'risk', 'summary', 'status'
    ]
  );

  v_subject := (p_fields ->> 'subject_product_id')::uuid;
  v_object := (p_fields ->> 'object_product_id')::uuid;
  v_relationship := (p_fields ->> 'relationship')::public.relationship_type;
  v_status := (p_fields ->> 'status')::public.rule_status;

  if v_subject = v_object then
    raise exception using errcode = '22023',
      message = 'A product cannot be compared with itself';
  end if;
  if char_length(btrim(coalesce(p_fields ->> 'summary', ''))) not between 10 and 300 then
    raise exception using errcode = '22023',
      message = 'Rule summary must contain 10-300 characters';
  end if;
  if coalesce((p_fields ->> 'confidence')::integer, -1) not between 0 and 100 then
    raise exception using errcode = '22023',
      message = 'Confidence must be an integer from 0 to 100';
  end if;

  -- Symmetric relationships are stored once in canonical UUID order. The
  -- orientation of directional relationships (fits, variant_of) is preserved.
  if v_relationship in ('works_with', 'same_as', 'not_compatible_with')
     and v_subject::text > v_object::text then
    select v_subject, v_object into v_object, v_subject;
  end if;

  v_reason := nullif(btrim(coalesce(p_reason, '')), '');
  if v_reason is not null and char_length(v_reason) < 5 then
    raise exception using errcode = '22023',
      message = 'A change reason must contain at least 5 characters';
  end if;
  if v_reason is null then
    v_reason := case when p_id is null then 'Initial rule creation' else 'Admin rule update' end;
  end if;

  if v_id is null then
    -- Suppress the insert snapshot; the final update below writes the single
    -- authoritative creation version after evidence has been staged.
    perform pg_catalog.set_config('compat.suppress_rule_snapshot', 'on', true);

    insert into public.compatibility_rules (
      subject_product_id, object_product_id, relationship, verification,
      confidence, risk, summary, technical_details, required_modifications,
      status, created_by, reviewed_by
    ) values (
      v_subject,
      v_object,
      v_relationship,
      (p_fields ->> 'verification')::public.verification_level,
      (p_fields ->> 'confidence')::smallint,
      (p_fields ->> 'risk')::public.risk_level,
      btrim(p_fields ->> 'summary'),
      nullif(btrim(coalesce(p_fields ->> 'technical_details', '')), ''),
      nullif(btrim(coalesce(p_fields ->> 'required_modifications', '')), ''),
      'draft',
      auth.uid(),
      auth.uid()
    )
    returning id into v_id;
  else
    perform 1
    from public.compatibility_rules r
    where r.id = v_id
    for update;

    if not found then
      raise exception using errcode = '23503', message = 'Rule not found';
    end if;

    perform pg_catalog.set_config('compat.suppress_rule_snapshot', 'on', true);
  end if;

  -- Existing evidence updates are verified against the target rule. Any row
  -- that belongs to another rule aborts the whole transaction.
  for v_item in select value from jsonb_array_elements(v_evidence) loop
    v_source_url := btrim(coalesce(v_item ->> 'source_url', ''));
    v_title := btrim(coalesce(v_item ->> 'title', ''));

    if v_source_url !~* '^https?://' then
      raise exception using errcode = '22023',
        message = 'Evidence source URLs must begin with http:// or https://';
    end if;
    if char_length(v_title) < 3 then
      raise exception using errcode = '22023',
        message = 'Evidence titles must contain at least 3 characters';
    end if;
    if nullif(v_item ->> 'evidence_type', '') is null then
      raise exception using errcode = '22023',
        message = 'Every evidence item requires an evidence type';
    end if;

    if v_item ? 'id' and nullif(v_item ->> 'id', '') is not null then
      v_evidence_id := (v_item ->> 'id')::uuid;

      select e.rule_id into v_owner
      from public.rule_evidence e
      where e.id = v_evidence_id
      for update;

      if v_owner is null then
        raise exception using errcode = '23503', message = 'Evidence item not found';
      end if;
      if v_owner <> v_id then
        raise exception using errcode = '42501',
          message = 'Evidence item does not belong to this rule';
      end if;

      update public.rule_evidence e
      set evidence_type = (v_item ->> 'evidence_type')::public.evidence_type,
          title = v_title,
          source_url = v_source_url,
          publisher = nullif(btrim(coalesce(v_item ->> 'publisher', '')), ''),
          excerpt = nullif(btrim(coalesce(v_item ->> 'excerpt', '')), ''),
          captured_at = nullif(v_item ->> 'captured_at', '')::date
      where e.id = v_evidence_id;
    else
      insert into public.rule_evidence (
        rule_id, evidence_type, title, source_url, publisher, excerpt,
        captured_at, added_by
      ) values (
        v_id,
        (v_item ->> 'evidence_type')::public.evidence_type,
        v_title,
        v_source_url,
        nullif(btrim(coalesce(v_item ->> 'publisher', '')), ''),
        nullif(btrim(coalesce(v_item ->> 'excerpt', '')), ''),
        nullif(v_item ->> 'captured_at', '')::date,
        auth.uid()
      )
      on conflict (rule_id, source_url) do update
      set evidence_type = excluded.evidence_type,
          title = excluded.title,
          publisher = excluded.publisher,
          excerpt = excluded.excerpt,
          captured_at = excluded.captured_at
      returning id into v_evidence_id;
    end if;

    v_kept := v_kept || v_evidence_id;
  end loop;

  -- Remove only evidence omitted by the submitted set. On a published rule the
  -- last-evidence trigger still prevents an empty citation list.
  delete from public.rule_evidence e
  where e.rule_id = v_id
    and not (e.id = any(v_kept));

  -- One final update. It validates publication against the final evidence set,
  -- records the actor/reason, and writes exactly one snapshot of the result.
  perform pg_catalog.set_config('compat.suppress_rule_snapshot', 'off', true);
  perform pg_catalog.set_config('compat.rule_change_reason', v_reason, true);

  update public.compatibility_rules r
  set subject_product_id = v_subject,
      object_product_id = v_object,
      relationship = v_relationship,
      verification = (p_fields ->> 'verification')::public.verification_level,
      confidence = (p_fields ->> 'confidence')::smallint,
      risk = (p_fields ->> 'risk')::public.risk_level,
      summary = btrim(p_fields ->> 'summary'),
      technical_details = nullif(btrim(coalesce(p_fields ->> 'technical_details', '')), ''),
      required_modifications = nullif(btrim(coalesce(p_fields ->> 'required_modifications', '')), ''),
      status = v_status,
      published_at = case
        when v_status = 'published' then coalesce(r.published_at, now())
        else null
      end,
      reviewed_by = auth.uid()
  where r.id = v_id;

  select coalesce(max(v.version), 0) into v_version
  from public.rule_versions v where v.rule_id = v_id;

  return jsonb_build_object(
    'id', v_id,
    'version', v_version,
    'status', v_status,
    'evidence_count', (
      select count(*)::integer from public.rule_evidence e where e.rule_id = v_id
    )
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- Rollback: restore the exact evidence set and write one new version
-- ---------------------------------------------------------------------------

create or replace function public.admin_rollback_rule(
  p_rule_id uuid,
  p_version integer,
  p_reason text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_snapshot jsonb;
  v_item jsonb;
  v_source_url text;
  v_status public.rule_status;
  v_new_version integer;
begin
  perform private.assert_admin();

  if p_reason is null or char_length(btrim(p_reason)) < 5 then
    raise exception using errcode = '22023', message = 'A rollback reason is required';
  end if;

  select snapshot into v_snapshot
  from public.rule_versions v
  where v.rule_id = p_rule_id and v.version = p_version;

  if v_snapshot is null then
    raise exception using errcode = '23503', message = 'Rule version not found';
  end if;

  perform 1
  from public.compatibility_rules r
  where r.id = p_rule_id
  for update;

  if not found then
    raise exception using errcode = '23503', message = 'Rule not found';
  end if;

  v_status := (v_snapshot ->> 'status')::public.rule_status;

  perform pg_catalog.set_config('compat.suppress_rule_snapshot', 'on', true);

  -- Restore by source URL, which is the stable citation identity. Existing
  -- rows are updated in place so their ids and added_by attribution survive.
  for v_item in
    select value
    from jsonb_array_elements(coalesce(v_snapshot -> 'evidence', '[]'::jsonb))
  loop
    v_source_url := btrim(v_item ->> 'source_url');

    update public.rule_evidence e
    set evidence_type = (v_item ->> 'evidence_type')::public.evidence_type,
        title = v_item ->> 'title',
        publisher = nullif(v_item ->> 'publisher', ''),
        excerpt = nullif(v_item ->> 'excerpt', ''),
        captured_at = nullif(v_item ->> 'captured_at', '')::date,
        archived_path = nullif(v_item ->> 'archived_path', '')
    where e.rule_id = p_rule_id and e.source_url = v_source_url;

    if not found then
      insert into public.rule_evidence (
        rule_id, evidence_type, title, source_url, publisher, excerpt,
        captured_at, archived_path, added_by
      ) values (
        p_rule_id,
        (v_item ->> 'evidence_type')::public.evidence_type,
        v_item ->> 'title',
        v_source_url,
        nullif(v_item ->> 'publisher', ''),
        nullif(v_item ->> 'excerpt', ''),
        nullif(v_item ->> 'captured_at', '')::date,
        nullif(v_item ->> 'archived_path', ''),
        auth.uid()
      );
    end if;
  end loop;

  -- Move the rule to the target lifecycle state before pruning evidence. This
  -- allows rollback to a draft version with no citations while the current
  -- row is still published; the final update below remains authoritative.
  update public.compatibility_rules r
  set status = v_status
  where r.id = p_rule_id;

  delete from public.rule_evidence e
  where e.rule_id = p_rule_id
    and e.source_url not in (
      select btrim(item ->> 'source_url')
      from jsonb_array_elements(coalesce(v_snapshot -> 'evidence', '[]'::jsonb)) item
    );

  perform pg_catalog.set_config('compat.suppress_rule_snapshot', 'off', true);
  perform pg_catalog.set_config(
    'compat.rule_change_reason',
    'Rollback to v' || p_version || ': ' || btrim(p_reason),
    true
  );

  update public.compatibility_rules r
  set subject_product_id = (v_snapshot ->> 'subject_product_id')::uuid,
      object_product_id = (v_snapshot ->> 'object_product_id')::uuid,
      relationship = (v_snapshot ->> 'relationship')::public.relationship_type,
      verification = (v_snapshot ->> 'verification')::public.verification_level,
      confidence = (v_snapshot ->> 'confidence')::smallint,
      risk = (v_snapshot ->> 'risk')::public.risk_level,
      summary = v_snapshot ->> 'summary',
      technical_details = nullif(v_snapshot ->> 'technical_details', ''),
      required_modifications = nullif(v_snapshot ->> 'required_modifications', ''),
      status = v_status,
      published_at = nullif(v_snapshot ->> 'published_at', '')::timestamptz,
      reviewed_by = auth.uid()
  where r.id = p_rule_id;

  select coalesce(max(v.version), 0) into v_new_version
  from public.rule_versions v where v.rule_id = p_rule_id;

  insert into public.audit_logs (
    actor_id, action, entity_type, entity_id, before_data, after_data, context
  ) values (
    auth.uid(), 'rule.rolled_back', 'compatibility_rules', p_rule_id,
    null, jsonb_build_object('restored_version', p_version),
    jsonb_build_object('reason', btrim(p_reason))
  );

  return jsonb_build_object(
    'rule_id', p_rule_id,
    'restored_version', p_version,
    'new_version', v_new_version
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- Privileges
-- ---------------------------------------------------------------------------

revoke all on function public.admin_save_product(uuid, jsonb, jsonb, jsonb) from public, anon;
revoke all on function public.admin_save_guide(uuid, jsonb, jsonb) from public, anon;
revoke all on function public.admin_save_rule(uuid, jsonb, jsonb, text) from public, anon;
revoke all on function public.admin_rollback_rule(uuid, integer, text) from public, anon;

grant execute on function public.admin_save_product(uuid, jsonb, jsonb, jsonb) to authenticated;
grant execute on function public.admin_save_guide(uuid, jsonb, jsonb) to authenticated;
grant execute on function public.admin_save_rule(uuid, jsonb, jsonb, text) to authenticated;
grant execute on function public.admin_rollback_rule(uuid, integer, text) to authenticated;
