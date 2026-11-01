import type { SupabaseClient } from 'npm:@supabase/supabase-js@2.57.4';
import {
  type AdminContext,
  HttpError,
  corsHeaders,
  errorResponse,
  handleOptions,
  json,
  rejectDisallowedOrigin,
  requireAdmin,
  requirePost,
  toCsv,
} from '../_shared/auth.ts';

interface ExportRequest {
  action: 'export';
  resource: 'products' | 'rules';
}

interface ImportRequest {
  action: 'import';
  rows: Record<string, unknown>[];
  publish?: boolean;
}

export type CsvRequest = ExportRequest | ImportRequest;

const PRODUCT_HEADERS = [
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
] as const;

const RULE_HEADERS = [
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
] as const;

export function parseBody(value: unknown): CsvRequest {
  if (!value || typeof value !== 'object') {
    throw new HttpError(400, 'A JSON request body is required');
  }
  const body = value as Partial<CsvRequest>;
  if (body.action === 'export') {
    if (body.resource !== 'products' && body.resource !== 'rules') {
      throw new HttpError(400, 'resource must be products or rules');
    }
    return { action: 'export', resource: body.resource };
  }
  if (body.action === 'import') {
    if (!Array.isArray(body.rows) || body.rows.length < 1 || body.rows.length > 500) {
      throw new HttpError(400, 'Import 1-500 rows per request');
    }
    if (body.rows.some((row) => !row || typeof row !== 'object' || Array.isArray(row))) {
      throw new HttpError(400, 'Every import row must be a JSON object');
    }
    return {
      action: 'import',
      rows: body.rows as Record<string, unknown>[],
      publish: body.publish === true,
    };
  }
  throw new HttpError(400, 'action must be export or import');
}

async function exportProducts(client: SupabaseClient, request: Request) {
  const [productsResult, brandsResult, categoriesResult] = await Promise.all([
    client
      .from('products')
      .select(
        'slug,name,model_number,release_year,summary,technical_notes,connector_type,form_factor,status,brand_id,category_id',
      )
      .order('name'),
    client.from('brands').select('id,slug').order('name'),
    client.from('categories').select('id,slug').order('name'),
  ]);
  if (productsResult.error || brandsResult.error || categoriesResult.error) {
    throw new HttpError(500, 'Product export failed');
  }
  const products = productsResult.data ?? [];
  const brands = brandsResult.data ?? [];
  const categories = categoriesResult.data ?? [];

  const brandSlug = new Map(brands.map((b) => [b.id, b.slug]));
  const categorySlug = new Map(categories.map((c) => [c.id, c.slug]));
  const rows = products.map((p) => ({
    ...p,
    brand_slug: brandSlug.get(p.brand_id) ?? '',
    category_slug: categorySlug.get(p.category_id) ?? '',
  }));
  return new Response(toCsv(PRODUCT_HEADERS, rows), {
    headers: {
      ...corsHeaders(request),
      'Content-Type': 'text/csv; charset=utf-8',
      'Content-Disposition': 'attachment; filename="products.csv"',
    },
  });
}

async function exportRules(client: SupabaseClient, request: Request) {
  const [rulesResult, productsResult, evidenceResult] = await Promise.all([
    client
      .from('compatibility_rules')
      .select(
        'id,subject_product_id,object_product_id,relationship,verification,confidence,risk,summary,technical_details,required_modifications,status',
      )
      .order('updated_at', { ascending: false }),
    client.from('products').select('id,slug'),
    client.from('rule_evidence').select('rule_id'),
  ]);
  if (rulesResult.error || productsResult.error || evidenceResult.error) {
    throw new HttpError(500, 'Rule export failed');
  }
  const rules = rulesResult.data ?? [];
  const products = productsResult.data ?? [];
  const evidence = evidenceResult.data ?? [];

  const productSlug = new Map(products.map((p) => [p.id, p.slug]));
  const counts = new Map<string, number>();
  for (const item of evidence) {
    counts.set(item.rule_id, (counts.get(item.rule_id) ?? 0) + 1);
  }
  const rows = rules.map((r) => ({
    ...r,
    subject_product_slug: productSlug.get(r.subject_product_id) ?? '',
    object_product_slug: productSlug.get(r.object_product_id) ?? '',
    evidence_count: counts.get(r.id) ?? 0,
  }));
  return new Response(toCsv(RULE_HEADERS, rows), {
    headers: {
      ...corsHeaders(request),
      'Content-Type': 'text/csv; charset=utf-8',
      'Content-Disposition': 'attachment; filename="rules.csv"',
    },
  });
}

export type AdminCsvDependencies = {
  authenticate: (request: Request) => Promise<AdminContext>;
};

export function createAdminCsvHandler(
  dependencies: Partial<AdminCsvDependencies> = {},
): (request: Request) => Promise<Response> {
  const authenticate = dependencies.authenticate ?? requireAdmin;

  return async function handleAdminCsv(request: Request): Promise<Response> {
    const options = handleOptions(request);
    if (options) return options;

    const originError = rejectDisallowedOrigin(request);
    if (originError) return originError;

    try {
      requirePost(request);
      const context = await authenticate(request);
      let payload: unknown;
      try {
        payload = await request.json();
      } catch {
        throw new HttpError(400, 'A JSON request body is required');
      }
      const body = parseBody(payload);

      if (body.action === 'import') {
        const { data, error } = await context.client.rpc('admin_bulk_import_products', {
          p_rows: body.rows,
          p_publish: body.publish ?? false,
        });
        if (error) throw new HttpError(422, error.message);
        return json(data, 200, request);
      }

      return body.resource === 'products'
        ? await exportProducts(context.client, request)
        : await exportRules(context.client, request);
    } catch (error) {
      return errorResponse(error, request);
    }
  };
}

export const handleAdminCsv = createAdminCsvHandler();
