import { assert, assertEquals } from 'jsr:@std/assert@1';
import type { AdminContext } from '../_shared/auth.ts';
import { HttpError } from '../_shared/auth.ts';
import {
  createAdminCsvHandler,
  handleAdminCsv,
  parseBody,
} from '../admin-csv/handler.ts';

type RpcResult = { data: unknown; error: { message: string } | null };

function fakeClient(
  tables: Record<string, Array<Record<string, unknown>>> = {},
  rpcResult: RpcResult = { data: null, error: null },
  tableErrors: Record<string, { message: string }> = {},
) {
  const rpcCalls: Array<{ name: string; args: Record<string, unknown> }> = [];
  const client = {
    from(table: string) {
      const result = Promise.resolve({
        data: tables[table] ?? [],
        error: tableErrors[table] ?? null,
      });
      return {
        select: () => Object.assign(result, { order: () => result }),
      };
    },
    rpc: async (name: string, args: Record<string, unknown>) => {
      rpcCalls.push({ name, args });
      return rpcResult;
    },
  };
  return {
    client: client as unknown as AdminContext['client'],
    rpcCalls,
  };
}

function post(body: unknown, headers: Record<string, string> = {}): Request {
  return new Request('https://functions.example.test/admin-csv', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body),
  });
}

function withAdminEnv(adminOrigin?: string): () => void {
  const previousOrigin = Deno.env.get('ADMIN_ORIGIN');
  const previousUrl = Deno.env.get('SUPABASE_URL');
  const previousAnon = Deno.env.get('SUPABASE_ANON_KEY');
  if (adminOrigin === undefined) Deno.env.delete('ADMIN_ORIGIN');
  else Deno.env.set('ADMIN_ORIGIN', adminOrigin);
  Deno.env.set('SUPABASE_URL', 'http://127.0.0.1:54321');
  Deno.env.set('SUPABASE_ANON_KEY', 'test-anon-key');
  return () => {
    for (const [key, value] of [
      ['ADMIN_ORIGIN', previousOrigin],
      ['SUPABASE_URL', previousUrl],
      ['SUPABASE_ANON_KEY', previousAnon],
    ] as const) {
      if (value === undefined) Deno.env.delete(key);
      else Deno.env.set(key, value);
    }
  };
}

Deno.test('parseBody accepts export and bounded import payloads', () => {
  assertEquals(parseBody({ action: 'export', resource: 'rules' }), {
    action: 'export',
    resource: 'rules',
  });
  assertEquals(parseBody({ action: 'import', rows: [{ slug: 'p' }], publish: true }), {
    action: 'import',
    rows: [{ slug: 'p' }],
    publish: true,
  });
  assertEquals(parseBody({ action: 'import', rows: [{ slug: 'p' }], publish: 'yes' }), {
    action: 'import',
    rows: [{ slug: 'p' }],
    publish: false,
  });
});

Deno.test('parseBody rejects invalid resources, row counts, and row shapes', () => {
  for (const body of [
    null,
    { action: 'unknown' },
    { action: 'export', resource: 'users' },
    { action: 'import', rows: [] },
    { action: 'import', rows: Array.from({ length: 501 }, () => ({ slug: 'p' })) },
    { action: 'import', rows: ['not-an-object'] },
    { action: 'import', rows: [{ slug: 'p' }, null] },
  ]) {
    assertThrowsHttp(body);
  }
});

function assertThrowsHttp(value: unknown): void {
  try {
    parseBody(value);
    throw new Error('Expected HttpError');
  } catch (error) {
    assert(error instanceof HttpError);
    assertEquals(error.status, 400);
  }
}

Deno.test('admin-csv returns 401 without a bearer token', async () => {
  const restore = withAdminEnv();
  try {
    const response = await handleAdminCsv(post({ action: 'export', resource: 'products' }));
    assertEquals(response.status, 401);
    assertEquals(await response.json(), { error: 'A user access token is required' });
  } finally {
    restore();
  }
});

Deno.test('admin-csv rejects a disallowed browser origin before authentication', async () => {
  const restore = withAdminEnv('https://admin.example.com');
  try {
    let authenticated = false;
    const handler = createAdminCsvHandler({
      authenticate: async () => {
        authenticated = true;
        throw new Error('should not authenticate');
      },
    });
    const response = await handler(
      post({ action: 'export', resource: 'products' }, { Origin: 'https://evil.example' }),
    );
    assertEquals(response.status, 403);
    assertEquals(authenticated, false);
    assertEquals(response.headers.get('Access-Control-Allow-Origin'), 'https://admin.example.com');
  } finally {
    restore();
  }
});

Deno.test('admin-csv maps non-admin authentication failures to 403', async () => {
  const handler = createAdminCsvHandler({
    authenticate: async () => {
      throw new HttpError(403, 'Administrator role required');
    },
  });
  const response = await handler(post({ action: 'import', rows: [{ slug: 'p' }] }));
  assertEquals(response.status, 403);
  assertEquals(await response.json(), { error: 'Administrator role required' });
});

Deno.test('admin-csv maps malformed JSON to a 400 response', async () => {
  const fake = fakeClient();
  const handler = createAdminCsvHandler({
    authenticate: async () => ({
      client: fake.client,
      user: {} as never,
      role: 'admin',
    }),
  });
  const response = await handler(
    new Request('https://functions.example.test/admin-csv', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{',
    }),
  );
  assertEquals(response.status, 400);
  assertEquals(await response.json(), { error: 'A JSON request body is required' });
});

Deno.test('admin-csv imports rows through the database RPC and respects publish', async () => {
  const fake = fakeClient({}, { data: { imported: 2 }, error: null });
  const handler = createAdminCsvHandler({
    authenticate: async () => ({
      client: fake.client,
      user: {} as never,
      role: 'admin',
    }),
  });
  const response = await handler(
    post({ action: 'import', rows: [{ slug: 'a' }, { slug: 'b' }], publish: true }),
  );
  assertEquals(response.status, 200);
  assertEquals(await response.json(), { imported: 2 });
  assertEquals(fake.rpcCalls.length, 1);
  assertEquals(fake.rpcCalls[0].name, 'admin_bulk_import_products');
  assertEquals(fake.rpcCalls[0].args.p_publish, true);
  assertEquals(fake.rpcCalls[0].args.p_rows, [{ slug: 'a' }, { slug: 'b' }]);
});

Deno.test('admin-csv does not call the RPC for an over-limit import', async () => {
  const fake = fakeClient({}, { data: null, error: null });
  const handler = createAdminCsvHandler({
    authenticate: async () => ({
      client: fake.client,
      user: {} as never,
      role: 'admin',
    }),
  });
  const response = await handler(
    post({
      action: 'import',
      rows: Array.from({ length: 501 }, () => ({ slug: 'p' })),
    }),
  );
  assertEquals(response.status, 400);
  assertEquals(fake.rpcCalls.length, 0);
});

Deno.test('admin-csv exports products with formula-neutralized cells', async () => {
  const fake = fakeClient({
    products: [
      {
        slug: 'p1',
        name: '=SUM(A1)',
        model_number: 'm1',
        release_year: 2024,
        summary: null,
        technical_notes: null,
        connector_type: 'USB-C',
        form_factor: 'cable',
        status: 'published',
        brand_id: 'b1',
        category_id: 'c1',
      },
    ],
    brands: [{ id: 'b1', slug: 'brand-one' }],
    categories: [{ id: 'c1', slug: 'cables' }],
  });
  const handler = createAdminCsvHandler({
    authenticate: async () => ({
      client: fake.client,
      user: {} as never,
      role: 'admin',
    }),
  });
  const response = await handler(post({ action: 'export', resource: 'products' }));
  assertEquals(response.status, 200);
  assertEquals(response.headers.get('Content-Type'), 'text/csv; charset=utf-8');
  assertEquals(response.headers.get('Content-Disposition'), 'attachment; filename="products.csv"');
  const body = await response.text();
  assert(body.startsWith('\uFEFFslug,name,brand_slug,category_slug'));
  assert(body.includes('brand-one,cables'));
  assert(body.includes("'=SUM(A1)"));
});

Deno.test('admin-csv does not return a partial export when a lookup fails', async () => {
  const fake = fakeClient(
    {
      products: [
        {
          slug: 'p1',
          name: 'Product',
          model_number: null,
          release_year: null,
          summary: null,
          technical_notes: null,
          connector_type: null,
          form_factor: null,
          status: 'published',
          brand_id: 'b1',
          category_id: 'c1',
        },
      ],
    },
    { data: null, error: null },
    { brands: { message: 'lookup failed' } },
  );
  const handler = createAdminCsvHandler({
    authenticate: async () => ({
      client: fake.client,
      user: {} as never,
      role: 'admin',
    }),
  });
  const response = await handler(post({ action: 'export', resource: 'products' }));
  assertEquals(response.status, 500);
  assertEquals(await response.json(), { error: 'Product export failed' });
});

Deno.test('admin-csv exports rule evidence counts', async () => {
  const fake = fakeClient({
    compatibility_rules: [
      {
        id: 'r1',
        subject_product_id: 'p1',
        object_product_id: 'p2',
        relationship: 'fits',
        verification: 'official',
        confidence: 90,
        risk: 'none',
        summary: 'Fits',
        technical_details: null,
        required_modifications: null,
        status: 'published',
      },
    ],
    products: [
      { id: 'p1', slug: 'product-one' },
      { id: 'p2', slug: 'product-two' },
    ],
    rule_evidence: [{ rule_id: 'r1' }, { rule_id: 'r1' }],
  });
  const handler = createAdminCsvHandler({
    authenticate: async () => ({
      client: fake.client,
      user: {} as never,
      role: 'admin',
    }),
  });
  const response = await handler(post({ action: 'export', resource: 'rules' }));
  assertEquals(response.status, 200);
  const body = await response.text();
  assert(body.includes('product-one,product-two,fits'));
  assert(body.endsWith(',2\r\n'));
});
