-- 011: privileged account-purge worker support.
-- Deletion is a delayed, auditable maintenance task. It is intentionally not
-- callable by clients; only the service role (used by the Edge Function) may
-- invoke it. Content owned solely by the account cascades; authored catalog
-- records are retained with the author reference nulled by existing FKs.

create or replace function public.purge_pending_accounts(p_limit integer default 100)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_limit integer := least(greatest(coalesce(p_limit, 100), 1), 500);
  v_result jsonb;
begin
  with victims as (
    select p.id
    from public.profiles p
    where p.account_status = 'deletion_pending'
      and p.deletion_requested_at is not null
      and p.deletion_requested_at <= now() - interval '30 days'
    order by p.deletion_requested_at
    limit v_limit
    for update of p skip locked
  ),
  removed as (
    delete from auth.users u
    using victims v
    where u.id = v.id
    returning u.id
  )
  select jsonb_build_object(
    'purged', count(*)::integer,
    'ids', coalesce(jsonb_agg(id), '[]'::jsonb),
    'cutoff', now() - interval '30 days'
  )
  into v_result
  from removed;

  return v_result;
end;
$$;

revoke all on function public.purge_pending_accounts(integer)
  from public, anon, authenticated;
grant execute on function public.purge_pending_accounts(integer) to service_role;

comment on function public.purge_pending_accounts(integer) is
  'Service-role maintenance: permanently removes accounts whose 30-day deletion window elapsed.';
