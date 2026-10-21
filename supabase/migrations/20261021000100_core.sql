-- 001: extensions, enums, shared helpers.
-- All timestamps are UTC. Domain values are deliberately narrow enums so that
-- the database, not a client, owns the vocabulary.

create extension if not exists pgcrypto;
create extension if not exists citext;
create extension if not exists pg_trgm;
create extension if not exists unaccent;

create schema if not exists private;
revoke all on schema private from public;
grant usage on schema private to service_role;

-- pg_trgm is installed in `public` on a plain PostgreSQL install and in
-- `extensions` on Supabase. Security-definer functions lock search_path to the
-- empty string, so extension calls are routed through this explicit resolver.
create or replace function private.text_similarity(p_left text, p_right text)
returns real
language sql
immutable
set search_path = public, extensions
as $$
  select similarity(coalesce(p_left, ''), coalesce(p_right, ''));
$$;

revoke all on function private.text_similarity(text, text) from public;

create or replace function private.text_word_similarity(p_query text, p_target text)
returns real
language sql
immutable
set search_path = public, extensions
as $$
  select word_similarity(coalesce(p_query, ''), coalesce(p_target, ''));
$$;

revoke all on function private.text_word_similarity(text, text) from public;

create type public.app_role as enum ('member', 'contributor', 'trusted', 'admin');

create type public.account_status as enum (
  'active', 'suspended', 'banned', 'deletion_pending', 'deleted'
);

create type public.product_status as enum ('draft', 'published', 'archived');

create type public.rule_status as enum (
  'draft', 'pending_review', 'published', 'rejected', 'retired'
);

create type public.relationship_type as enum (
  'fits', 'works_with', 'same_as', 'variant_of', 'not_compatible_with'
);

create type public.verification_level as enum (
  'official', 'community_verified', 'needs_verification', 'experimental'
);

create type public.risk_level as enum ('none', 'low', 'moderate', 'high', 'critical');

create type public.evidence_type as enum (
  'oem_document', 'service_bulletin', 'teardown', 'lab_test',
  'community_report', 'other'
);

create type public.vote_type as enum ('confirm', 'dispute');

create type public.contribution_kind as enum (
  'new_product', 'product_edit', 'new_rule', 'evidence', 'guide'
);

create type public.contribution_status as enum (
  'pending', 'needs_changes', 'approved', 'rejected', 'withdrawn'
);

create type public.request_status as enum ('open', 'planned', 'fulfilled', 'declined');

create type public.report_status as enum ('open', 'investigating', 'resolved', 'dismissed');

create type public.comment_status as enum ('visible', 'hidden', 'deleted');

create type public.guide_status as enum ('draft', 'pending_review', 'published', 'archived');

create type public.media_status as enum ('pending', 'approved', 'rejected');

create type public.notification_type as enum (
  'contribution_review', 'report_update', 'comment_reply', 'role_changed',
  'moderation', 'system'
);

-- Updated-at maintenance is centralised so every mutable table behaves the same.
create or replace function private.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

-- Raise a PostgREST-friendly 403 for inactive accounts.
create or replace function private.assert_active_user()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_status public.account_status;
begin
  if auth.uid() is null then
    raise exception using errcode = '42501', message = 'Authentication required';
  end if;

  select p.account_status into v_status
  from public.profiles p
  where p.id = auth.uid();

  if v_status is null then
    raise exception using errcode = '42501', message = 'Profile not found';
  end if;

  if v_status <> 'active' then
    raise exception using errcode = '42501', message = 'Account is not active';
  end if;
end;
$$;

-- is_active_user(), is_admin(), current_role(), and assert_admin() are defined
-- in 002 after public.profiles exists (SQL functions validate references at
-- creation time).

create or replace function private.reputation_tier(p_points integer)
returns text
language sql
immutable
set search_path = ''
as $$
  select case
    when p_points >= 1000 then 'expert'
    when p_points >= 500 then 'trusted'
    when p_points >= 150 then 'contributor'
    when p_points >= 25 then 'newcomer'
    else 'visitor'
  end;
$$;

-- Fixed-window rate limiting. Keys are server-built and never contain raw IPs.
create table private.rate_limits (
  key text primary key,
  window_start timestamptz not null default now(),
  count integer not null default 0 check (count >= 0)
);

revoke all on private.rate_limits from public;
grant all on private.rate_limits to service_role;

create or replace function private.enforce_rate_limit(
  p_key text,
  p_limit integer,
  p_window interval
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_count integer;
begin
  insert into private.rate_limits as rl (key, window_start, count)
  values (p_key, now(), 1)
  on conflict (key) do update
    set count = case
          when rl.window_start < now() - p_window then 1
          else rl.count + 1
        end,
        window_start = case
          when rl.window_start < now() - p_window then now()
          else rl.window_start
        end
  returning rl.count into v_count;

  if v_count > p_limit then
    raise exception using errcode = 'P0001', message = 'Rate limit exceeded';
  end if;
end;
$$;

comment on schema private is
  'Internal helpers only. Not exposed through PostgREST; no direct client grants.';
comment on table private.rate_limits is
  'Fixed-window counters. Keys are hashed or server-built identifiers, not raw IP addresses.';
