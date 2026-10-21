-- Test helpers. These are created only in the disposable test database.
create schema if not exists tests;

grant usage on schema tests to anon, authenticated;

create or replace function tests.assert_true(p_condition boolean, p_label text)
returns void
language plpgsql
as $$
begin
  if p_condition is not true then
    raise exception using errcode = 'P0001', message = 'FAIL: ' || p_label;
  end if;
end;
$$;

create or replace function tests.assert_int(p_actual integer, p_expected integer, p_label text)
returns void
language plpgsql
as $$
begin
  if p_actual is distinct from p_expected then
    raise exception using errcode = 'P0001',
      message = format('FAIL: %s (expected %s, got %s)', p_label, p_expected, p_actual);
  end if;
end;
$$;

create or replace function tests.assert_text(p_actual text, p_expected text, p_label text)
returns void
language plpgsql
as $$
begin
  if p_actual is distinct from p_expected then
    raise exception using errcode = 'P0001',
      message = format('FAIL: %s (expected %L, got %L)', p_label, p_expected, p_actual);
  end if;
end;
$$;

-- Executes a statement and requires a specific SQLSTATE. The inner subtransaction
-- means a caught failure does not abort the surrounding test transaction.
create or replace function tests.expect_error(p_sql text, p_state text, p_label text)
returns void
language plpgsql
as $$
begin
  begin
    execute p_sql;
    raise exception using errcode = 'P0001', message = '__EXPECTED_ERROR_NOT_RAISED__';
  exception
    when others then
      if sqlerrm = '__EXPECTED_ERROR_NOT_RAISED__' then
        raise exception using errcode = 'P0001',
          message = format('FAIL: %s (statement unexpectedly succeeded)', p_label);
      end if;
      if sqlstate <> p_state then
        raise exception using errcode = 'P0001',
          message = format('FAIL: %s (expected SQLSTATE %s, got %s: %s)',
                           p_label, p_state, sqlstate, sqlerrm);
      end if;
  end;
end;
$$;

create or replace function tests.assert_sql_error(p_sql text, p_state text, p_label text)
returns void
language sql
as $$
  select tests.expect_error(p_sql, p_state, p_label);
$$;

-- Moderators cannot read the contributions base table directly (RLS), so tests
-- resolve ids through the same admin RPC the console uses.
create or replace function tests.contribution_id(p_slug text)
returns uuid
language sql
stable
as $$
  select (item ->> 'id')::uuid
  from jsonb_array_elements(
    public.admin_list_contributions(null, null, 100, 0) -> 'items'
  ) item
  where item -> 'payload' ->> 'slug' = p_slug
  order by item ->> 'created_at' desc
  limit 1;
$$;

create or replace function tests.pending_contribution_of_kind(p_kind text)
returns uuid
language sql
stable
as $$
  select (item ->> 'id')::uuid
  from jsonb_array_elements(
    public.admin_list_contributions('pending', p_kind, 100, 0) -> 'items'
  ) item
  limit 1;
$$;

create or replace function tests.contribution_status(p_slug text)
returns text
language sql
stable
as $$
  select item ->> 'status'
  from jsonb_array_elements(
    public.admin_list_contributions(null, null, 100, 0) -> 'items'
  ) item
  where item -> 'payload' ->> 'slug' = p_slug
  order by item ->> 'created_at' desc
  limit 1;
$$;

grant execute on function tests.contribution_id(text) to authenticated;
grant execute on function tests.pending_contribution_of_kind(text) to authenticated;
grant execute on function tests.contribution_status(text) to authenticated;

grant execute on all functions in schema tests to anon, authenticated;
