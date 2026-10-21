-- 006: append-only reputation ledger, tiers, and public reputation projection.

create table public.points_ledger (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  delta integer not null check (delta between -1000 and 1000 and delta <> 0),
  reason text not null check (reason in (
    'contribution_approved', 'comment_created', 'vote_cast',
    'report_accepted', 'moderation_penalty', 'manual_adjustment'
  )),
  reference_type text,
  reference_id uuid,
  dedupe_key text not null unique,
  created_by uuid references public.profiles(id) on delete set null,
  created_at timestamptz not null default now()
);

create index points_ledger_user_idx
  on public.points_ledger (user_id, created_at desc);

create or replace function private.forbid_ledger_mutation()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  raise exception using errcode = '42501',
    message = 'The reputation ledger is append-only';
end;
$$;

create trigger points_ledger_append_only
before update or delete on public.points_ledger
for each row execute function private.forbid_ledger_mutation();

alter table public.points_ledger enable row level security;

create policy points_ledger_select_own_or_admin
on public.points_ledger for select to authenticated
using (user_id = auth.uid() or private.is_admin());

-- Only SECURITY DEFINER functions may write points. Idempotency is enforced by
-- dedupe_key, so retries and duplicate triggers cannot inflate reputation.
create or replace function private.award_points(
  p_user_id uuid,
  p_delta integer,
  p_reason text,
  p_reference_type text,
  p_reference_id uuid,
  p_dedupe_key text
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_inserted boolean;
begin
  if p_delta = 0 or p_delta < -1000 or p_delta > 1000 then
    raise exception using errcode = '22023', message = 'Invalid points delta';
  end if;

  insert into public.points_ledger (
    user_id, delta, reason, reference_type, reference_id, dedupe_key, created_by
  )
  values (
    p_user_id, p_delta, p_reason, p_reference_type, p_reference_id,
    p_dedupe_key, auth.uid()
  )
  on conflict (dedupe_key) do nothing
  returning true into v_inserted;

  return coalesce(v_inserted, false);
end;
$$;

revoke all on function private.award_points(uuid, integer, text, text, uuid, text) from public;

create view public.profiles_public
with (security_invoker = false)
as
select
  p.id,
  p.username,
  p.display_name,
  p.bio,
  p.avatar_url,
  p.role,
  p.created_at,
  coalesce((
    select sum(l.delta)::integer from public.points_ledger l where l.user_id = p.id
  ), 0) as reputation_points,
  private.reputation_tier(coalesce((
    select sum(l.delta)::integer from public.points_ledger l where l.user_id = p.id
  ), 0)) as reputation_tier,
  (
    select count(*)::integer from public.contributions c
    where c.contributor_id = p.id and c.status = 'approved'
  ) as approved_contributions,
  (
    select count(*)::integer from public.compatibility_rules r
    where r.created_by = p.id and r.status = 'published'
  ) as published_rules
from public.profiles p
where p.account_status = 'active';

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

create or replace function public.my_reputation()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_total integer;
  v_entries jsonb;
begin
  perform private.assert_active_user();

  select coalesce(sum(l.delta)::integer, 0) into v_total
  from public.points_ledger l
  where l.user_id = auth.uid();

  select coalesce(jsonb_agg(jsonb_build_object(
    'id', l.id, 'delta', l.delta, 'reason', l.reason,
    'reference_type', l.reference_type, 'reference_id', l.reference_id,
    'created_at', l.created_at
  ) order by l.created_at desc), '[]'::jsonb)
  into v_entries
  from (
    select * from public.points_ledger l
    where l.user_id = auth.uid()
    order by l.created_at desc
    limit 50
  ) l;

  return jsonb_build_object(
    'points', v_total,
    'tier', private.reputation_tier(v_total),
    'entries', v_entries
  );
end;
$$;

create or replace function public.reputation_leaderboard(p_limit integer default 20)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_limit integer := least(greatest(coalesce(p_limit, 20), 1), 50);
  v_items jsonb;
begin
  select coalesce(jsonb_agg(to_jsonb(p) order by p.reputation_points desc, p.display_name), '[]'::jsonb)
  into v_items
  from (
    select id, username, display_name, avatar_url, role,
           reputation_points, reputation_tier, approved_contributions
    from public.profiles_public
    where reputation_points > 0
    order by reputation_points desc, display_name
    limit v_limit
  ) p;

  return jsonb_build_object('items', v_items);
end;
$$;

comment on table public.points_ledger is
  'Append-only reputation history. Updates and deletes are rejected by trigger.';
comment on function private.award_points(uuid, integer, text, text, uuid, text) is
  'Idempotent points award. Callable only from SECURITY DEFINER routines.';
