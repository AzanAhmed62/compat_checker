-- 005: community surfaces, votes, requests, comments, reports, contributions,
-- and the compatibility evaluation RPC. Public vote data is aggregate-only.

-- ---------------------------------------------------------------------------
-- Rule votes and aggregate counters
-- ---------------------------------------------------------------------------

create table public.rule_votes (
  rule_id uuid not null references public.compatibility_rules(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  vote public.vote_type not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (rule_id, user_id)
);

create index rule_votes_user_idx on public.rule_votes (user_id, created_at desc);

create table public.rule_vote_counts (
  rule_id uuid primary key references public.compatibility_rules(id) on delete cascade,
  confirms integer not null default 0 check (confirms >= 0),
  disputes integer not null default 0 check (disputes >= 0),
  updated_at timestamptz not null default now()
);

create or replace function private.refresh_rule_vote_counts()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_rule_id uuid := coalesce(new.rule_id, old.rule_id);
begin
  insert into public.rule_vote_counts as c (rule_id, confirms, disputes, updated_at)
  select v_rule_id,
         count(*) filter (where v.vote = 'confirm')::integer,
         count(*) filter (where v.vote = 'dispute')::integer,
         now()
  from public.rule_votes v
  where v.rule_id = v_rule_id
  on conflict (rule_id) do update
    set confirms = excluded.confirms,
        disputes = excluded.disputes,
        updated_at = now();
  return coalesce(new, old);
end;
$$;

create trigger rule_votes_refresh_counts
after insert or update or delete on public.rule_votes
for each row execute function private.refresh_rule_vote_counts();

alter table public.rule_votes enable row level security;
alter table public.rule_vote_counts enable row level security;

create policy rule_votes_select_own_or_admin
on public.rule_votes for select to authenticated
using (user_id = auth.uid() or private.is_admin());

create policy rule_vote_counts_public_select
on public.rule_vote_counts for select to anon, authenticated
using (true);

create or replace function public.cast_rule_vote(p_rule_id uuid, p_vote text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_vote public.vote_type;
  v_existed boolean;
  v_counts public.rule_vote_counts;
begin
  perform private.assert_active_user();

  if p_vote not in ('confirm', 'dispute') then
    raise exception using errcode = '22023', message = 'Vote must be confirm or dispute';
  end if;
  v_vote := p_vote::public.vote_type;

  if not exists (
    select 1 from public.compatibility_rules r
    where r.id = p_rule_id and r.status = 'published'
  ) then
    raise exception using errcode = 'P0001', message = 'Rule is not published';
  end if;

  perform private.enforce_rate_limit(
    'rule_vote:' || auth.uid()::text, 60, interval '1 hour'
  );

  select exists (
    select 1 from public.rule_votes v
    where v.rule_id = p_rule_id and v.user_id = auth.uid()
  ) into v_existed;

  insert into public.rule_votes (rule_id, user_id, vote)
  values (p_rule_id, auth.uid(), v_vote)
  on conflict (rule_id, user_id) do update
    set vote = excluded.vote, updated_at = now();

  if not v_existed then
    perform private.award_points(
      auth.uid(), 1, 'vote_cast', 'compatibility_rule', p_rule_id,
      'rule-vote:' || p_rule_id::text || ':' || auth.uid()::text
    );
  end if;

  select * into v_counts from public.rule_vote_counts where rule_id = p_rule_id;

  return jsonb_build_object(
    'rule_id', p_rule_id,
    'my_vote', v_vote,
    'confirms', coalesce(v_counts.confirms, 0),
    'disputes', coalesce(v_counts.disputes, 0)
  );
end;
$$;

create or replace function public.remove_rule_vote(p_rule_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_counts public.rule_vote_counts;
begin
  perform private.assert_active_user();

  delete from public.rule_votes v
  where v.rule_id = p_rule_id and v.user_id = auth.uid();

  select * into v_counts from public.rule_vote_counts where rule_id = p_rule_id;

  return jsonb_build_object(
    'rule_id', p_rule_id,
    'my_vote', null,
    'confirms', coalesce(v_counts.confirms, 0),
    'disputes', coalesce(v_counts.disputes, 0)
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- Favorites
-- ---------------------------------------------------------------------------

create table public.favorites (
  user_id uuid not null references public.profiles(id) on delete cascade,
  product_id uuid not null references public.products(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, product_id)
);

create index favorites_product_idx on public.favorites (product_id);

alter table public.favorites enable row level security;

create policy favorites_select_own_or_admin
on public.favorites for select to authenticated
using (user_id = auth.uid() or private.is_admin());

create or replace function public.toggle_favorite(p_product_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_exists boolean;
  v_is_favorite boolean;
begin
  perform private.assert_active_user();

  if not exists (
    select 1 from public.products p
    where p.id = p_product_id and p.status = 'published'
  ) then
    raise exception using errcode = 'P0001', message = 'Product is not published';
  end if;

  perform private.enforce_rate_limit(
    'favorite:' || auth.uid()::text, 120, interval '1 hour'
  );

  select exists (
    select 1 from public.favorites f
    where f.user_id = auth.uid() and f.product_id = p_product_id
  ) into v_exists;

  if v_exists then
    delete from public.favorites f
    where f.user_id = auth.uid() and f.product_id = p_product_id;
    v_is_favorite := false;
  else
    insert into public.favorites (user_id, product_id)
    values (auth.uid(), p_product_id);
    v_is_favorite := true;
  end if;

  return jsonb_build_object(
    'product_id', p_product_id,
    'is_favorite', v_is_favorite,
    'favorite_count', (
      select count(*)::integer from public.favorites f where f.product_id = p_product_id
    )
  );
end;
$$;

create or replace function public.list_my_favorites()
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

  select coalesce(jsonb_agg(to_jsonb(p) order by f.created_at desc), '[]'::jsonb)
  into v_items
  from public.favorites f
  join public.products_public p on p.id = f.product_id
  where f.user_id = auth.uid();

  return jsonb_build_object('items', v_items);
end;
$$;

-- ---------------------------------------------------------------------------
-- Product requests and private votes
-- ---------------------------------------------------------------------------

create table public.product_requests (
  id uuid primary key default gen_random_uuid(),
  title text not null check (char_length(btrim(title)) between 5 and 150),
  description text not null check (char_length(btrim(description)) between 10 and 5000),
  category_id uuid references public.categories(id) on delete set null,
  status public.request_status not null default 'open',
  created_by uuid not null references public.profiles(id) on delete cascade,
  is_anonymous boolean not null default true,
  fulfilled_product_id uuid references public.products(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index requests_one_open_title_per_user
  on public.product_requests (created_by, lower(title))
  where status in ('open', 'planned');

create index requests_status_idx on public.product_requests (status, created_at desc);

create trigger requests_set_updated_at
before update on public.product_requests
for each row execute function private.set_updated_at();

create table public.request_votes (
  request_id uuid not null references public.product_requests(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (request_id, user_id)
);

create table public.request_vote_counts (
  request_id uuid primary key references public.product_requests(id) on delete cascade,
  votes integer not null default 0 check (votes >= 0),
  updated_at timestamptz not null default now()
);

create or replace function private.refresh_request_vote_counts()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_request_id uuid := coalesce(new.request_id, old.request_id);
begin
  insert into public.request_vote_counts as c (request_id, votes, updated_at)
  select v_request_id, count(*)::integer, now()
  from public.request_votes v
  where v.request_id = v_request_id
  on conflict (request_id) do update
    set votes = excluded.votes, updated_at = now();
  return coalesce(new, old);
end;
$$;

create trigger request_votes_refresh_counts
after insert or delete on public.request_votes
for each row execute function private.refresh_request_vote_counts();

alter table public.product_requests enable row level security;
alter table public.request_votes enable row level security;
alter table public.request_vote_counts enable row level security;

create policy requests_select_own_or_admin
on public.product_requests for select to authenticated
using (created_by = auth.uid() or private.is_admin());

create policy request_votes_select_own_or_admin
on public.request_votes for select to authenticated
using (user_id = auth.uid() or private.is_admin());

create policy request_vote_counts_public_select
on public.request_vote_counts for select to anon, authenticated
using (true);

create view public.requests_public
with (security_invoker = false)
as
select
  r.id, r.title, r.description, r.status, r.is_anonymous,
  r.created_at, r.updated_at, r.fulfilled_product_id,
  c.slug as category_slug, c.name as category_name,
  coalesce(vc.votes, 0) as vote_count
from public.product_requests r
left join public.categories c on c.id = r.category_id
left join public.request_vote_counts vc on vc.request_id = r.id;

create or replace function public.create_product_request(
  p_title text,
  p_description text,
  p_category_id uuid default null
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
    'request:' || auth.uid()::text, 5, interval '1 day'
  );

  if p_category_id is not null and not exists (
    select 1 from public.categories c where c.id = p_category_id and c.is_public
  ) then
    raise exception using errcode = '22023', message = 'Invalid category';
  end if;

  insert into public.product_requests (title, description, category_id, created_by)
  values (btrim(p_title), btrim(p_description), p_category_id, auth.uid())
  returning id into v_id;

  return v_id;
exception
  when unique_violation then
    raise exception using errcode = '23505',
      message = 'You already have an open request with this title';
end;
$$;

create or replace function public.toggle_request_vote(p_request_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_voted boolean;
  v_votes integer;
begin
  perform private.assert_active_user();
  perform private.enforce_rate_limit(
    'request_vote:' || auth.uid()::text, 120, interval '1 hour'
  );

  if not exists (select 1 from public.product_requests r where r.id = p_request_id) then
    raise exception using errcode = 'P0001', message = 'Request not found';
  end if;

  if exists (
    select 1 from public.request_votes v
    where v.request_id = p_request_id and v.user_id = auth.uid()
  ) then
    delete from public.request_votes v
    where v.request_id = p_request_id and v.user_id = auth.uid();
    v_voted := false;
  else
    insert into public.request_votes (request_id, user_id)
    values (p_request_id, auth.uid());
    v_voted := true;
    perform private.award_points(
      auth.uid(), 1, 'vote_cast', 'product_request', p_request_id,
      'request-vote:' || p_request_id::text || ':' || auth.uid()::text
    );
  end if;

  select coalesce(vc.votes, 0) into v_votes
  from public.request_vote_counts vc where vc.request_id = p_request_id;

  return jsonb_build_object('request_id', p_request_id, 'voted', v_voted, 'votes', v_votes);
end;
$$;

-- ---------------------------------------------------------------------------
-- Comments
-- ---------------------------------------------------------------------------

create table public.comments (
  id uuid primary key default gen_random_uuid(),
  target_type text not null check (target_type in ('product', 'rule', 'guide')),
  target_id uuid not null,
  parent_id uuid references public.comments(id) on delete set null,
  user_id uuid not null references public.profiles(id) on delete cascade,
  body text not null check (char_length(btrim(body)) between 1 and 4000),
  status public.comment_status not null default 'visible',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index comments_target_idx
  on public.comments (target_type, target_id, created_at desc);
create index comments_user_idx on public.comments (user_id, created_at desc);

create trigger comments_set_updated_at
before update on public.comments
for each row execute function private.set_updated_at();

create or replace function private.validate_comment_target()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.target_type = 'product' then
    if not exists (
      select 1 from public.products p
      where p.id = new.target_id and p.status = 'published'
    ) then
      raise exception using errcode = '23503', message = 'Published product not found';
    end if;
  elsif new.target_type = 'rule' then
    if not exists (
      select 1 from public.compatibility_rules r
      where r.id = new.target_id and r.status = 'published'
    ) then
      raise exception using errcode = '23503', message = 'Published rule not found';
    end if;
  elsif new.target_type = 'guide' then
    if not exists (
      select 1 from public.guides g
      where g.id = new.target_id and g.status = 'published'
    ) then
      raise exception using errcode = '23503', message = 'Published guide not found';
    end if;
  end if;
  return new;
end;
$$;

create trigger comments_validate_target
before insert on public.comments
for each row execute function private.validate_comment_target();

alter table public.comments enable row level security;

create policy comments_select_own_or_admin
on public.comments for select to authenticated
using (user_id = auth.uid() or private.is_admin());

create or replace function public.create_comment(
  p_target_type text,
  p_target_id uuid,
  p_body text,
  p_parent_id uuid default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_comment public.comments;
  v_parent public.comments;
begin
  perform private.assert_active_user();
  perform private.enforce_rate_limit(
    'comment_min:' || auth.uid()::text, 5, interval '1 minute'
  );
  perform private.enforce_rate_limit(
    'comment_day:' || auth.uid()::text, 50, interval '1 day'
  );

  if p_target_type not in ('product', 'rule', 'guide') then
    raise exception using errcode = '22023', message = 'Invalid comment target';
  end if;

  if p_parent_id is not null then
    select * into v_parent from public.comments c where c.id = p_parent_id;
    if v_parent.id is null
       or v_parent.target_type <> p_target_type
       or v_parent.target_id <> p_target_id then
      raise exception using errcode = '22023',
        message = 'Parent comment must belong to the same target';
    end if;
  end if;

  insert into public.comments (target_type, target_id, parent_id, user_id, body)
  values (p_target_type, p_target_id, p_parent_id, auth.uid(), btrim(p_body))
  returning * into v_comment;

  perform private.award_points(
    auth.uid(), 2, 'comment_created', 'comment', v_comment.id,
    'comment:' || v_comment.id::text
  );

  if v_parent.user_id is not null and v_parent.user_id <> auth.uid() then
    insert into public.notifications (user_id, type, title, body, link)
    values (
      v_parent.user_id, 'comment_reply', 'New reply to your comment',
      left(v_comment.body, 200), '/community/comment/' || v_comment.id::text
    );
  end if;

  return jsonb_build_object(
    'id', v_comment.id,
    'status', v_comment.status,
    'created_at', v_comment.created_at
  );
end;
$$;

create or replace function public.delete_my_comment(p_comment_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.assert_active_user();

  update public.comments c
  set status = 'deleted'
  where c.id = p_comment_id and c.user_id = auth.uid() and c.status = 'visible';

  if not found then
    raise exception using errcode = 'P0001', message = 'Comment not found';
  end if;
  return true;
end;
$$;

create view public.comments_public
with (security_invoker = false)
as
select
  c.id, c.target_type, c.target_id, c.parent_id, c.body,
  c.created_at, c.updated_at,
  pr.username, pr.display_name, pr.role, pr.avatar_url
from public.comments c
join public.profiles pr on pr.id = c.user_id
where c.status = 'visible'
  and pr.account_status = 'active';

create view public.comment_counts
with (security_invoker = false)
as
select target_type, target_id, count(*)::integer as comment_count
from public.comments
where status = 'visible'
group by target_type, target_id;

-- ---------------------------------------------------------------------------
-- Reports
-- ---------------------------------------------------------------------------

create table public.reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references public.profiles(id) on delete cascade,
  target_type text not null check (
    target_type in ('product', 'rule', 'comment', 'guide', 'user', 'request')
  ),
  target_id uuid not null,
  reason text not null check (
    reason in ('spam', 'harassment', 'false_claim', 'unsafe_advice', 'copyright', 'other')
  ),
  details text check (details is null or char_length(details) <= 2000),
  status public.report_status not null default 'open',
  handled_by uuid references public.profiles(id) on delete set null,
  resolution_note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index reports_status_idx on public.reports (status, created_at desc);
create unique index reports_one_open_per_user_target
  on public.reports (reporter_id, target_type, target_id)
  where status in ('open', 'investigating');

create trigger reports_set_updated_at
before update on public.reports
for each row execute function private.set_updated_at();

alter table public.reports enable row level security;

create policy reports_select_own_or_admin
on public.reports for select to authenticated
using (reporter_id = auth.uid() or private.is_admin());

create or replace function public.submit_report(
  p_target_type text,
  p_target_id uuid,
  p_reason text,
  p_details text default null
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_id uuid;
  v_exists boolean := false;
begin
  perform private.assert_active_user();
  perform private.enforce_rate_limit(
    'report:' || auth.uid()::text, 10, interval '1 day'
  );

  if p_target_type = 'product' then
    select exists (select 1 from public.products p
                   where p.id = p_target_id and p.status = 'published') into v_exists;
  elsif p_target_type = 'rule' then
    select exists (select 1 from public.compatibility_rules r
                   where r.id = p_target_id and r.status = 'published') into v_exists;
  elsif p_target_type = 'comment' then
    select exists (select 1 from public.comments c
                   where c.id = p_target_id and c.status = 'visible') into v_exists;
  elsif p_target_type = 'guide' then
    select exists (select 1 from public.guides g
                   where g.id = p_target_id and g.status = 'published') into v_exists;
  elsif p_target_type = 'user' then
    select exists (select 1 from public.profiles pr where pr.id = p_target_id) into v_exists;
  elsif p_target_type = 'request' then
    select exists (select 1 from public.product_requests r where r.id = p_target_id) into v_exists;
  end if;

  if not v_exists then
    raise exception using errcode = '23503', message = 'Report target not found';
  end if;

  insert into public.reports (reporter_id, target_type, target_id, reason, details)
  values (auth.uid(), p_target_type, p_target_id, p_reason, nullif(btrim(p_details), ''))
  returning id into v_id;

  return v_id;
exception
  when unique_violation then
    raise exception using errcode = '23505',
      message = 'You already reported this item';
end;
$$;

-- ---------------------------------------------------------------------------
-- Structured contributions
-- ---------------------------------------------------------------------------

create table public.contributions (
  id uuid primary key default gen_random_uuid(),
  contributor_id uuid not null references public.profiles(id) on delete cascade,
  kind public.contribution_kind not null,
  payload jsonb not null check (jsonb_typeof(payload) = 'object'),
  status public.contribution_status not null default 'pending',
  reviewer_id uuid references public.profiles(id) on delete set null,
  review_note text,
  applied_entity_id uuid,
  created_at timestamptz not null default now(),
  reviewed_at timestamptz,
  updated_at timestamptz not null default now()
);

create index contributions_status_idx on public.contributions (status, created_at);
create index contributions_contributor_idx
  on public.contributions (contributor_id, created_at desc);

create trigger contributions_set_updated_at
before update on public.contributions
for each row execute function private.set_updated_at();

alter table public.contributions enable row level security;

create policy contributions_select_own_or_admin
on public.contributions for select to authenticated
using (contributor_id = auth.uid() or private.is_admin());

create or replace function private.require_json_keys(p_payload jsonb, p_keys text[])
returns void
language plpgsql
immutable
set search_path = ''
as $$
declare
  v_key text;
begin
  foreach v_key in array p_keys loop
    if not p_payload ? v_key then
      raise exception using errcode = '22023',
        message = format('Missing contribution field: %s', v_key);
    end if;
  end loop;
end;
$$;

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
      if v_evidence ->> 'source_url' !~ '^https?://'
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

create or replace function public.submit_contribution(
  p_kind public.contribution_kind,
  p_payload jsonb
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
    'contribution:' || auth.uid()::text, 10, interval '1 day'
  );
  perform private.validate_contribution_payload(p_kind, p_payload);

  insert into public.contributions (contributor_id, kind, payload)
  values (auth.uid(), p_kind, p_payload)
  returning id into v_id;

  return v_id;
end;
$$;

create or replace function public.withdraw_contribution(p_contribution_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.assert_active_user();

  update public.contributions c
  set status = 'withdrawn', reviewed_at = now()
  where c.id = p_contribution_id
    and c.contributor_id = auth.uid()
    and c.status in ('pending', 'needs_changes');

  if not found then
    raise exception using errcode = 'P0001',
      message = 'Only your pending contribution can be withdrawn';
  end if;
  return true;
end;
$$;

create or replace function public.list_my_contributions()
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
  select coalesce(jsonb_agg(to_jsonb(c) order by c.created_at desc), '[]'::jsonb)
  into v_items
  from public.contributions c
  where c.contributor_id = auth.uid();
  return jsonb_build_object('items', v_items);
end;
$$;

-- ---------------------------------------------------------------------------
-- Public rule projections and evaluation
-- ---------------------------------------------------------------------------

create view public.rules_public
with (security_invoker = false)
as
select
  r.id, r.relationship, r.verification, r.confidence, r.risk,
  r.summary, r.technical_details, r.required_modifications,
  r.published_at, r.updated_at,
  r.subject_product_id, sp.name as subject_product_name, sp.slug as subject_product_slug,
  sb.name as subject_brand_name,
  r.object_product_id, op.name as object_product_name, op.slug as object_product_slug,
  ob.name as object_brand_name,
  (select count(*)::integer from public.rule_evidence e where e.rule_id = r.id)
    as evidence_count,
  coalesce(vc.confirms, 0) as confirm_count,
  coalesce(vc.disputes, 0) as dispute_count
from public.compatibility_rules r
join public.products sp on sp.id = r.subject_product_id
join public.brands sb on sb.id = sp.brand_id
join public.products op on op.id = r.object_product_id
join public.brands ob on ob.id = op.brand_id
left join public.rule_vote_counts vc on vc.rule_id = r.id
where r.status = 'published';

create view public.evidence_public
with (security_invoker = false)
as
select
  e.id, e.rule_id, e.evidence_type, e.title, e.source_url, e.publisher,
  e.excerpt, e.captured_at, e.created_at
from public.rule_evidence e
join public.compatibility_rules r on r.id = e.rule_id
where r.status = 'published';

create or replace function public.get_rule_detail(p_rule_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_rule jsonb;
  v_evidence jsonb;
  v_my_vote public.vote_type;
  v_is_favorite boolean := false;
  v_product_id uuid;
begin
  select to_jsonb(r) into v_rule
  from public.rules_public r where r.id = p_rule_id;

  if v_rule is null then
    return null;
  end if;

  select coalesce(jsonb_agg(to_jsonb(e) order by e.created_at), '[]'::jsonb)
  into v_evidence
  from public.evidence_public e
  where e.rule_id = p_rule_id;

  if auth.uid() is not null then
    select v.vote into v_my_vote
    from public.rule_votes v
    where v.rule_id = p_rule_id and v.user_id = auth.uid();
  end if;

  return v_rule || jsonb_build_object(
    'evidence', v_evidence,
    'my_vote', v_my_vote
  );
end;
$$;

create or replace function public.check_compatibility(
  p_product_a uuid,
  p_product_b uuid
)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_rules jsonb;
  v_rule_count integer;
  v_has_incompatibility boolean;
  v_primary jsonb;
  v_outcome text;
begin
  if p_product_a is null or p_product_b is null then
    raise exception using errcode = '22023', message = 'Both products are required';
  end if;

  if not exists (
    select 1 from public.products p
    where p.id = p_product_a and p.status = 'published'
  ) or not exists (
    select 1 from public.products p
    where p.id = p_product_b and p.status = 'published'
  ) then
    raise exception using errcode = '22023', message = 'Both products must be published';
  end if;

  if p_product_a = p_product_b then
    return jsonb_build_object(
      'known', false,
      'outcome', 'unknown',
      'same_product', true,
      'rule_count', 0,
      'rules', '[]'::jsonb,
      'primary_rule', null,
      'has_incompatibility', false,
      'checked_at', now()
    );
  end if;

  select
    coalesce(jsonb_agg(jsonb_build_object(
      'id', r.id,
      'relationship', r.relationship,
      'verification', r.verification,
      'confidence', r.confidence,
      'risk_level', r.risk,
      'summary', r.summary,
      'technical_details', r.technical_details,
      'required_modifications', r.required_modifications,
      'subject_product_id', r.subject_product_id,
      'subject_product_name', r.subject_product_name,
      'object_product_id', r.object_product_id,
      'object_product_name', r.object_product_name,
      'direction', case
        when r.subject_product_id = p_product_a then 'a_to_b'
        else 'b_to_a'
      end,
      'published_at', r.published_at,
      'evidence', coalesce((
        select jsonb_agg(jsonb_build_object(
          'id', e.id, 'title', e.title, 'source_url', e.source_url,
          'publisher', e.publisher, 'excerpt', e.excerpt,
          'evidence_type', e.evidence_type, 'captured_at', e.captured_at
        ) order by e.created_at)
        from public.rule_evidence e where e.rule_id = r.id
      ), '[]'::jsonb),
      'confirm_count', r.confirm_count,
      'dispute_count', r.dispute_count
    ) order by
      case r.relationship when 'not_compatible_with' then 0 else 1 end,
      case r.verification
        when 'official' then 4 when 'community_verified' then 3
        when 'needs_verification' then 2 else 1
      end desc,
      r.confidence desc,
      r.published_at desc
    ), '[]'::jsonb),
    count(*)::integer,
    bool_or(r.relationship = 'not_compatible_with')
  into v_rules, v_rule_count, v_has_incompatibility
  from public.rules_public r
  where (r.subject_product_id = p_product_a and r.object_product_id = p_product_b)
     or (r.subject_product_id = p_product_b and r.object_product_id = p_product_a);

  v_has_incompatibility := coalesce(v_has_incompatibility, false);
  v_primary := v_rules -> 0;

  if v_rule_count = 0 then
    v_outcome := 'unknown';
  elsif v_has_incompatibility then
    v_outcome := 'incompatible';
  else
    v_outcome := 'compatible';
  end if;

  return jsonb_build_object(
    'known', v_rule_count > 0,
    'outcome', v_outcome,
    'same_product', false,
    'rule_count', v_rule_count,
    'rules', v_rules,
    'primary_rule', v_primary,
    'has_incompatibility', v_has_incompatibility,
    'checked_at', now()
  );
end;
$$;

create or replace function public.product_graph(
  p_product_id uuid,
  p_depth integer default 1
)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_depth integer := least(greatest(coalesce(p_depth, 1), 1), 2);
  v_nodes jsonb;
  v_edges jsonb;
begin
  if not exists (
    select 1 from public.products p
    where p.id = p_product_id and p.status = 'published'
  ) then
    raise exception using errcode = '22023', message = 'Published product not found';
  end if;

  with recursive reachable(id, depth) as (
    select p_product_id, 0
    union
    select case
      when r.subject_product_id = reach.id then r.object_product_id
      else r.subject_product_id
    end,
    reach.depth + 1
    from reachable reach
    join public.compatibility_rules r
      on r.status = 'published'
     and (r.subject_product_id = reach.id or r.object_product_id = reach.id)
    where reach.depth < v_depth
  )
  select coalesce(jsonb_agg(distinct jsonb_build_object(
    'id', p.id, 'name', p.name, 'slug', p.slug, 'model_number', p.model_number,
    'brand_name', b.name, 'category_slug', c.slug
  )), '[]'::jsonb)
  into v_nodes
  from reachable reach
  join public.products p on p.id = reach.id and p.status = 'published'
  join public.brands b on b.id = p.brand_id
  join public.categories c on c.id = p.category_id;

  with recursive reachable(id, depth) as (
    select p_product_id, 0
    union
    select case
      when r.subject_product_id = reach.id then r.object_product_id
      else r.subject_product_id
    end,
    reach.depth + 1
    from reachable reach
    join public.compatibility_rules r
      on r.status = 'published'
     and (r.subject_product_id = reach.id or r.object_product_id = reach.id)
    where reach.depth < v_depth
  )
  select coalesce(jsonb_agg(distinct jsonb_build_object(
    'id', r.id, 'relationship', r.relationship, 'verification', r.verification,
    'confidence', r.confidence, 'summary', r.summary,
    'subject_product_id', r.subject_product_id,
    'object_product_id', r.object_product_id
  )), '[]'::jsonb)
  into v_edges
  from public.compatibility_rules r
  where r.status = 'published'
    and (r.subject_product_id in (select id from reachable)
      or r.object_product_id in (select id from reachable));

  return jsonb_build_object(
    'root', p_product_id,
    'depth', v_depth,
    'nodes', v_nodes,
    'edges', v_edges
  );
end;
$$;

comment on table public.rule_votes is
  'Private individual votes. Never exposed publicly; only aggregate counters are.';
comment on function public.check_compatibility(uuid, uuid) is
  'Returns only explicit published rules. Absence of rules yields outcome=unknown.';
comment on function public.submit_contribution(public.contribution_kind, jsonb) is
  'Validates a structured contribution and stores it pending review.';
