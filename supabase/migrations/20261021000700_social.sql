-- 007: follows, notifications, collections, guides, media, and account lifecycle.

-- ---------------------------------------------------------------------------
-- Follows
-- ---------------------------------------------------------------------------

create table public.follows (
  follower_id uuid not null references public.profiles(id) on delete cascade,
  followee_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (follower_id, followee_id),
  check (follower_id <> followee_id)
);

create index follows_followee_idx on public.follows (followee_id, created_at desc);

alter table public.follows enable row level security;

create policy follows_select_own_or_admin
on public.follows for select to authenticated
using (
  follower_id = auth.uid() or followee_id = auth.uid() or private.is_admin()
);

create view public.follow_counts
with (security_invoker = false)
as
select
  p.id as user_id,
  (select count(*)::integer from public.follows f where f.followee_id = p.id) as followers,
  (select count(*)::integer from public.follows f where f.follower_id = p.id) as following
from public.profiles p
where p.account_status = 'active';

create or replace function public.toggle_follow(p_user_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_following boolean;
begin
  perform private.assert_active_user();

  if p_user_id = auth.uid() then
    raise exception using errcode = '22023', message = 'You cannot follow yourself';
  end if;
  if not exists (
    select 1 from public.profiles p
    where p.id = p_user_id and p.account_status = 'active'
  ) then
    raise exception using errcode = '23503', message = 'Profile not found';
  end if;

  perform private.enforce_rate_limit(
    'follow:' || auth.uid()::text, 60, interval '1 hour'
  );

  if exists (
    select 1 from public.follows f
    where f.follower_id = auth.uid() and f.followee_id = p_user_id
  ) then
    delete from public.follows f
    where f.follower_id = auth.uid() and f.followee_id = p_user_id;
    v_following := false;
  else
    insert into public.follows (follower_id, followee_id)
    values (auth.uid(), p_user_id);
    v_following := true;

    insert into public.notifications (user_id, type, title, body, link)
    values (
      p_user_id, 'system', 'New follower',
      (select display_name from public.profiles where id = auth.uid()) || ' started following you',
      '/profile/' || auth.uid()::text
    );
  end if;

  return jsonb_build_object(
    'user_id', p_user_id,
    'following', v_following,
    'followers', (
      select count(*)::integer from public.follows f where f.followee_id = p_user_id
    )
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- Notifications
-- ---------------------------------------------------------------------------

create table public.notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  type public.notification_type not null default 'system',
  title text not null check (char_length(btrim(title)) between 1 and 120),
  body text not null check (char_length(btrim(body)) between 1 and 1000),
  link text check (link is null or char_length(link) <= 500),
  read_at timestamptz,
  created_at timestamptz not null default now()
);

create index notifications_user_idx
  on public.notifications (user_id, created_at desc);
create index notifications_unread_idx
  on public.notifications (user_id) where read_at is null;

alter table public.notifications enable row level security;

create policy notifications_select_own
on public.notifications for select to authenticated
using (user_id = auth.uid());

create or replace function public.list_my_notifications(
  p_limit integer default 30,
  p_offset integer default 0
)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_limit integer := least(greatest(coalesce(p_limit, 30), 1), 100);
  v_offset integer := greatest(coalesce(p_offset, 0), 0);
  v_items jsonb;
  v_total integer;
begin
  perform private.assert_active_user();

  select count(*)::integer into v_total
  from public.notifications n where n.user_id = auth.uid();

  select coalesce(jsonb_agg(to_jsonb(n) order by n.created_at desc), '[]'::jsonb)
  into v_items
  from (
    select id, type, title, body, link, read_at, created_at
    from public.notifications
    where user_id = auth.uid()
    order by created_at desc
    limit v_limit offset v_offset
  ) n;

  return jsonb_build_object('items', v_items, 'total', v_total);
end;
$$;

create or replace function public.mark_notification_read(p_notification_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.assert_active_user();

  update public.notifications n
  set read_at = coalesce(n.read_at, now())
  where n.id = p_notification_id and n.user_id = auth.uid();

  if not found then
    raise exception using errcode = 'P0001', message = 'Notification not found';
  end if;
  return true;
end;
$$;

create or replace function public.mark_all_notifications_read()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_count integer;
begin
  perform private.assert_active_user();

  update public.notifications n
  set read_at = now()
  where n.user_id = auth.uid() and n.read_at is null;
  get diagnostics v_count = row_count;
  return v_count;
end;
$$;

-- ---------------------------------------------------------------------------
-- Collections
-- ---------------------------------------------------------------------------

create table public.collections (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.profiles(id) on delete cascade,
  title text not null check (char_length(btrim(title)) between 1 and 120),
  description text check (description is null or char_length(description) <= 1000),
  is_public boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index collections_owner_idx on public.collections (owner_id, created_at desc);

create trigger collections_set_updated_at
before update on public.collections
for each row execute function private.set_updated_at();

create table public.collection_items (
  collection_id uuid not null references public.collections(id) on delete cascade,
  product_id uuid not null references public.products(id) on delete cascade,
  note text check (note is null or char_length(note) <= 300),
  added_at timestamptz not null default now(),
  primary key (collection_id, product_id)
);

create index collection_items_product_idx on public.collection_items (product_id);

alter table public.collections enable row level security;
alter table public.collection_items enable row level security;

create policy collections_select_own_or_admin
on public.collections for select to authenticated
using (owner_id = auth.uid() or private.is_admin());

create policy collection_items_select_own_or_admin
on public.collection_items for select to authenticated
using (
  private.is_admin()
  or exists (
    select 1 from public.collections c
    where c.id = collection_id and c.owner_id = auth.uid()
  )
);

create view public.collections_public
with (security_invoker = false)
as
select
  c.id, c.title, c.description, c.created_at, c.updated_at,
  p.username as owner_username, p.display_name as owner_display_name,
  (select count(*)::integer from public.collection_items i
   where i.collection_id = c.id) as item_count
from public.collections c
join public.profiles p on p.id = c.owner_id
where c.is_public and p.account_status = 'active';

create view public.collection_items_public
with (security_invoker = false)
as
select
  i.collection_id, i.product_id, i.note, i.added_at,
  p.name as product_name, p.slug as product_slug,
  p.model_number, p.summary,
  b.name as brand_name, c.slug as category_slug
from public.collection_items i
join public.collections col on col.id = i.collection_id and col.is_public
join public.products p on p.id = i.product_id and p.status = 'published'
join public.brands b on b.id = p.brand_id
join public.categories c on c.id = p.category_id;

create or replace function public.create_collection(
  p_title text,
  p_description text default null,
  p_is_public boolean default false
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_id uuid;
begin
  perform private.assert_active_user();
  perform private.enforce_rate_limit(
    'collection:' || auth.uid()::text, 20, interval '1 day'
  );

  insert into public.collections (owner_id, title, description, is_public)
  values (auth.uid(), btrim(p_title), nullif(btrim(p_description), ''), coalesce(p_is_public, false))
  returning id into v_id;
  return v_id;
end;
$$;

create or replace function public.update_collection(
  p_collection_id uuid,
  p_title text default null,
  p_description text default null,
  p_is_public boolean default null
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.assert_active_user();

  update public.collections c
  set title = coalesce(nullif(btrim(p_title), ''), c.title),
      description = coalesce(p_description, c.description),
      is_public = coalesce(p_is_public, c.is_public)
  where c.id = p_collection_id and c.owner_id = auth.uid();

  if not found then
    raise exception using errcode = 'P0001', message = 'Collection not found';
  end if;
  return true;
end;
$$;

create or replace function public.delete_collection(p_collection_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.assert_active_user();

  delete from public.collections c
  where c.id = p_collection_id and c.owner_id = auth.uid();

  if not found then
    raise exception using errcode = 'P0001', message = 'Collection not found';
  end if;
  return true;
end;
$$;

create or replace function public.add_to_collection(
  p_collection_id uuid,
  p_product_id uuid,
  p_note text default null
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_count integer;
begin
  perform private.assert_active_user();

  if not exists (
    select 1 from public.collections c
    where c.id = p_collection_id and c.owner_id = auth.uid()
  ) then
    raise exception using errcode = 'P0001', message = 'Collection not found';
  end if;
  if not exists (
    select 1 from public.products p
    where p.id = p_product_id and p.status = 'published'
  ) then
    raise exception using errcode = '23503', message = 'Published product not found';
  end if;

  select count(*)::integer into v_count
  from public.collection_items i where i.collection_id = p_collection_id;
  if v_count >= 200 then
    raise exception using errcode = 'P0001', message = 'Collection limit reached';
  end if;

  insert into public.collection_items (collection_id, product_id, note)
  values (p_collection_id, p_product_id, nullif(btrim(p_note), ''))
  on conflict (collection_id, product_id) do update
    set note = excluded.note;
  return true;
end;
$$;

create or replace function public.remove_from_collection(
  p_collection_id uuid,
  p_product_id uuid
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.assert_active_user();

  delete from public.collection_items i
  where i.collection_id = p_collection_id
    and i.product_id = p_product_id
    and exists (
      select 1 from public.collections c
      where c.id = p_collection_id and c.owner_id = auth.uid()
    );

  return found;
end;
$$;

-- ---------------------------------------------------------------------------
-- Guides
-- ---------------------------------------------------------------------------

create table public.guides (
  id uuid primary key default gen_random_uuid(),
  slug citext not null unique check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  title text not null check (char_length(btrim(title)) between 3 and 200),
  summary text not null check (char_length(btrim(summary)) between 10 and 1000),
  body_md text not null check (char_length(body_md) between 20 and 50000),
  difficulty text not null default 'moderate'
    check (difficulty in ('easy', 'moderate', 'difficult', 'expert')),
  status public.guide_status not null default 'draft',
  product_id uuid references public.products(id) on delete set null,
  created_by uuid references public.profiles(id) on delete set null,
  published_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index guides_status_idx on public.guides (status, published_at desc);
create index guides_product_idx on public.guides (product_id);

create trigger guides_set_updated_at
before update on public.guides
for each row execute function private.set_updated_at();

create or replace function private.sync_guide_published_at()
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

create trigger guides_sync_published_at
before insert or update of status on public.guides
for each row execute function private.sync_guide_published_at();

create table public.guide_steps (
  id uuid primary key default gen_random_uuid(),
  guide_id uuid not null references public.guides(id) on delete cascade,
  position integer not null check (position between 1 and 100),
  title text not null check (char_length(btrim(title)) between 1 and 160),
  body text not null check (char_length(btrim(body)) between 1 and 5000),
  image_path text,
  unique (guide_id, position)
);

create index guide_steps_guide_idx on public.guide_steps (guide_id, position);

alter table public.guides enable row level security;
alter table public.guide_steps enable row level security;

create policy guides_select_admin
on public.guides for select to authenticated
using (private.is_admin());

create policy guides_admin_write
on public.guides for all to authenticated
using (private.is_admin()) with check (private.is_admin());

create policy guide_steps_select_admin
on public.guide_steps for select to authenticated
using (private.is_admin());

create policy guide_steps_admin_write
on public.guide_steps for all to authenticated
using (private.is_admin()) with check (private.is_admin());

create view public.guides_public
with (security_invoker = false)
as
select
  g.id, g.slug, g.title, g.summary, g.body_md, g.difficulty,
  g.published_at, g.updated_at,
  g.product_id,
  pr.name as product_name, pr.slug as product_slug,
  coalesce(au.display_name, 'Compat Checker editorial') as author_display_name,
  (select count(*)::integer from public.guide_steps s where s.guide_id = g.id)
    as step_count
from public.guides g
left join public.products pr on pr.id = g.product_id and pr.status = 'published'
left join public.profiles au on au.id = g.created_by and au.account_status = 'active'
where g.status = 'published';

create view public.guide_steps_public
with (security_invoker = false)
as
select s.id, s.guide_id, s.position, s.title, s.body, s.image_path
from public.guide_steps s
join public.guides g on g.id = s.guide_id
where g.status = 'published';

create or replace function public.get_guide_detail(p_guide_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_guide jsonb;
  v_steps jsonb;
  v_comments integer;
begin
  select to_jsonb(g) into v_guide
  from public.guides_public g where g.id = p_guide_id;
  if v_guide is null then
    return null;
  end if;

  select coalesce(jsonb_agg(to_jsonb(s) order by s.position), '[]'::jsonb)
  into v_steps
  from public.guide_steps_public s where s.guide_id = p_guide_id;

  select count(*)::integer into v_comments
  from public.comments c
  where c.target_type = 'guide' and c.target_id = p_guide_id and c.status = 'visible';

  return v_guide || jsonb_build_object('steps', v_steps, 'comment_count', v_comments);
end;
$$;

create trigger guides_audit
after insert or update on public.guides
for each row execute function private.audit_row_change();

-- ---------------------------------------------------------------------------
-- Media asset registry and storage policies
-- ---------------------------------------------------------------------------

create table public.media_assets (
  id uuid primary key default gen_random_uuid(),
  bucket text not null check (bucket in ('media', 'evidence')),
  storage_path text not null check (char_length(storage_path) between 3 and 500),
  owner_id uuid references public.profiles(id) on delete set null,
  alt_text text check (alt_text is null or char_length(alt_text) <= 250),
  status public.media_status not null default 'pending',
  target_type text check (target_type in ('product', 'guide', 'rule_evidence')),
  target_id uuid,
  created_at timestamptz not null default now(),
  unique (bucket, storage_path)
);

alter table public.media_assets enable row level security;

create policy media_assets_admin_all
on public.media_assets for all to authenticated
using (private.is_admin()) with check (private.is_admin());

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('media', 'media', true, 10485760, array['image/jpeg', 'image/png', 'image/webp']),
  ('evidence', 'evidence', false, 20971520, array['application/pdf', 'image/jpeg', 'image/png'])
on conflict (id) do nothing;

do $storage_policies$
begin
  if to_regclass('storage.objects') is null then
    raise notice 'storage.objects is absent; skipping storage RLS policies';
    return;
  end if;

  execute $p$
    create policy media_public_read on storage.objects
    for select to anon, authenticated
    using (bucket_id = 'media')
  $p$;

  execute $p$
    create policy media_admin_all on storage.objects
    for all to authenticated
    using (bucket_id in ('media', 'evidence') and private.is_admin())
    with check (bucket_id in ('media', 'evidence') and private.is_admin())
  $p$;
exception
  when duplicate_object then null;
end;
$storage_policies$;

-- ---------------------------------------------------------------------------
-- Account lifecycle and current-user payload
-- ---------------------------------------------------------------------------

create or replace function public.request_account_deletion()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.assert_active_user();
  perform private.enforce_rate_limit(
    'delete_request:' || auth.uid()::text, 3, interval '1 day'
  );

  update public.profiles p
  set account_status = 'deletion_pending',
      deletion_requested_at = now()
  where p.id = auth.uid();

  return jsonb_build_object(
    'status', 'deletion_pending',
    'requested_at', now(),
    'purge_after', now() + interval '30 days',
    'message', 'Your profile is hidden and scheduled for deletion after 30 days. Contact support to cancel.'
  );
end;
$$;

-- Cancellation is allowed while the account is in deletion_pending, so it cannot
-- use assert_active_user.
create or replace function public.cancel_account_deletion()
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception using errcode = '42501', message = 'Authentication required';
  end if;

  update public.profiles p
  set account_status = 'active',
      deletion_requested_at = null
  where p.id = auth.uid() and p.account_status = 'deletion_pending';

  if not found then
    raise exception using errcode = 'P0001', message = 'No pending deletion to cancel';
  end if;
  return true;
end;
$$;

create or replace function public.me()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_profile public.profiles;
  v_points integer;
  v_unread integer;
  v_favorites integer;
begin
  if auth.uid() is null then
    raise exception using errcode = '42501', message = 'Authentication required';
  end if;

  select * into v_profile from public.profiles p where p.id = auth.uid();
  if v_profile.id is null then
    return null;
  end if;

  select coalesce(sum(l.delta)::integer, 0) into v_points
  from public.points_ledger l where l.user_id = auth.uid();

  select count(*)::integer into v_unread
  from public.notifications n
  where n.user_id = auth.uid() and n.read_at is null;

  select count(*)::integer into v_favorites
  from public.favorites f where f.user_id = auth.uid();

  return jsonb_build_object(
    'id', v_profile.id,
    'username', v_profile.username,
    'display_name', v_profile.display_name,
    'bio', v_profile.bio,
    'avatar_url', v_profile.avatar_url,
    'role', v_profile.role,
    'account_status', v_profile.account_status,
    'locale', v_profile.locale,
    'created_at', v_profile.created_at,
    'deletion_requested_at', v_profile.deletion_requested_at,
    'reputation_points', v_points,
    'reputation_tier', private.reputation_tier(v_points),
    'unread_notifications', v_unread,
    'favorite_count', v_favorites,
    'capabilities', jsonb_build_object(
      'can_moderate', v_profile.role in ('trusted', 'admin')
        and v_profile.account_status = 'active',
      'can_admin', v_profile.role = 'admin'
        and v_profile.account_status = 'active',
      'can_contribute', v_profile.account_status = 'active'
    )
  );
end;
$$;

-- Public statistics are aggregated and contain no personal data.
create view public.public_stats
with (security_invoker = false)
as
select
  (select count(*)::integer from public.products where status = 'published') as products,
  (select count(*)::integer from public.compatibility_rules where status = 'published') as rules,
  (select count(*)::integer from public.guides where status = 'published') as guides,
  (select count(*)::integer from public.rule_evidence e
     join public.compatibility_rules r on r.id = e.rule_id
     where r.status = 'published') as evidence_items;

comment on table public.follows is
  'Follower graph. Only aggregate counts are public.';
comment on table public.notifications is
  'User-owned notifications. Read and marked through RPCs.';
comment on table public.media_assets is
  'Admin-managed media registry. Public media is served from the media bucket.';
