-- 002: identity, profiles, audit log, and profile self-service.

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username citext not null unique
    check (username ~ '^[a-z0-9_]{3,30}$'),
  display_name text not null
    check (char_length(btrim(display_name)) between 1 and 80),
  bio text check (bio is null or char_length(bio) <= 500),
  avatar_url text check (avatar_url is null or avatar_url ~ '^https://'),
  role public.app_role not null default 'member',
  account_status public.account_status not null default 'active',
  locale text not null default 'en'
    check (locale ~ '^[a-z]{2}(-[A-Z]{2})?$'),
  moderation_note text,
  deletion_requested_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index profiles_role_idx on public.profiles (role);
create index profiles_status_idx on public.profiles (account_status);
create index profiles_display_name_trgm_idx
  on public.profiles using gin (display_name gin_trgm_ops);

create trigger profiles_set_updated_at
before update on public.profiles
for each row execute function private.set_updated_at();

alter table public.profiles enable row level security;

create or replace function private.is_active_user()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.profiles p
    where p.id = auth.uid() and p.account_status = 'active'
  );
$$;

create or replace function private.is_admin()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.profiles p
    where p.id = auth.uid()
      and p.role = 'admin'
      and p.account_status = 'active'
  );
$$;

create or replace function private.current_role()
returns public.app_role
language sql
stable
security definer
set search_path = ''
as $$
  select p.role from public.profiles p where p.id = auth.uid();
$$;

create or replace function private.assert_admin()
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if not private.is_admin() then
    raise exception using errcode = '42501', message = 'Administrator role required';
  end if;
end;
$$;

create policy profiles_select_self_or_admin
on public.profiles for select
to authenticated
using (id = auth.uid() or private.is_admin());

-- Public profile fields are safe: email is never stored in this table.
create policy profiles_select_active_public
on public.profiles for select
to authenticated
using (account_status = 'active');

-- No INSERT/UPDATE/DELETE policies: identity rows are created by the auth
-- trigger and mutated by SECURITY DEFINER functions only.

create table public.audit_logs (
  id uuid primary key default gen_random_uuid(),
  actor_id uuid,
  action text not null check (char_length(action) between 3 and 80),
  entity_type text not null check (char_length(entity_type) between 1 and 60),
  entity_id uuid,
  before_data jsonb,
  after_data jsonb,
  context jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index audit_logs_created_at_idx on public.audit_logs (created_at desc);
create index audit_logs_actor_idx on public.audit_logs (actor_id, created_at desc);
create index audit_logs_entity_idx on public.audit_logs (entity_type, entity_id, created_at desc);

alter table public.audit_logs enable row level security;

create policy audit_logs_admin_select
on public.audit_logs for select
to authenticated
using (private.is_admin());

-- Audit rows are append-only; clients receive no INSERT/UPDATE/DELETE policy.

create or replace function private.audit_row_change()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_entity_id uuid;
begin
  v_entity_id := coalesce(
    (to_jsonb(new) ->> 'id')::uuid,
    (to_jsonb(old) ->> 'id')::uuid
  );

  insert into public.audit_logs (
    actor_id, action, entity_type, entity_id, before_data, after_data, context
  )
  values (
    auth.uid(),
    lower(tg_table_name) || '.' || lower(tg_op),
    tg_table_name,
    v_entity_id,
    case when tg_op in ('UPDATE', 'DELETE') then to_jsonb(old) end,
    case when tg_op in ('INSERT', 'UPDATE') then to_jsonb(new) end,
    jsonb_build_object('schema', tg_table_schema, 'operation', tg_op)
  );

  return coalesce(new, old);
end;
$$;

-- New auth users receive a profile, never a role chosen by the client.
create or replace function private.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_base text;
  v_username text;
  v_display_name text;
begin
  v_base := lower(regexp_replace(
    coalesce(nullif(split_part(new.email, '@', 1), ''), 'user'),
    '[^a-zA-Z0-9_]+', '_', 'g'
  ));
  v_base := btrim(v_base, '_');
  if char_length(v_base) < 3 then
    v_base := 'user';
  end if;

  v_username := left(v_base, 18) || '_' || left(replace(new.id::text, '-', ''), 8);
  v_display_name := nullif(btrim(coalesce(new.raw_user_meta_data ->> 'display_name', '')), '');
  if v_display_name is null then
    v_display_name := initcap(replace(v_base, '_', ' '));
  end if;

  insert into public.profiles (id, username, display_name, role, account_status)
  values (new.id, v_username, left(v_display_name, 80), 'member', 'active')
  on conflict (id) do nothing;

  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
after insert on auth.users
for each row execute function private.handle_new_user();

create or replace function public.update_my_profile(
  p_display_name text default null,
  p_bio text default null,
  p_avatar_url text default null,
  p_locale text default null,
  p_username text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_profile public.profiles;
  v_username text;
begin
  perform private.assert_active_user();

  if p_display_name is not null then
    if char_length(btrim(p_display_name)) not between 1 and 80 then
      raise exception using errcode = '22023', message = 'Display name must be 1-80 characters';
    end if;
  end if;

  if p_bio is not null and char_length(p_bio) > 500 then
    raise exception using errcode = '22023', message = 'Bio must be at most 500 characters';
  end if;

  if p_avatar_url is not null and p_avatar_url !~ '^https://' then
    raise exception using errcode = '22023', message = 'Avatar URL must use HTTPS';
  end if;

  if p_locale is not null and p_locale !~ '^[a-z]{2}(-[A-Z]{2})?$' then
    raise exception using errcode = '22023', message = 'Invalid locale';
  end if;

  if p_username is not null then
    v_username := lower(btrim(p_username));
    if v_username !~ '^[a-z0-9_]{3,30}$' then
      raise exception using errcode = '22023',
        message = 'Username must be 3-30 lowercase letters, numbers, or underscores';
    end if;
    if exists (
      select 1 from public.profiles p
      where p.username = v_username and p.id <> auth.uid()
    ) then
      raise exception using errcode = '23505', message = 'Username is already taken';
    end if;
  end if;

  update public.profiles p
  set display_name = coalesce(btrim(p_display_name), p.display_name),
      bio = coalesce(p_bio, p.bio),
      avatar_url = coalesce(p_avatar_url, p.avatar_url),
      locale = coalesce(p_locale, p.locale),
      username = coalesce(v_username, p.username)
  where p.id = auth.uid()
  returning * into v_profile;

  return to_jsonb(v_profile) - 'moderation_note';
end;
$$;

revoke all on function public.update_my_profile(text, text, text, text, text) from public;
grant execute on function public.update_my_profile(text, text, text, text, text) to authenticated;

comment on table public.profiles is
  'Identity extension keyed to auth.users. Role and status are server-owned.';
comment on table public.audit_logs is
  'Append-only administrative audit trail. Admin-readable only.';
