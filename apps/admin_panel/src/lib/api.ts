import type { SupabaseClient } from '@supabase/supabase-js';
import { requireClient } from './supabase';
import type {
  Analytics,
  AuditRow,
  Brand,
  Category,
  CommentRow,
  Contribution,
  ContributionKind,
  ContributionStatus,
  Dashboard,
  Guide,
  GuideDetail,
  GuideStep,
  Me,
  MediaAsset,
  Paged,
  Product,
  ProductAlias,
  ProductDetail,
  ProductListItem,
  ProductMedia,
  ProductSpec,
  ProductStatus,
  Relationship,
  Report,
  ReportStatus,
  RequestRow,
  RequestStatus,
  Risk,
  Rule,
  RuleEvidence,
  RuleStatus,
  RuleVersion,
  RuleWithMeta,
  UserRow,
  Verification,
} from './types';
import { serializeCsv } from './csv';

const PAGE_SIZE_DEFAULT = 25;

function client(): SupabaseClient {
  return requireClient();
}

function fail(error: { message: string } | null): never {
  throw new Error(error?.message ?? 'The request failed');
}

function withCount<T>(
  data: T[] | null,
  count: number | null,
  limit: number,
  offset: number,
): Paged<T> {
  return { items: data ?? [], total: count ?? 0, limit, offset };
}

export function sanitizeSearchTerm(value: string): string {
  return value
    .replace(/[,%()*\\]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

export async function fetchMe(): Promise<Me | null> {
  const { data, error } = await client().rpc('me');
  if (error) fail(error);
  return (data as Me | null) ?? null;
}

export async function fetchDashboard(): Promise<Dashboard> {
  const { data, error } = await client().rpc('admin_dashboard');
  if (error) fail(error);
  return data as Dashboard;
}

// ---------------------------------------------------------------------------
// Catalog
// ---------------------------------------------------------------------------

export async function listBrands(): Promise<Brand[]> {
  const { data, error } = await client()
    .from('brands')
    .select('id,slug,name')
    .order('name');
  if (error) fail(error);
  return (data ?? []) as Brand[];
}

export async function listCategories(): Promise<Category[]> {
  const { data, error } = await client()
    .from('categories')
    .select('id,slug,name,is_public')
    .order('sort_order')
    .order('name');
  if (error) fail(error);
  return (data ?? []) as Category[];
}

interface RawProduct extends Product {
  brands: { name: string } | null;
  categories: { name: string } | null;
}

export async function listProducts(options: {
  page: number;
  pageSize?: number;
  query?: string;
  status?: ProductStatus | '';
}): Promise<Paged<ProductListItem>> {
  const limit = options.pageSize ?? PAGE_SIZE_DEFAULT;
  const offset = options.page * limit;
  let query = client()
    .from('products')
    .select('*, brands(name), categories(name)', { count: 'exact' })
    .order('updated_at', { ascending: false })
    .range(offset, offset + limit - 1);

  if (options.status) query = query.eq('status', options.status);
  const term = sanitizeSearchTerm(options.query ?? '');
  if (term) query = query.or(`name.ilike.%${term}%,slug.ilike.%${term}%`);

  const { data, error, count } = await query;
  if (error) fail(error);
  const items = ((data ?? []) as RawProduct[]).map(({ brands, categories, ...product }) => ({
    ...product,
    brand_name: brands?.name ?? '—',
    category_name: categories?.name ?? '—',
  }));
  return withCount(items, count, limit, offset);
}

export async function getProductDetail(id: string): Promise<ProductDetail> {
  const db = client();
  const [productResult, aliasesResult, specsResult] = await Promise.all([
    db.from('products').select('*').eq('id', id).single(),
    db.from('product_aliases').select('*').eq('product_id', id).order('alias'),
    db.from('product_specs').select('*').eq('product_id', id).order('sort_order'),
  ]);
  if (productResult.error) fail(productResult.error);
  if (aliasesResult.error) fail(aliasesResult.error);
  if (specsResult.error) fail(specsResult.error);
  return {
    ...(productResult.data as Product),
    aliases: (aliasesResult.data ?? []) as ProductAlias[],
    specs: (specsResult.data ?? []) as ProductSpec[],
  };
}

export interface ProductInput {
  name: string;
  slug: string;
  brand_id: string;
  category_id: string;
  model_number: string | null;
  release_year: number | null;
  summary: string | null;
  technical_notes: string | null;
  connector_type: string | null;
  form_factor: string | null;
  status: ProductStatus;
}

export async function saveProduct(
  input: ProductInput,
  relations: {
    aliases: string[];
    specs: Array<{
      label: string;
      value: string;
      unit: string | null;
      is_critical: boolean;
      sort_order: number;
    }>;
  },
  actorId: string,
  id?: string,
): Promise<string> {
  const db = client();
  let productId = id;

  if (id) {
    const { error } = await db.from('products').update(input).eq('id', id);
    if (error) fail(error);
  } else {
    const { data, error } = await db
      .from('products')
      .insert({ ...input, created_by: actorId })
      .select('id')
      .single();
    if (error) fail(error);
    productId = (data as { id: string }).id;
  }

  if (!productId) throw new Error('Product id was not returned');

  const aliases = [...new Set(relations.aliases.map((alias) => alias.trim()).filter(Boolean))];
  const specs = relations.specs.filter((spec) => spec.label.trim() && spec.value.trim());

  const { error: aliasDeleteError } = await db
    .from('product_aliases')
    .delete()
    .eq('product_id', productId);
  if (aliasDeleteError) fail(aliasDeleteError);
  if (aliases.length > 0) {
    const { error } = await db
      .from('product_aliases')
      .insert(aliases.map((alias) => ({ product_id: productId, alias })));
    if (error) fail(error);
  }

  const { error: specDeleteError } = await db
    .from('product_specs')
    .delete()
    .eq('product_id', productId);
  if (specDeleteError) fail(specDeleteError);
  if (specs.length > 0) {
    const { error } = await db.from('product_specs').insert(
      specs.map((spec, index) => ({
        product_id: productId,
        label: spec.label.trim(),
        value: spec.value.trim(),
        unit: spec.unit?.trim() || null,
        is_critical: spec.is_critical,
        sort_order: index,
      })),
    );
    if (error) fail(error);
  }

  return productId;
}

export async function deleteProduct(id: string): Promise<void> {
  const { error } = await client().from('products').delete().eq('id', id);
  if (error) fail(error);
}

export async function listProductOptions(): Promise<Array<{ id: string; name: string; slug: string; status: string }>> {
  const { data, error } = await client()
    .from('products')
    .select('id,name,slug,status')
    .order('name')
    .limit(500);
  if (error) fail(error);
  return data ?? [];
}

// ---------------------------------------------------------------------------
// Compatibility rules and evidence
// ---------------------------------------------------------------------------

export async function listRules(options: {
  page: number;
  pageSize?: number;
  query?: string;
  status?: RuleStatus | '';
  relationship?: Relationship | '';
}): Promise<Paged<RuleWithMeta>> {
  const limit = options.pageSize ?? PAGE_SIZE_DEFAULT;
  const offset = options.page * limit;
  let query = client()
    .from('compatibility_rules')
    .select('*', { count: 'exact' })
    .order('updated_at', { ascending: false })
    .range(offset, offset + limit - 1);
  if (options.status) query = query.eq('status', options.status);
  if (options.relationship) query = query.eq('relationship', options.relationship);
  const term = sanitizeSearchTerm(options.query ?? '');
  if (term) query = query.ilike('summary', `%${term}%`);

  const { data, error, count } = await query;
  if (error) fail(error);
  const rules = (data ?? []) as Rule[];

  const [products, evidence] = await Promise.all([
    client().from('products').select('id,name'),
    client()
      .from('rule_evidence')
      .select('rule_id')
      .in(
        'rule_id',
        rules.map((rule) => rule.id),
      ),
  ]);
  if (products.error) fail(products.error);
  if (evidence.error) fail(evidence.error);

  const productNames = new Map(
    ((products.data ?? []) as Array<{ id: string; name: string }>).map((p) => [p.id, p.name]),
  );
  const evidenceCounts = new Map<string, number>();
  for (const item of (evidence.data ?? []) as Array<{ rule_id: string }>) {
    evidenceCounts.set(item.rule_id, (evidenceCounts.get(item.rule_id) ?? 0) + 1);
  }
  const items = rules.map((rule) => ({
    ...rule,
    subject_name: productNames.get(rule.subject_product_id) ?? 'Unknown product',
    object_name: productNames.get(rule.object_product_id) ?? 'Unknown product',
    evidence_count: evidenceCounts.get(rule.id) ?? 0,
  }));
  return withCount(items, count, limit, offset);
}

export async function getRuleEvidence(ruleId: string): Promise<RuleEvidence[]> {
  const { data, error } = await client()
    .from('rule_evidence')
    .select('*')
    .eq('rule_id', ruleId)
    .order('created_at');
  if (error) fail(error);
  return (data ?? []) as RuleEvidence[];
}

export interface RuleInput {
  subject_product_id: string;
  object_product_id: string;
  relationship: Relationship;
  verification: Verification;
  confidence: number;
  risk: Risk;
  summary: string;
  technical_details: string | null;
  required_modifications: string | null;
  status: RuleStatus;
}

export interface EvidenceInput {
  id?: string;
  evidence_type: RuleEvidence['evidence_type'];
  title: string;
  source_url: string;
  publisher: string | null;
  excerpt: string | null;
  captured_at: string | null;
}

function canonicalRule(input: RuleInput, actorId: string) {
  const symmetric =
    input.relationship === 'works_with' ||
    input.relationship === 'same_as' ||
    input.relationship === 'not_compatible_with';
  if (symmetric && input.subject_product_id > input.object_product_id) {
    return {
      ...input,
      subject_product_id: input.object_product_id,
      object_product_id: input.subject_product_id,
      reviewed_by: actorId,
    };
  }
  return { ...input, reviewed_by: actorId };
}

export async function saveRule(
  input: RuleInput,
  evidence: EvidenceInput[],
  actorId: string,
  id?: string,
): Promise<string> {
  if (input.subject_product_id === input.object_product_id) {
    throw new Error('A product cannot be compared with itself');
  }
  if (evidence.length < 1 && input.status === 'published') {
    throw new Error('At least one evidence item is required before publishing');
  }
  for (const item of evidence) {
    if (!/^https?:\/\//i.test(item.source_url)) {
      throw new Error('Evidence source URLs must begin with http:// or https://');
    }
  }

  const db = client();
  let ruleId = id;
  const payload = canonicalRule(input, actorId);

  if (id) {
    const { error } = await db.from('compatibility_rules').update(payload).eq('id', id);
    if (error) fail(error);
  } else {
    const { data, error } = await db
      .from('compatibility_rules')
      .insert({ ...payload, status: 'draft', created_by: actorId, published_at: null })
      .select('id')
      .single();
    if (error) fail(error);
    ruleId = (data as { id: string }).id;
  }
  if (!ruleId) throw new Error('Rule id was not returned');

  const existing = id ? await getRuleEvidence(id) : [];
  const keptIds = new Set(evidence.flatMap((item) => (item.id ? [item.id] : [])));

  // Insert new evidence first so a published rule never temporarily loses its
  // last citation, then update existing rows, then remove omitted evidence.
  for (const item of evidence) {
    const row = {
      evidence_type: item.evidence_type,
      title: item.title,
      source_url: item.source_url,
      publisher: item.publisher || null,
      excerpt: item.excerpt || null,
      captured_at: item.captured_at || null,
    };
    if (item.id) {
      const { error } = await db.from('rule_evidence').update(row).eq('id', item.id);
      if (error) fail(error);
    } else {
      const { error } = await db
        .from('rule_evidence')
        .insert({ ...row, rule_id: ruleId, added_by: actorId });
      if (error) fail(error);
    }
  }
  const removed = existing.filter((item) => !keptIds.has(item.id));
  for (const item of removed) {
    const { error } = await db.from('rule_evidence').delete().eq('id', item.id);
    if (error) fail(error);
  }

  if (!id || payload.status !== 'published') {
    const { error } = await db
      .from('compatibility_rules')
      .update({
        status: payload.status,
        published_at: payload.status === 'published' ? new Date().toISOString() : null,
      })
      .eq('id', ruleId);
    if (error) fail(error);
  }

  return ruleId;
}

export async function listRuleVersions(ruleId: string): Promise<RuleVersion[]> {
  const { data, error } = await client().rpc('admin_rule_versions', { p_rule_id: ruleId });
  if (error) fail(error);
  return ((data as { items?: RuleVersion[] } | null)?.items ?? []) as RuleVersion[];
}

export async function rollbackRule(
  ruleId: string,
  version: number,
  reason: string,
): Promise<void> {
  const { error } = await client().rpc('admin_rollback_rule', {
    p_rule_id: ruleId,
    p_version: version,
    p_reason: reason,
  });
  if (error) fail(error);
}

// ---------------------------------------------------------------------------
// Moderation queues
// ---------------------------------------------------------------------------

export async function listReports(options: {
  page: number;
  pageSize?: number;
  status?: ReportStatus | '';
}): Promise<Paged<Report>> {
  const limit = options.pageSize ?? PAGE_SIZE_DEFAULT;
  const offset = options.page * limit;
  const { data, error } = await client().rpc('admin_list_reports', {
    p_status: options.status || null,
    p_limit: limit,
    p_offset: offset,
  });
  if (error) fail(error);
  const result = data as Paged<Report>;
  return { ...result, items: result.items ?? [], limit, offset };
}

export async function resolveReport(input: {
  id: string;
  status: Exclude<ReportStatus, 'open'>;
  note: string;
  action: 'none' | 'hide_comment' | 'retire_rule' | 'archive_product';
}): Promise<void> {
  const { error } = await client().rpc('admin_resolve_report', {
    p_report_id: input.id,
    p_status: input.status,
    p_note: input.note || null,
    p_action: input.action,
  });
  if (error) fail(error);
}

export async function listContributions(options: {
  page: number;
  pageSize?: number;
  status?: ContributionStatus | '';
  kind?: ContributionKind | '';
}): Promise<Paged<Contribution>> {
  const limit = options.pageSize ?? PAGE_SIZE_DEFAULT;
  const offset = options.page * limit;
  const { data, error } = await client().rpc('admin_list_contributions', {
    p_status: options.status || null,
    p_kind: options.kind || null,
    p_limit: limit,
    p_offset: offset,
  });
  if (error) fail(error);
  const result = data as Paged<Contribution>;
  return { ...result, items: result.items ?? [], limit, offset };
}

export async function reviewContribution(input: {
  id: string;
  decision: 'approved' | 'rejected' | 'needs_changes';
  note: string;
}): Promise<void> {
  const { error } = await client().rpc('admin_review_contribution', {
    p_contribution_id: input.id,
    p_decision: input.decision,
    p_note: input.note || null,
  });
  if (error) fail(error);
}

export async function listUsers(options: {
  page: number;
  pageSize?: number;
  search?: string;
  role?: string;
  status?: string;
}): Promise<Paged<UserRow>> {
  const limit = options.pageSize ?? PAGE_SIZE_DEFAULT;
  const offset = options.page * limit;
  const { data, error } = await client().rpc('admin_list_users', {
    p_search: options.search || null,
    p_role: options.role || null,
    p_status: options.status || null,
    p_limit: limit,
    p_offset: offset,
  });
  if (error) fail(error);
  const result = data as Paged<UserRow>;
  return { ...result, items: result.items ?? [], limit, offset };
}

export async function setUserRole(input: {
  userId: string;
  role: string;
  reason: string;
}): Promise<void> {
  const { error } = await client().rpc('admin_set_user_role', {
    p_user_id: input.userId,
    p_role: input.role,
    p_reason: input.reason,
  });
  if (error) fail(error);
}

export async function setUserStatus(input: {
  userId: string;
  status: string;
  reason: string;
}): Promise<void> {
  const { error } = await client().rpc('admin_set_user_status', {
    p_user_id: input.userId,
    p_status: input.status,
    p_reason: input.reason,
  });
  if (error) fail(error);
}

export async function listRequests(options: {
  page: number;
  pageSize?: number;
  status?: RequestStatus | '';
  query?: string;
}): Promise<Paged<RequestRow>> {
  const limit = options.pageSize ?? PAGE_SIZE_DEFAULT;
  const offset = options.page * limit;
  let query = client()
    .from('product_requests')
    .select('*', { count: 'exact' })
    .order('created_at', { ascending: false })
    .range(offset, offset + limit - 1);
  if (options.status) query = query.eq('status', options.status);
  const term = sanitizeSearchTerm(options.query ?? '');
  if (term) query = query.ilike('title', `%${term}%`);
  const { data, error, count } = await query;
  if (error) fail(error);
  const requests = (data ?? []) as Array<Omit<RequestRow, 'vote_count' | 'category_name'>>;
  const [categories, votes] = await Promise.all([
    client().from('categories').select('id,name'),
    client()
      .from('request_vote_counts')
      .select('request_id,votes')
      .in(
        'request_id',
        requests.map((request) => request.id),
      ),
  ]);
  if (categories.error) fail(categories.error);
  if (votes.error) fail(votes.error);
  const categoryNames = new Map(
    ((categories.data ?? []) as Array<{ id: string; name: string }>).map((c) => [c.id, c.name]),
  );
  const voteCounts = new Map(
    ((votes.data ?? []) as Array<{ request_id: string; votes: number }>).map((v) => [
      v.request_id,
      v.votes,
    ]),
  );
  const items = requests.map((request) => ({
    ...request,
    category_name: request.category_id ? (categoryNames.get(request.category_id) ?? null) : null,
    vote_count: voteCounts.get(request.id) ?? 0,
  }));
  return withCount(items, count, limit, offset);
}

export async function updateRequest(input: {
  id: string;
  status: RequestStatus;
  fulfilledProductId: string | null;
}): Promise<void> {
  const { error } = await client().rpc('admin_update_request', {
    p_request_id: input.id,
    p_status: input.status,
    p_fulfilled_product_id: input.fulfilledProductId,
  });
  if (error) fail(error);
}

export async function listComments(options: {
  page: number;
  pageSize?: number;
  status?: string;
  query?: string;
}): Promise<Paged<CommentRow>> {
  const limit = options.pageSize ?? PAGE_SIZE_DEFAULT;
  const offset = options.page * limit;
  let query = client()
    .from('comments')
    .select('*', { count: 'exact' })
    .order('created_at', { ascending: false })
    .range(offset, offset + limit - 1);
  if (options.status) query = query.eq('status', options.status);
  const term = sanitizeSearchTerm(options.query ?? '');
  if (term) query = query.ilike('body', `%${term}%`);
  const { data, error, count } = await query;
  if (error) fail(error);
  const comments = (data ?? []) as Array<Omit<CommentRow, 'author_name'>>;
  const userIds = [...new Set(comments.map((comment) => comment.user_id))];
  const profiles = userIds.length
    ? await client().from('profiles').select('id,display_name').in('id', userIds)
    : { data: [], error: null };
  if (profiles.error) fail(profiles.error);
  const names = new Map(
    ((profiles.data ?? []) as Array<{ id: string; display_name: string }>).map((p) => [
      p.id,
      p.display_name,
    ]),
  );
  const items = comments.map((comment) => ({
    ...comment,
    author_name: names.get(comment.user_id) ?? null,
  }));
  return withCount(items, count, limit, offset);
}

export async function moderateComment(input: {
  id: string;
  status: 'visible' | 'hidden' | 'deleted';
  reason: string;
}): Promise<void> {
  const { error } = await client().rpc('admin_moderate_comment', {
    p_comment_id: input.id,
    p_status: input.status,
    p_reason: input.reason,
  });
  if (error) fail(error);
}

// ---------------------------------------------------------------------------
// Guides, media, analytics, audit
// ---------------------------------------------------------------------------

export async function listGuides(options: {
  page: number;
  pageSize?: number;
  status?: string;
  query?: string;
}): Promise<Paged<Guide>> {
  const limit = options.pageSize ?? PAGE_SIZE_DEFAULT;
  const offset = options.page * limit;
  let query = client()
    .from('guides')
    .select('*', { count: 'exact' })
    .order('updated_at', { ascending: false })
    .range(offset, offset + limit - 1);
  if (options.status) query = query.eq('status', options.status);
  const term = sanitizeSearchTerm(options.query ?? '');
  if (term) query = query.ilike('title', `%${term}%`);
  const { data, error, count } = await query;
  if (error) fail(error);
  return withCount((data ?? []) as Guide[], count, limit, offset);
}

export async function getGuide(id: string): Promise<GuideDetail> {
  const db = client();
  const [guideResult, stepsResult] = await Promise.all([
    db.from('guides').select('*').eq('id', id).single(),
    db.from('guide_steps').select('*').eq('guide_id', id).order('position'),
  ]);
  if (guideResult.error) fail(guideResult.error);
  if (stepsResult.error) fail(stepsResult.error);
  const guide = guideResult.data as Guide;
  let productName: string | null = null;
  if (guide.product_id) {
    const { data } = await db.from('products').select('name').eq('id', guide.product_id).single();
    productName = (data as { name: string } | null)?.name ?? null;
  }
  return {
    ...guide,
    steps: (stepsResult.data ?? []) as GuideStep[],
    product_name: productName,
  };
}

export interface GuideInput {
  slug: string;
  title: string;
  summary: string;
  body_md: string;
  difficulty: string;
  status: Guide['status'];
  product_id: string | null;
}

export async function saveGuide(
  input: GuideInput,
  steps: Array<{ title: string; body: string; image_path: string | null }>,
  actorId: string,
  id?: string,
): Promise<string> {
  const db = client();
  let guideId = id;
  const payload = {
    ...input,
    status: input.status,
    published_at:
      input.status === 'published' ? new Date().toISOString() : null,
  };
  if (id) {
    const { error } = await db.from('guides').update(payload).eq('id', id);
    if (error) fail(error);
  } else {
    const { data, error } = await db
      .from('guides')
      .insert({ ...payload, created_by: actorId })
      .select('id')
      .single();
    if (error) fail(error);
    guideId = (data as { id: string }).id;
  }
  if (!guideId) throw new Error('Guide id was not returned');

  const { error: deleteError } = await db.from('guide_steps').delete().eq('guide_id', guideId);
  if (deleteError) fail(deleteError);
  if (steps.length > 0) {
    const { error } = await db.from('guide_steps').insert(
      steps.map((step, index) => ({
        guide_id: guideId,
        position: index + 1,
        title: step.title,
        body: step.body,
        image_path: step.image_path,
      })),
    );
    if (error) fail(error);
  }
  return guideId;
}

export async function listProductMedia(): Promise<ProductMedia[]> {
  const db = client();
  const { data, error } = await db
    .from('product_media')
    .select('*')
    .order('sort_order')
    .limit(200);
  if (error) fail(error);
  const media = (data ?? []) as ProductMedia[];
  const productIds = [...new Set(media.map((item) => item.product_id))];
  const products = productIds.length
    ? await db.from('products').select('id,name').in('id', productIds)
    : { data: [], error: null };
  if (products.error) fail(products.error);
  const names = new Map(
    ((products.data ?? []) as Array<{ id: string; name: string }>).map((p) => [p.id, p.name]),
  );
  return media.map((item) => ({ ...item, product_name: names.get(item.product_id) ?? null }));
}

export async function listMediaAssets(): Promise<MediaAsset[]> {
  const { data, error } = await client()
    .from('media_assets')
    .select('*')
    .order('created_at', { ascending: false })
    .limit(200);
  if (error) fail(error);
  return (data ?? []) as MediaAsset[];
}

function cleanFilename(name: string): string {
  return name
    .toLowerCase()
    .replace(/[^a-z0-9._-]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 80);
}

export async function uploadProductMedia(input: {
  productId: string;
  file: File;
  altText: string;
  actorId: string;
}): Promise<void> {
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(input.file.type)) {
    throw new Error('Only JPEG, PNG, or WebP images are accepted');
  }
  if (input.file.size > 10 * 1024 * 1024) {
    throw new Error('Images must be 10 MB or smaller');
  }
  if (!input.altText.trim()) {
    throw new Error('Alternative text is required for accessibility');
  }
  const db = client();
  const path = `products/${input.productId}/${crypto.randomUUID()}-${cleanFilename(input.file.name)}`;
  const upload = await db.storage.from('media').upload(path, input.file, {
    cacheControl: '3600',
    contentType: input.file.type,
    upsert: false,
  });
  if (upload.error) fail(upload.error);

  const { error: mediaError } = await db.from('product_media').insert({
    product_id: input.productId,
    storage_path: path,
    alt_text: input.altText.trim(),
    status: 'approved',
    created_by: input.actorId,
  });
  if (mediaError) {
    await db.storage.from('media').remove([path]);
    fail(mediaError);
  }
  const { error: assetError } = await db.from('media_assets').insert({
    bucket: 'media',
    storage_path: path,
    owner_id: input.actorId,
    alt_text: input.altText.trim(),
    status: 'approved',
    target_type: 'product',
    target_id: input.productId,
  });
  if (assetError) {
    await db.from('product_media').delete().eq('storage_path', path);
    await db.storage.from('media').remove([path]);
    fail(assetError);
  }
}

export async function setMediaStatus(
  id: string,
  status: 'pending' | 'approved' | 'rejected',
): Promise<void> {
  const { error } = await client().from('product_media').update({ status }).eq('id', id);
  if (error) fail(error);
  const { data } = await client()
    .from('product_media')
    .select('storage_path')
    .eq('id', id)
    .maybeSingle();
  if (data?.storage_path) {
    await client()
      .from('media_assets')
      .update({ status })
      .eq('storage_path', data.storage_path);
  }
}

export async function deleteMedia(item: ProductMedia): Promise<void> {
  const db = client();
  const remove = await db.storage.from('media').remove([item.storage_path]);
  if (remove.error) fail(remove.error);
  const { error } = await db.from('product_media').delete().eq('id', item.id);
  if (error) fail(error);
  await db.from('media_assets').delete().eq('storage_path', item.storage_path);
}

export function publicMediaUrl(path: string): string {
  return client().storage.from('media').getPublicUrl(path).data.publicUrl;
}

export async function fetchAnalytics(days: number): Promise<Analytics> {
  const { data, error } = await client().rpc('admin_analytics', { p_days: days });
  if (error) fail(error);
  return data as Analytics;
}

export async function listAudit(options: {
  page: number;
  pageSize?: number;
  action?: string;
  entityType?: string;
}): Promise<Paged<AuditRow>> {
  const limit = options.pageSize ?? PAGE_SIZE_DEFAULT;
  const offset = options.page * limit;
  let query = client()
    .from('audit_logs')
    .select('*', { count: 'exact' })
    .order('created_at', { ascending: false })
    .range(offset, offset + limit - 1);
  if (options.action) query = query.ilike('action', `%${sanitizeSearchTerm(options.action)}%`);
  if (options.entityType) query = query.eq('entity_type', options.entityType);
  const { data, error, count } = await query;
  if (error) fail(error);
  const rows = (data ?? []) as Array<Omit<AuditRow, 'actor_name'>>;
  const actorIds = [...new Set(rows.flatMap((row) => (row.actor_id ? [row.actor_id] : [])))];
  const actors = actorIds.length
    ? await client().from('profiles').select('id,display_name').in('id', actorIds)
    : { data: [], error: null };
  if (actors.error) fail(actors.error);
  const names = new Map(
    ((actors.data ?? []) as Array<{ id: string; display_name: string }>).map((p) => [
      p.id,
      p.display_name,
    ]),
  );
  return withCount(
    rows.map((row) => ({
      ...row,
      actor_name: row.actor_id ? (names.get(row.actor_id) ?? null) : null,
    })),
    count,
    limit,
    offset,
  );
}

export async function exportProductsCsv(): Promise<string> {
  const db = client();
  const [productsResult, brandsResult, categoriesResult] = await Promise.all([
    db
      .from('products')
      .select(
        'slug,name,model_number,release_year,summary,technical_notes,connector_type,form_factor,status,brand_id,category_id',
      )
      .order('name'),
    db.from('brands').select('id,slug'),
    db.from('categories').select('id,slug'),
  ]);
  if (productsResult.error) fail(productsResult.error);
  if (brandsResult.error) fail(brandsResult.error);
  if (categoriesResult.error) fail(categoriesResult.error);
  const brands = new Map(
    ((brandsResult.data ?? []) as Array<{ id: string; slug: string }>).map((b) => [b.id, b.slug]),
  );
  const categories = new Map(
    ((categoriesResult.data ?? []) as Array<{ id: string; slug: string }>).map((c) => [
      c.id,
      c.slug,
    ]),
  );
  const rows = ((productsResult.data ?? []) as Array<Record<string, unknown>>).map((row) => ({
    ...row,
    brand_slug: brands.get(String(row.brand_id)) ?? '',
    category_slug: categories.get(String(row.category_id)) ?? '',
  }));
  return serializeCsv(
    [
      'slug',
      'name',
      'brand_slug',
      'category_slug',
      'model_number',
      'release_year',
      'summary',
      'technical_notes',
      'connector_type',
      'form_factor',
      'status',
    ],
    rows,
  );
}

export async function importProducts(
  rows: Array<Record<string, unknown>>,
  publish: boolean,
): Promise<{ imported: number; ids: string[]; published: boolean }> {
  const { data, error } = await client().rpc('admin_bulk_import_products', {
    p_rows: rows,
    p_publish: publish,
  });
  if (error) fail(error);
  return data as { imported: number; ids: string[]; published: boolean };
}

export async function exportRulesCsv(): Promise<string> {
  const db = client();
  const [rulesResult, productsResult, evidenceResult] = await Promise.all([
    db
      .from('compatibility_rules')
      .select(
        'id,subject_product_id,object_product_id,relationship,verification,confidence,risk,summary,technical_details,required_modifications,status',
      )
      .order('updated_at', { ascending: false }),
    db.from('products').select('id,slug'),
    db.from('rule_evidence').select('rule_id'),
  ]);
  if (rulesResult.error) fail(rulesResult.error);
  if (productsResult.error) fail(productsResult.error);
  if (evidenceResult.error) fail(evidenceResult.error);
  const slugs = new Map(
    ((productsResult.data ?? []) as Array<{ id: string; slug: string }>).map((p) => [p.id, p.slug]),
  );
  const counts = new Map<string, number>();
  for (const item of (evidenceResult.data ?? []) as Array<{ rule_id: string }>) {
    counts.set(item.rule_id, (counts.get(item.rule_id) ?? 0) + 1);
  }
  const rows = ((rulesResult.data ?? []) as Array<Record<string, unknown>>).map((rule) => ({
    ...rule,
    subject_product_slug: slugs.get(String(rule.subject_product_id)) ?? '',
    object_product_slug: slugs.get(String(rule.object_product_id)) ?? '',
    evidence_count: counts.get(String(rule.id)) ?? 0,
  }));
  return serializeCsv(
    [
      'subject_product_slug',
      'object_product_slug',
      'relationship',
      'verification',
      'confidence',
      'risk',
      'summary',
      'technical_details',
      'required_modifications',
      'status',
      'evidence_count',
    ],
    rows,
  );
}

export function defaultPageSize(): number {
  return PAGE_SIZE_DEFAULT;
}
