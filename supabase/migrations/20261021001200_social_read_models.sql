-- 012: social discovery RPCs and contribution validation hardening.
--
-- Applied migrations are immutable, so this migration adds the read models the
-- Android client needs for collections and public profiles without opening the
-- underlying tables. It also closes an evidence-contribution gap: rule
-- evidence required a real HTTP(S) URL, but standalone evidence did not.

-- ---------------------------------------------------------------------------
-- Collection read models
-- ---------------------------------------------------------------------------

create or replace function public.list_my_collections()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_items jsonb;
begin
  perform private.assert_active_user();

  select coalesce(jsonb_agg(
    jsonb_build_object(
      'id', c.id,
      'title', c.title,
      'description', c.description,
      'is_public', c.is_public,
      'item_count', (
        select count(*)::integer
        from public.collection_items i
        where i.collection_id = c.id
      ),
      'created_at', c.created_at,
      'updated_at', c.updated_at
    ) order by c.updated_at desc, c.created_at desc
  ), '[]'::jsonb)
  into v_items
  from public.collections c
  where c.owner_id = auth.uid();

  return jsonb_build_object('items', v_items);
end;
$$;

create or replace function public.get_collection(p_collection_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_collection public.collections%rowtype;
  v_owner_name text;
  v_owner_username text;
  v_items jsonb;
  v_is_owner boolean;
begin
  select c.* into v_collection
  from public.collections c
  where c.id = p_collection_id;

  if v_collection.id is null then
    return null;
  end if;

  v_is_owner := auth.uid() is not null and v_collection.owner_id = auth.uid();

  -- Private collections are visible to their owner and to administrators only.
  if not v_collection.is_public
     and not v_is_owner
     and not coalesce(private.is_admin(), false) then
    return null;
  end if;

  select p.display_name, p.username
  into v_owner_name, v_owner_username
  from public.profiles p
  where p.id = v_collection.owner_id and p.account_status = 'active';

  if v_owner_name is null and not v_is_owner then
    return null;
  end if;

  select coalesce(jsonb_agg(
    jsonb_build_object(
      'product_id', p.id,
      'product_name', p.name,
      'product_slug', p.slug,
      'model_number', p.model_number,
      'summary', p.summary,
      'brand_name', b.name,
      'brand_slug', b.slug,
      'category_name', cat.name,
      'category_slug', cat.slug,
      'image_path', (
        select m.storage_path
        from public.product_media m
        where m.product_id = p.id and m.status = 'approved'
        order by m.sort_order
        limit 1
      ),
      'note', i.note,
      'added_at', i.added_at
    ) order by i.added_at desc
  ), '[]'::jsonb)
  into v_items
  from public.collection_items i
  join public.products p on p.id = i.product_id and p.status = 'published'
  join public.brands b on b.id = p.brand_id
  join public.categories cat on cat.id = p.category_id
  where i.collection_id = p_collection_id;

  return jsonb_build_object(
    'id', v_collection.id,
    'title', v_collection.title,
    'description', v_collection.description,
    'is_public', v_collection.is_public,
    'is_owner', v_is_owner,
    'owner_username', v_owner_username,
    'owner_display_name', v_owner_name,
    'created_at', v_collection.created_at,
    'updated_at', v_collection.updated_at,
    'items', v_items
  );
end;
$$;

comment on function public.list_my_collections() is
  'Returns the caller''s collections with item counts. Authenticated only.';
comment on function public.get_collection(uuid) is
  'Returns a public collection or one owned by the caller; never exposes private collections of others.';

-- ---------------------------------------------------------------------------
-- Public profile follow state and counts
-- ---------------------------------------------------------------------------

create or replace function public.get_public_profile(p_user_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_profile jsonb;
begin
  select to_jsonb(p) into v_profile
  from public.profiles_public p where p.id = p_user_id;

  if v_profile is null then
    return null;
  end if;

  return v_profile || jsonb_build_object(
    'followers', (
      select count(*)::integer from public.follows f where f.followee_id = p_user_id
    ),
    'following', (
      select count(*)::integer from public.follows f where f.follower_id = p_user_id
    ),
    'is_following', (
      select exists (
        select 1 from public.follows f
        where f.follower_id = auth.uid() and f.followee_id = p_user_id
      )
    ),
    'is_self', coalesce(auth.uid() = p_user_id, false),
    'recent_contributions', coalesce((
      select jsonb_agg(jsonb_build_object(
        'kind', c.kind, 'status', c.status, 'reviewed_at', c.reviewed_at
      ) order by c.reviewed_at desc)
      from public.contributions c
      where c.contributor_id = p_user_id and c.status = 'approved'
    ), '[]'::jsonb)
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- Evidence contribution URL validation
-- ---------------------------------------------------------------------------

create or replace function private.validate_contribution_payload(
  p_kind public.contribution_kind,
  p_payload jsonb
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_item jsonb;
  v_evidence jsonb;
begin
  if jsonb_typeof(p_payload) <> 'object' then
    raise exception using errcode = '22023', message = 'Payload must be an object';
  end if;

  if p_kind = 'new_product' then
    perform private.require_json_keys(p_payload, array['name', 'slug', 'brand_id', 'category_id']);
    if p_payload ->> 'slug' !~ '^[a-z0-9]+(-[a-z0-9]+)*$' then
      raise exception using errcode = '22023', message = 'Invalid product slug';
    end if;
    if not exists (select 1 from public.brands b where b.id = (p_payload ->> 'brand_id')::uuid) then
      raise exception using errcode = '23503', message = 'Unknown brand';
    end if;
    if not exists (select 1 from public.categories c where c.id = (p_payload ->> 'category_id')::uuid) then
      raise exception using errcode = '23503', message = 'Unknown category';
    end if;
    if p_payload ? 'aliases' and jsonb_typeof(p_payload -> 'aliases') <> 'array' then
      raise exception using errcode = '22023', message = 'aliases must be an array';
    end if;
    if p_payload ? 'specs' and jsonb_typeof(p_payload -> 'specs') <> 'array' then
      raise exception using errcode = '22023', message = 'specs must be an array';
    end if;

  elsif p_kind = 'product_edit' then
    perform private.require_json_keys(p_payload, array['product_id', 'patch']);
    if not exists (
      select 1 from public.products p where p.id = (p_payload ->> 'product_id')::uuid
    ) then
      raise exception using errcode = '23503', message = 'Unknown product';
    end if;
    if jsonb_typeof(p_payload -> 'patch') <> 'object' then
      raise exception using errcode = '22023', message = 'patch must be an object';
    end if;

  elsif p_kind = 'new_rule' then
    perform private.require_json_keys(p_payload, array[
      'subject_product_id', 'object_product_id', 'relationship',
      'confidence', 'summary', 'evidence'
    ]);
    if p_payload ->> 'relationship' not in (
      'fits', 'works_with', 'same_as', 'variant_of', 'not_compatible_with'
    ) then
      raise exception using errcode = '22023', message = 'Invalid relationship';
    end if;
    if jsonb_typeof(p_payload -> 'evidence') <> 'array'
       or jsonb_array_length(p_payload -> 'evidence') < 1 then
      raise exception using errcode = '22023',
        message = 'A compatibility contribution requires evidence';
    end if;
    if (p_payload ->> 'confidence')::integer not between 0 and 100 then
      raise exception using errcode = '22023', message = 'Confidence must be 0-100';
    end if;
    for v_evidence in select * from jsonb_array_elements(p_payload -> 'evidence') loop
      perform private.require_json_keys(v_evidence, array['title', 'source_url', 'evidence_type']);
      if v_evidence ->> 'source_url' !~* '^https?://'
         or v_evidence ->> 'source_url' ~* '^https?://(www\.)?example\.(com|org|net)' then
        raise exception using errcode = '22023',
          message = 'Evidence requires a real HTTP(S) source URL';
      end if;
    end loop;

  elsif p_kind = 'evidence' then
    perform private.require_json_keys(p_payload, array['rule_id', 'evidence']);
    if jsonb_typeof(p_payload -> 'evidence') <> 'object' then
      raise exception using errcode = '22023', message = 'evidence must be an object';
    end if;
    if not exists (
      select 1 from public.compatibility_rules r where r.id = (p_payload ->> 'rule_id')::uuid
    ) then
      raise exception using errcode = '23503', message = 'Unknown rule';
    end if;
    perform private.require_json_keys(
      p_payload -> 'evidence', array['title', 'source_url', 'evidence_type']
    );
    if p_payload -> 'evidence' ->> 'source_url' !~* '^https?://'
       or p_payload -> 'evidence' ->> 'source_url' ~* '^https?://(www\.)?example\.(com|org|net)' then
      raise exception using errcode = '22023',
        message = 'Evidence requires a real HTTP(S) source URL';
    end if;

  elsif p_kind = 'guide' then
    perform private.require_json_keys(p_payload, array['title', 'summary', 'body_md', 'difficulty']);
    if p_payload ->> 'difficulty' not in ('easy', 'moderate', 'difficult', 'expert') then
      raise exception using errcode = '22023', message = 'Invalid guide difficulty';
    end if;
    if p_payload ? 'steps' and jsonb_typeof(p_payload -> 'steps') <> 'array' then
      raise exception using errcode = '22023', message = 'steps must be an array';
    end if;
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- Grants (hardening policy: no PUBLIC execution)
-- ---------------------------------------------------------------------------

revoke all on function public.list_my_collections() from public;
revoke all on function public.get_collection(uuid) from public;

grant execute on function public.list_my_collections() to authenticated;
grant execute on function public.get_collection(uuid) to anon, authenticated;
grant execute on function public.get_public_profile(uuid) to anon, authenticated;
