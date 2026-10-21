import {
  HttpError,
  corsHeaders,
  errorResponse,
  handleOptions,
  json,
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

type CsvRequest = ExportRequest | ImportRequest;

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

function parseBody(value: unknown): CsvRequest {
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
    return {
      action: 'import',
      rows: body.rows,
      publish: body.publish === true,
    };
  }
  throw new HttpError(400, 'action must be export or import');
}

async function exportProducts(client: Awaited<ReturnType<typeof requireAdmin>>['client']) {
  const [{ data: products, error }, { data: brands }, { data: categories }] = await Promise.all([
    client
      .from('products')
      .select(
        'slug,name,model_number,release_year,summary,technical_notes,connector_type,form_factor,status,brand_id,category_id',
      )
      .order('name'),
    client.from('brands').select('id,slug').order('name'),
    client.from('categories').select('id,slug').order('name'),
  ]);
  if (error) throw new HttpError(500, `Product export failed: ${error.message}`);

  const brandSlug = new Map((brands ?? []).map((b) => [b.id, b.slug]));
  const categorySlug = new Map((categories ?? []).map((c) => [c.id, c.slug]));
  const rows = (products ?? []).map((p) => ({
    ...p,
    brand_slug: brandSlug.get(p.brand_id) ?? '',
    category_slug: categorySlug.get(p.category_id) ?? '',
  }));
  return new Response(toCsv(PRODUCT_HEADERS, rows), {
    headers: {
      ...corsHeaders,
      'Content-Type': 'text/csv; charset=utf-8',
      'Content-Disposition': 'attachment; filename="products.csv"',
    },
  });
}

async function exportRules(client: Awaited<ReturnType<typeof requireAdmin>>['client']) {
  const [{ data: rules, error }, { data: products }, { data: evidence }] = await Promise.all([
    client
      .from('compatibility_rules')
      .select(
        'id,subject_product_id,object_product_id,relationship,verification,confidence,risk,summary,technical_details,required_modifications,status',
      )
      .order('updated_at', { ascending: false }),
    client.from('products').select('id,slug'),
    client.from('rule_evidence').select('rule_id'),
  ]);
  if (error) throw new HttpError(500, `Rule export failed: ${error.message}`);

  const productSlug = new Map((products ?? []).map((p) => [p.id, p.slug]));
  const counts = new Map<string, number>();
  for (const item of evidence ?? []) {
    counts.set(item.rule_id, (counts.get(item.rule_id) ?? 0) + 1);
  }
  const rows = (rules ?? []).map((r) => ({
    ...r,
    subject_product_slug: productSlug.get(r.subject_product_id) ?? '',
    object_product_slug: productSlug.get(r.object_product_id) ?? '',
    evidence_count: counts.get(r.id) ?? 0,
  }));
  return new Response(toCsv(RULE_HEADERS, rows), {
    headers: {
      ...corsHeaders,
      'Content-Type': 'text/csv; charset=utf-8',
      'Content-Disposition': 'attachment; filename="rules.csv"',
    },
  });
}

Deno.serve(async (request) => {
  const options = handleOptions(request);
  if (options) return options;

  try {
    requirePost(request);
    const context = await requireAdmin(request);
    const body = parseBody(await request.json());

    if (body.action === 'import') {
      const { data, error } = await context.client.rpc('admin_bulk_import_products', {
        p_rows: body.rows,
        p_publish: body.publish ?? false,
      });
      if (error) throw new HttpError(422, error.message);
      return json(data);
    }

    return body.resource === 'products'
      ? await exportProducts(context.client)
      : await exportRules(context.client);
  } catch (error) {
    return errorResponse(error);
  }
});
