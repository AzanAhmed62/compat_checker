-- 009: admin dashboard, moderation, atomic contribution application, rollback,
-- and CSV bulk import. Every function re-checks the caller's role in SQL.

create or replace function private.is_moderator()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.profiles p
    where p.id = auth.uid()
      and p.role in ('trusted', 'admin')
      and p.account_status = 'active'
  );
$$;

create or replace function private.assert_moderator()
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if not private.is_moderator() then
    raise exception using errcode = '42501',
      message = 'Trusted contributor or administrator role required';
  end if;
end;
$$;

create or replace function private.slugify(p_text text)
returns text
language sql
immutable
set search_path = ''
as $$
  select coalesce(
    nullif(
      left(trim(both '-' from lower(regexp_replace(coalesce(p_text, ''), '[^a-zA-Z0-9]+', '-', 'g'))), 80),
      ''
    ),
    'item'
  );
$$;

create or replace function private.notify_user(
  p_user_id uuid,
  p_type public.notification_type,
  p_title text,
  p_body text,
  p_link text default null
)
returns void
language sql
security definer
set search_path = ''
as $$
  insert into public.notifications (user_id, type, title, body, link)
  values (p_user_id, p_type, p_title, p_body, p_link);
$$;

create or replace function public.admin_dashboard()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_result jsonb;
begin
  perform private.assert_admin();

  select jsonb_build_object(
    'counts', jsonb_build_object(
      'users_active', (select count(*)::integer from public.profiles where account_status = 'active'),
      'users_banned', (select count(*)::integer from public.profiles where account_status = 'banned'),
      'users_suspended', (select count(*)::integer from public.profiles where account_status = 'suspended'),
      'products_published', (select count(*)::integer from public.products where status = 'published'),
      'products_draft', (select count(*)::integer from public.products where status = 'draft'),
      'rules_published', (select count(*)::integer from public.compatibility_rules where status = 'published'),
      'rules_draft', (select count(*)::integer from public.compatibility_rules where status = 'draft'),
      'contributions_pending', (select count(*)::integer from public.contributions where status = 'pending'),
      'reports_open', (select count(*)::integer from public.reports where status in ('open', 'investigating')),
      'comments_hidden', (select count(*)::integer from public.comments where status = 'hidden'),
      'requests_open', (select count(*)::integer from public.product_requests where status = 'open'),
      'notifications_unread', (select count(*)::integer from public.notifications where read_at is null)
    ),
    'recent_audit', coalesce((
      select jsonb_agg(jsonb_build_object(
        'id', a.id, 'action', a.action, 'entity_type', a.entity_type,
        'entity_id', a.entity_id, 'created_at', a.created_at,
        'actor', au.display_name
      ) order by a.created_at desc)
      from (
        select * from public.audit_logs order by created_at desc limit 10
      ) a
      left join public.profiles au on au.id = a.actor_id
    ), '[]'::jsonb),
    'pending_contributions', coalesce((
      select jsonb_agg(to_jsonb(c) order by c.created_at)
      from (
        select c.id, c.kind, c.status, c.created_at, c.payload,
               p.display_name as contributor_name
        from public.contributions c
        join public.profiles p on p.id = c.contributor_id
        where c.status = 'pending'
        order by c.created_at
        limit 5
      ) c
    ), '[]'::jsonb),
    'open_reports', coalesce((
      select jsonb_agg(to_jsonb(r) order by r.created_at)
      from (
        select r.id, r.target_type, r.target_id, r.reason, r.status,
               r.created_at, p.display_name as reporter_name
        from public.reports r
        join public.profiles p on p.id = r.reporter_id
        where r.status in ('open', 'investigating')
        order by r.created_at
        limit 5
      ) r
    ), '[]'::jsonb)
  ) into v_result;

  return v_result;
end;
$$;

create or replace function public.admin_list_users(
  p_search text default null,
  p_role text default null,
  p_status text default null,
  p_limit integer default 25,
  p_offset integer default 0
)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_limit integer := least(greatest(coalesce(p_limit, 25), 1), 100);
  v_offset integer := greatest(coalesce(p_offset, 0), 0);
  v_total integer;
  v_items jsonb;
begin
  perform private.assert_admin();

  select count(*)::integer into v_total
  from public.profiles p
  join auth.users u on u.id = p.id
  where (p_search is null or p_search = ''
         or p.username ilike '%' || p_search || '%'
         or p.display_name ilike '%' || p_search || '%'
         or u.email ilike '%' || p_search || '%')
    and (p_role is null or p_role = '' or p.role::text = p_role)
    and (p_status is null or p_status = '' or p.account_status::text = p_status);

  select coalesce(jsonb_agg(jsonb_build_object(
    'id', p.id, 'username', p.username, 'display_name', p.display_name,
    'email', u.email, 'role', p.role, 'account_status', p.account_status,
    'created_at', p.created_at,
    'reputation_points', coalesce((
      select sum(l.delta)::integer from public.points_ledger l where l.user_id = p.id
    ), 0),
    'approved_contributions', (
      select count(*)::integer from public.contributions c
      where c.contributor_id = p.id and c.status = 'approved'
    )
  ) order by p.created_at desc), '[]'::jsonb)
  into v_items
  from (
    select p.* from public.profiles p
    join auth.users u on u.id = p.id
    where (p_search is null or p_search = ''
           or p.username ilike '%' || p_search || '%'
           or p.display_name ilike '%' || p_search || '%'
           or u.email ilike '%' || p_search || '%')
      and (p_role is null or p_role = '' or p.role::text = p_role)
      and (p_status is null or p_status = '' or p.account_status::text = p_status)
    order by p.created_at desc
    limit v_limit offset v_offset
  ) p
  join auth.users u on u.id = p.id;

  return jsonb_build_object('items', v_items, 'total', v_total,
                            'limit', v_limit, 'offset', v_offset);
end;
$$;

create or replace function public.admin_set_user_role(
  p_user_id uuid,
  p_role public.app_role,
  p_reason text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_before public.profiles;
  v_after public.profiles;
  v_other_admins integer;
begin
  perform private.assert_admin();

  if p_reason is null or char_length(btrim(p_reason)) < 5 then
    raise exception using errcode = '22023', message = 'A reason is required';
  end if;

  select * into v_before from public.profiles p where p.id = p_user_id for update;
  if v_before.id is null then
    raise exception using errcode = '23503', message = 'User not found';
  end if;

  if v_before.role = 'admin' and p_role <> 'admin' then
    select count(*)::integer into v_other_admins
    from public.profiles p
    where p.role = 'admin' and p.account_status = 'active' and p.id <> p_user_id;
    if v_other_admins < 1 then
      raise exception using errcode = 'P0001',
        message = 'Cannot remove the last active administrator';
    end if;
  end if;

  update public.profiles p
  set role = p_role,
      moderation_note = left(btrim(p_reason), 1000)
  where p.id = p_user_id
  returning * into v_after;

  insert into public.audit_logs (
    actor_id, action, entity_type, entity_id, before_data, after_data, context
  )
  values (
    auth.uid(), 'user.role_changed', 'profiles', p_user_id,
    jsonb_build_object('role', v_before.role),
    jsonb_build_object('role', v_after.role),
    jsonb_build_object('reason', btrim(p_reason))
  );

  perform private.notify_user(
    p_user_id, 'role_changed', 'Your role changed',
    'Your account role is now ' || p_role::text || '.', '/profile'
  );

  return jsonb_build_object(
    'id', v_after.id, 'role', v_after.role, 'account_status', v_after.account_status
  );
end;
$$;

create or replace function public.admin_set_user_status(
  p_user_id uuid,
  p_status text,
  p_reason text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_before public.profiles;
  v_after public.profiles;
  v_status public.account_status;
  v_other_admins integer;
begin
  perform private.assert_admin();

  if p_status not in ('active', 'suspended', 'banned') then
    raise exception using errcode = '22023',
      message = 'Status must be active, suspended, or banned';
  end if;
  if p_reason is null or char_length(btrim(p_reason)) < 5 then
    raise exception using errcode = '22023', message = 'A reason is required';
  end if;
  if p_user_id = auth.uid() and p_status <> 'active' then
    raise exception using errcode = 'P0001',
      message = 'You cannot suspend or ban your own account';
  end if;

  v_status := p_status::public.account_status;

  select * into v_before from public.profiles p where p.id = p_user_id for update;
  if v_before.id is null then
    raise exception using errcode = '23503', message = 'User not found';
  end if;

  if v_before.role = 'admin' and p_status <> 'active' then
    select count(*)::integer into v_other_admins
    from public.profiles p
    where p.role = 'admin' and p.account_status = 'active' and p.id <> p_user_id;
    if v_other_admins < 1 then
      raise exception using errcode = 'P0001',
        message = 'Cannot deactivate the last active administrator';
    end if;
  end if;

  update public.profiles p
  set account_status = v_status,
      moderation_note = left(btrim(p_reason), 1000),
      deletion_requested_at = case when v_status = 'active' then null else p.deletion_requested_at end
  where p.id = p_user_id
  returning * into v_after;

  update auth.users u
  set banned_until = case
        when v_status = 'banned' then 'infinity'::timestamptz
        when v_status = 'suspended' then now() + interval '30 days'
        else null
      end
  where u.id = p_user_id;

  insert into public.audit_logs (
    actor_id, action, entity_type, entity_id, before_data, after_data, context
  )
  values (
    auth.uid(), 'user.status_changed', 'profiles', p_user_id,
    jsonb_build_object('status', v_before.account_status),
    jsonb_build_object('status', v_after.account_status),
    jsonb_build_object('reason', btrim(p_reason))
  );

  perform private.notify_user(
    p_user_id, 'moderation', 'Your account status changed',
    'Your account status is now ' || v_status::text || '. Reason: ' || btrim(p_reason),
    '/profile'
  );

  return jsonb_build_object(
    'id', v_after.id, 'role', v_after.role, 'account_status', v_after.account_status
  );
end;
$$;

create or replace function public.admin_list_contributions(
  p_status text default null,
  p_kind text default null,
  p_limit integer default 25,
  p_offset integer default 0
)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_limit integer := least(greatest(coalesce(p_limit, 25), 1), 100);
  v_offset integer := greatest(coalesce(p_offset, 0), 0);
  v_total integer;
  v_items jsonb;
begin
  perform private.assert_moderator();

  select count(*)::integer into v_total
  from public.contributions c
  where (p_status is null or p_status = '' or c.status::text = p_status)
    and (p_kind is null or p_kind = '' or c.kind::text = p_kind);

  select coalesce(jsonb_agg(to_jsonb(c) order by c.created_at), '[]'::jsonb)
  into v_items
  from (
    select c.id, c.contributor_id, c.kind, c.payload, c.status,
           c.review_note, c.applied_entity_id, c.created_at, c.reviewed_at,
           p.display_name as contributor_name,
           r.display_name as reviewer_name
    from public.contributions c
    join public.profiles p on p.id = c.contributor_id
    left join public.profiles r on r.id = c.reviewer_id
    where (p_status is null or p_status = '' or c.status::text = p_status)
      and (p_kind is null or p_kind = '' or c.kind::text = p_kind)
    order by c.created_at
    limit v_limit offset v_offset
  ) c;

  return jsonb_build_object('items', v_items, 'total', v_total,
                            'limit', v_limit, 'offset', v_offset);
end;
$$;

create or replace function public.admin_review_contribution(
  p_contribution_id uuid,
  p_decision text,
  p_note text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_contribution public.contributions;
  v_contributor public.profiles;
  v_payload jsonb;
  v_product_id uuid;
  v_rule_id uuid;
  v_guide_id uuid;
  v_evidence_id uuid;
  v_applied_id uuid;
  v_item jsonb;
  v_alias text;
  v_subject uuid;
  v_object uuid;
  v_relationship public.relationship_type;
  v_verification public.verification_level;
  v_risk public.risk_level;
  v_points integer;
  v_slug text;
  v_index integer := 0;
  v_step jsonb;
begin
  perform private.assert_moderator();

  if p_decision not in ('approved', 'rejected', 'needs_changes') then
    raise exception using errcode = '22023',
      message = 'Decision must be approved, rejected, or needs_changes';
  end if;

  select * into v_contribution
  from public.contributions c
  where c.id = p_contribution_id
  for update;

  if v_contribution.id is null then
    raise exception using errcode = '23503', message = 'Contribution not found';
  end if;
  if v_contribution.status not in ('pending', 'needs_changes') then
    raise exception using errcode = 'P0001', message = 'Contribution is already reviewed';
  end if;
  if v_contribution.contributor_id = auth.uid() then
    raise exception using errcode = 'P0001',
      message = 'You cannot review your own contribution';
  end if;

  select * into v_contributor from public.profiles p where p.id = v_contribution.contributor_id;

  if p_decision = 'approved' then
    v_payload := v_contribution.payload;

    if v_contribution.kind = 'new_product' then
      insert into public.products (
        slug, name, brand_id, category_id, model_number, release_year,
        summary, technical_notes, connector_type, form_factor,
        status, created_by, published_at
      )
      values (
        v_payload ->> 'slug', btrim(v_payload ->> 'name'),
        (v_payload ->> 'brand_id')::uuid, (v_payload ->> 'category_id')::uuid,
        nullif(v_payload ->> 'model_number', ''),
        nullif(v_payload ->> 'release_year', '')::integer,
        nullif(v_payload ->> 'summary', ''),
        nullif(v_payload ->> 'technical_notes', ''),
        nullif(v_payload ->> 'connector_type', ''),
        nullif(v_payload ->> 'form_factor', ''),
        'published', v_contribution.contributor_id, now()
      )
      returning id into v_product_id;

      for v_alias in
        select value from jsonb_array_elements_text(coalesce(v_payload -> 'aliases', '[]'::jsonb))
      loop
        insert into public.product_aliases (product_id, alias)
        values (v_product_id, v_alias) on conflict do nothing;
      end loop;

      for v_item in
        select value from jsonb_array_elements(coalesce(v_payload -> 'specs', '[]'::jsonb))
      loop
        insert into public.product_specs (
          product_id, label, value, unit, is_critical, sort_order
        ) values (
          v_product_id, v_item ->> 'label', v_item ->> 'value',
          nullif(v_item ->> 'unit', ''),
          coalesce((v_item ->> 'is_critical')::boolean, false),
          coalesce((v_item ->> 'sort_order')::integer, 0)
        );
      end loop;

      v_applied_id := v_product_id;

    elsif v_contribution.kind = 'product_edit' then
      v_product_id := (v_payload ->> 'product_id')::uuid;
      v_payload := v_payload -> 'patch';

      update public.products p
      set name = coalesce(nullif(btrim(v_payload ->> 'name'), ''), p.name),
          model_number = coalesce(nullif(v_payload ->> 'model_number', ''), p.model_number),
          release_year = coalesce(nullif(v_payload ->> 'release_year', '')::integer, p.release_year),
          summary = coalesce(nullif(v_payload ->> 'summary', ''), p.summary),
          technical_notes = coalesce(nullif(v_payload ->> 'technical_notes', ''), p.technical_notes),
          connector_type = coalesce(nullif(v_payload ->> 'connector_type', ''), p.connector_type),
          form_factor = coalesce(nullif(v_payload ->> 'form_factor', ''), p.form_factor)
      where p.id = v_product_id;

      if not found then
        raise exception using errcode = '23503', message = 'Product not found';
      end if;

      if v_payload ? 'aliases' then
        delete from public.product_aliases a where a.product_id = v_product_id;
        for v_alias in
          select value from jsonb_array_elements_text(coalesce(v_payload -> 'aliases', '[]'::jsonb))
        loop
          insert into public.product_aliases (product_id, alias)
          values (v_product_id, v_alias);
        end loop;
      end if;

      if v_payload ? 'specs' then
        delete from public.product_specs s where s.product_id = v_product_id;
        for v_item in
          select value from jsonb_array_elements(coalesce(v_payload -> 'specs', '[]'::jsonb))
        loop
          insert into public.product_specs (
            product_id, label, value, unit, is_critical, sort_order
          ) values (
            v_product_id, v_item ->> 'label', v_item ->> 'value',
            nullif(v_item ->> 'unit', ''),
            coalesce((v_item ->> 'is_critical')::boolean, false),
            coalesce((v_item ->> 'sort_order')::integer, 0)
          );
        end loop;
      end if;

      v_applied_id := v_product_id;

    elsif v_contribution.kind = 'new_rule' then
      v_subject := (v_payload ->> 'subject_product_id')::uuid;
      v_object := (v_payload ->> 'object_product_id')::uuid;
      v_relationship := (v_payload ->> 'relationship')::public.relationship_type;

      if v_relationship in ('works_with', 'same_as', 'not_compatible_with')
         and v_subject > v_object then
        v_rule_id := v_subject;
        v_subject := v_object;
        v_object := v_rule_id;
      end if;

      v_verification := case
        when v_payload ->> 'verification' in ('needs_verification', 'experimental')
          then (v_payload ->> 'verification')::public.verification_level
        else 'community_verified'
      end;
      v_risk := coalesce(
        nullif(v_payload ->> 'risk_level', ''),
        'none'
      )::public.risk_level;

      insert into public.compatibility_rules (
        subject_product_id, object_product_id, relationship, verification,
        confidence, risk, summary, technical_details, required_modifications,
        status, created_by, reviewed_by
      ) values (
        v_subject, v_object, v_relationship, v_verification,
        (v_payload ->> 'confidence')::smallint, v_risk,
        v_payload ->> 'summary',
        nullif(v_payload ->> 'technical_details', ''),
        nullif(v_payload ->> 'required_modifications', ''),
        'draft', v_contribution.contributor_id, auth.uid()
      )
      returning id into v_rule_id;

      for v_item in select value from jsonb_array_elements(v_payload -> 'evidence') loop
        insert into public.rule_evidence (
          rule_id, evidence_type, title, source_url, publisher, excerpt,
          captured_at, added_by
        ) values (
          v_rule_id,
          (v_item ->> 'evidence_type')::public.evidence_type,
          v_item ->> 'title',
          v_item ->> 'source_url',
          nullif(v_item ->> 'publisher', ''),
          nullif(v_item ->> 'excerpt', ''),
          nullif(v_item ->> 'captured_at', '')::date,
          v_contribution.contributor_id
        );
      end loop;

      update public.compatibility_rules
      set status = 'published'
      where id = v_rule_id;

      v_applied_id := v_rule_id;

    elsif v_contribution.kind = 'evidence' then
      v_rule_id := (v_payload ->> 'rule_id')::uuid;
      v_item := v_payload -> 'evidence';

      insert into public.rule_evidence (
        rule_id, evidence_type, title, source_url, publisher, excerpt,
        captured_at, added_by
      ) values (
        v_rule_id,
        (v_item ->> 'evidence_type')::public.evidence_type,
        v_item ->> 'title',
        v_item ->> 'source_url',
        nullif(v_item ->> 'publisher', ''),
        nullif(v_item ->> 'excerpt', ''),
        nullif(v_item ->> 'captured_at', '')::date,
        v_contribution.contributor_id
      )
      returning id into v_evidence_id;

      v_applied_id := v_evidence_id;

    elsif v_contribution.kind = 'guide' then
      v_slug := coalesce(nullif(v_payload ->> 'slug', ''), private.slugify(v_payload ->> 'title'))
        || '-' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 6);

      insert into public.guides (
        slug, title, summary, body_md, difficulty, status, product_id, created_by
      ) values (
        v_slug,
        btrim(v_payload ->> 'title'),
        v_payload ->> 'summary',
        v_payload ->> 'body_md',
        v_payload ->> 'difficulty',
        'published',
        nullif(v_payload ->> 'product_id', '')::uuid,
        v_contribution.contributor_id
      )
      returning id into v_guide_id;

      for v_step in select value from jsonb_array_elements(coalesce(v_payload -> 'steps', '[]'::jsonb)) loop
        v_index := v_index + 1;
        insert into public.guide_steps (guide_id, position, title, body, image_path)
        values (
          v_guide_id, v_index,
          v_step ->> 'title', v_step ->> 'body',
          nullif(v_step ->> 'image_path', '')
        );
      end loop;

      v_applied_id := v_guide_id;
    end if;

    update public.contributions c
    set status = 'approved',
        reviewer_id = auth.uid(),
        review_note = nullif(btrim(p_note), ''),
        applied_entity_id = v_applied_id,
        reviewed_at = now()
    where c.id = p_contribution_id;

    v_points := case v_contribution.kind
      when 'new_product' then 25
      when 'product_edit' then 10
      when 'new_rule' then 40
      when 'evidence' then 15
      when 'guide' then 20
    end;

    perform private.award_points(
      v_contribution.contributor_id, v_points, 'contribution_approved',
      'contribution', p_contribution_id,
      'contribution:' || p_contribution_id::text
    );

    perform private.notify_user(
      v_contribution.contributor_id, 'contribution_review',
      'Contribution approved',
      'Your ' || replace(v_contribution.kind::text, '_', ' ') || ' was approved. Thank you.',
      '/profile/contributions'
    );

    insert into public.audit_logs (
      actor_id, action, entity_type, entity_id, before_data, after_data, context
    ) values (
      auth.uid(), 'contribution.approved', 'contributions', p_contribution_id,
      jsonb_build_object('status', v_contribution.status),
      jsonb_build_object('status', 'approved', 'applied_entity_id', v_applied_id),
      jsonb_build_object('note', p_note)
    );

  else
    update public.contributions c
    set status = p_decision::public.contribution_status,
        reviewer_id = auth.uid(),
        review_note = nullif(btrim(p_note), ''),
        reviewed_at = now()
    where c.id = p_contribution_id;

    perform private.notify_user(
      v_contribution.contributor_id, 'contribution_review',
      'Contribution reviewed',
      'Your contribution is now ' || replace(p_decision, '_', ' ') || '.'
        || coalesce(' Note: ' || p_note, ''),
      '/profile/contributions'
    );

    insert into public.audit_logs (
      actor_id, action, entity_type, entity_id, before_data, after_data, context
    ) values (
      auth.uid(), 'contribution.' || p_decision, 'contributions', p_contribution_id,
      jsonb_build_object('status', v_contribution.status),
      jsonb_build_object('status', p_decision),
      jsonb_build_object('note', p_note)
    );
  end if;

  return jsonb_build_object(
    'id', p_contribution_id,
    'status', p_decision,
    'applied_entity_id', v_applied_id
  );
end;
$$;

create or replace function public.admin_list_reports(
  p_status text default null,
  p_limit integer default 25,
  p_offset integer default 0
)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_limit integer := least(greatest(coalesce(p_limit, 25), 1), 100);
  v_offset integer := greatest(coalesce(p_offset, 0), 0);
  v_total integer;
  v_items jsonb;
begin
  perform private.assert_moderator();

  select count(*)::integer into v_total
  from public.reports r
  where (p_status is null or p_status = '' or r.status::text = p_status);

  select coalesce(jsonb_agg(to_jsonb(r) order by r.created_at), '[]'::jsonb)
  into v_items
  from (
    select r.id, r.target_type, r.target_id, r.reason, r.details, r.status,
           r.resolution_note, r.created_at, r.updated_at,
           p.display_name as reporter_name,
           h.display_name as handler_name
    from public.reports r
    join public.profiles p on p.id = r.reporter_id
    left join public.profiles h on h.id = r.handled_by
    where (p_status is null or p_status = '' or r.status::text = p_status)
    order by r.created_at
    limit v_limit offset v_offset
  ) r;

  return jsonb_build_object('items', v_items, 'total', v_total,
                            'limit', v_limit, 'offset', v_offset);
end;
$$;

create or replace function public.admin_resolve_report(
  p_report_id uuid,
  p_status text,
  p_note text,
  p_action text default 'none'
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_report public.reports;
begin
  perform private.assert_moderator();

  if p_status not in ('investigating', 'resolved', 'dismissed') then
    raise exception using errcode = '22023',
      message = 'Status must be investigating, resolved, or dismissed';
  end if;
  if p_action not in ('none', 'hide_comment', 'retire_rule', 'archive_product') then
    raise exception using errcode = '22023', message = 'Invalid moderation action';
  end if;

  select * into v_report from public.reports r where r.id = p_report_id for update;
  if v_report.id is null then
    raise exception using errcode = '23503', message = 'Report not found';
  end if;

  if p_action = 'hide_comment' and v_report.target_type = 'comment' then
    update public.comments c set status = 'hidden' where c.id = v_report.target_id;
  elsif p_action = 'retire_rule' and v_report.target_type = 'rule' then
    update public.compatibility_rules r set status = 'retired' where r.id = v_report.target_id;
  elsif p_action = 'archive_product' and v_report.target_type = 'product' then
    update public.products p set status = 'archived' where p.id = v_report.target_id;
  end if;

  update public.reports r
  set status = p_status::public.report_status,
      handled_by = auth.uid(),
      resolution_note = nullif(btrim(p_note), '')
  where r.id = p_report_id;

  perform private.notify_user(
    v_report.reporter_id, 'report_update', 'Report updated',
    'Your report is now ' || p_status || '.'
      || coalesce(' Note: ' || p_note, ''),
    '/profile/reports'
  );

  if p_status = 'resolved' then
    perform private.award_points(
      v_report.reporter_id, 5, 'report_accepted', 'report', p_report_id,
      'report:' || p_report_id::text
    );
  end if;

  insert into public.audit_logs (
    actor_id, action, entity_type, entity_id, before_data, after_data, context
  ) values (
    auth.uid(), 'report.' || p_status, 'reports', p_report_id,
    jsonb_build_object('status', v_report.status),
    jsonb_build_object('status', p_status, 'action', p_action),
    jsonb_build_object('note', p_note)
  );

  return jsonb_build_object(
    'id', p_report_id, 'status', p_status, 'action', p_action
  );
end;
$$;

create or replace function public.admin_moderate_comment(
  p_comment_id uuid,
  p_status text,
  p_reason text
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_comment public.comments;
begin
  perform private.assert_moderator();

  if p_status not in ('visible', 'hidden', 'deleted') then
    raise exception using errcode = '22023', message = 'Invalid comment status';
  end if;
  if p_reason is null or char_length(btrim(p_reason)) < 3 then
    raise exception using errcode = '22023', message = 'A moderation reason is required';
  end if;

  select * into v_comment from public.comments c where c.id = p_comment_id for update;
  if v_comment.id is null then
    raise exception using errcode = '23503', message = 'Comment not found';
  end if;

  update public.comments c
  set status = p_status::public.comment_status
  where c.id = p_comment_id;

  if p_status <> 'visible' then
    perform private.notify_user(
      v_comment.user_id, 'moderation', 'Comment moderated',
      'A comment was ' || p_status || '. Reason: ' || btrim(p_reason),
      null
    );
  end if;

  insert into public.audit_logs (
    actor_id, action, entity_type, entity_id, before_data, after_data, context
  ) values (
    auth.uid(), 'comment.moderated', 'comments', p_comment_id,
    jsonb_build_object('status', v_comment.status),
    jsonb_build_object('status', p_status),
    jsonb_build_object('reason', btrim(p_reason))
  );

  return true;
end;
$$;

create or replace function public.admin_update_request(
  p_request_id uuid,
  p_status public.request_status,
  p_fulfilled_product_id uuid default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_request public.product_requests;
begin
  perform private.assert_moderator();

  select * into v_request
  from public.product_requests r
  where r.id = p_request_id
  for update;

  if v_request.id is null then
    raise exception using errcode = '23503', message = 'Request not found';
  end if;

  if p_fulfilled_product_id is not null and not exists (
    select 1 from public.products p
    where p.id = p_fulfilled_product_id and p.status = 'published'
  ) then
    raise exception using errcode = '23503', message = 'Fulfilling product must be published';
  end if;

  if p_status = 'fulfilled' and p_fulfilled_product_id is null
     and v_request.fulfilled_product_id is null then
    raise exception using errcode = '22023',
      message = 'A fulfilling product is required';
  end if;

  update public.product_requests r
  set status = p_status,
      fulfilled_product_id = coalesce(p_fulfilled_product_id, r.fulfilled_product_id)
  where r.id = p_request_id;

  perform private.notify_user(
    v_request.created_by, 'system', 'Product request updated',
    'Your request "' || v_request.title || '" is now ' || p_status::text || '.',
    '/community/requests/' || p_request_id::text
  );

  insert into public.audit_logs (
    actor_id, action, entity_type, entity_id, before_data, after_data, context
  ) values (
    auth.uid(), 'request.updated', 'product_requests', p_request_id,
    jsonb_build_object('status', v_request.status),
    jsonb_build_object('status', p_status, 'fulfilled_product_id', p_fulfilled_product_id),
    '{}'::jsonb
  );

  return jsonb_build_object(
    'id', p_request_id, 'status', p_status,
    'fulfilled_product_id', coalesce(p_fulfilled_product_id, v_request.fulfilled_product_id)
  );
end;
$$;

create or replace function public.admin_rule_versions(p_rule_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_items jsonb;
begin
  perform private.assert_admin();

  select coalesce(jsonb_agg(jsonb_build_object(
    'id', v.id, 'version', v.version, 'snapshot', v.snapshot,
    'change_reason', v.change_reason, 'created_at', v.created_at,
    'changed_by', p.display_name
  ) order by v.version desc), '[]'::jsonb)
  into v_items
  from public.rule_versions v
  left join public.profiles p on p.id = v.changed_by
  where v.rule_id = p_rule_id;

  return jsonb_build_object('items', v_items);
end;
$$;

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
  v_evidence jsonb;
  v_item jsonb;
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

  for v_item in select value from jsonb_array_elements(coalesce(v_snapshot -> 'evidence', '[]'::jsonb)) loop
    insert into public.rule_evidence (
      rule_id, evidence_type, title, source_url, publisher, excerpt,
      captured_at, archived_path, added_by
    ) values (
      p_rule_id,
      (v_item ->> 'evidence_type')::public.evidence_type,
      v_item ->> 'title',
      v_item ->> 'source_url',
      nullif(v_item ->> 'publisher', ''),
      nullif(v_item ->> 'excerpt', ''),
      nullif(v_item ->> 'captured_at', '')::date,
      nullif(v_item ->> 'archived_path', ''),
      auth.uid()
    )
    on conflict (rule_id, source_url) do nothing;
  end loop;

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
      status = (v_snapshot ->> 'status')::public.rule_status,
      reviewed_by = auth.uid()
  where r.id = p_rule_id;

  if not found then
    raise exception using errcode = '23503', message = 'Rule not found';
  end if;

  select max(v.version) into v_new_version
  from public.rule_versions v where v.rule_id = p_rule_id;

  update public.rule_versions v
  set change_reason = 'Rollback to v' || p_version || ': ' || btrim(p_reason)
  where v.rule_id = p_rule_id and v.version = v_new_version;

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

create or replace function public.admin_bulk_import_products(
  p_rows jsonb,
  p_publish boolean default false
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_row jsonb;
  v_index integer := 0;
  v_id uuid;
  v_brand_id uuid;
  v_category_id uuid;
  v_ids jsonb := '[]'::jsonb;
  v_alias text;
begin
  perform private.assert_admin();

  if jsonb_typeof(p_rows) <> 'array' then
    raise exception using errcode = '22023', message = 'Rows must be an array';
  end if;
  if jsonb_array_length(p_rows) = 0 or jsonb_array_length(p_rows) > 500 then
    raise exception using errcode = '22023', message = 'Import 1-500 rows at a time';
  end if;

  for v_row in select value from jsonb_array_elements(p_rows) loop
    v_index := v_index + 1;
    v_brand_id := null;
    v_category_id := null;

    begin
      if coalesce(btrim(v_row ->> 'name'), '') = ''
         or coalesce(btrim(v_row ->> 'slug'), '') = '' then
        raise exception using errcode = '22023', message = 'name and slug are required';
      end if;
      if v_row ->> 'slug' !~ '^[a-z0-9]+(-[a-z0-9]+)*$' then
        raise exception using errcode = '22023', message = 'invalid slug';
      end if;

      if v_row ? 'brand_id' and coalesce(v_row ->> 'brand_id', '') <> '' then
        v_brand_id := (v_row ->> 'brand_id')::uuid;
      elsif v_row ? 'brand_slug' and coalesce(v_row ->> 'brand_slug', '') <> '' then
        select b.id into v_brand_id from public.brands b where b.slug = v_row ->> 'brand_slug';
      end if;

      if v_row ? 'category_id' and coalesce(v_row ->> 'category_id', '') <> '' then
        v_category_id := (v_row ->> 'category_id')::uuid;
      elsif v_row ? 'category_slug' and coalesce(v_row ->> 'category_slug', '') <> '' then
        select c.id into v_category_id from public.categories c where c.slug = v_row ->> 'category_slug';
      end if;

      if v_brand_id is null then
        raise exception using errcode = '23503', message = 'unknown brand';
      end if;
      if v_category_id is null then
        raise exception using errcode = '23503', message = 'unknown category';
      end if;

      insert into public.products (
        slug, name, brand_id, category_id, model_number, release_year,
        summary, technical_notes, connector_type, form_factor, status, created_by
      ) values (
        v_row ->> 'slug', btrim(v_row ->> 'name'), v_brand_id, v_category_id,
        nullif(v_row ->> 'model_number', ''),
        nullif(v_row ->> 'release_year', '')::integer,
        nullif(v_row ->> 'summary', ''),
        nullif(v_row ->> 'technical_notes', ''),
        nullif(v_row ->> 'connector_type', ''),
        nullif(v_row ->> 'form_factor', ''),
        case when coalesce(p_publish, false) then 'published'::public.product_status
             else 'draft'::public.product_status end,
        auth.uid()
      )
      returning id into v_id;

      for v_alias in
        select value from jsonb_array_elements_text(coalesce(v_row -> 'aliases', '[]'::jsonb))
      loop
        insert into public.product_aliases (product_id, alias)
        values (v_id, v_alias) on conflict do nothing;
      end loop;

      v_ids := v_ids || to_jsonb(v_id);
    exception
      when others then
        raise exception using errcode = '22023',
          message = format('Row %s: %s', v_index, sqlerrm);
    end;
  end loop;

  return jsonb_build_object(
    'imported', jsonb_array_length(v_ids),
    'ids', v_ids,
    'published', coalesce(p_publish, false)
  );
end;
$$;

comment on function public.admin_review_contribution(uuid, text, text) is
  'Atomic review. Approval applies the payload and awards idempotent points.';
comment on function public.admin_rollback_rule(uuid, integer, text) is
  'Admin-only rule rollback with an explicit reason and a new version snapshot.';
