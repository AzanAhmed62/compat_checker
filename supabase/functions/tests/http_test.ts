import { assertEquals, assertStringIncludes } from 'jsr:@std/assert@1';
import type { AdminContext } from '../_shared/auth.ts';
import { HttpError } from '../_shared/auth.ts';
import { createAdminCsvHandler } from '../admin-csv/handler.ts';
import {
  type PurgeClient,
  createPurgeHandler,
} from '../purge-pending-accounts/handler.ts';

const TEST_ORIGIN = 'https://admin.compatchecker.test';

// Each Deno test file runs in its own worker process, so this test-only origin
// cannot leak into another suite's environment.
withEnv({ ADMIN_ORIGIN: TEST_ORIGIN });

type RpcResult = { data: unknown; error: { message: string } | null };

function fakeClient(
  tables: Record<string, Array<Record<string, unknown>>> = {},
  rpcResult: RpcResult = { data: null, error: null },
) {
  const rpcCalls: Array<{ name: string; args: Record<string, unknown> }> = [];
  const client = {
    from(table: string) {
      const result = Promise.resolve({ data: tables[table] ?? [], error: null });
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

async function serve(
  handler: (request: Request) => Promise<Response>,
): Promise<{ server: Deno.HttpServer; baseUrl: string }> {
  const server = Deno.serve(
    { hostname: '127.0.0.1', port: 0 },
    handler,
  );
  const address = server.addr;
  const hostname = address.hostname === '0.0.0.0' ? '127.0.0.1' : address.hostname;
  return { server, baseUrl: `http://${hostname}:${address.port}` };
}

function withEnv(values: Record<string, string>): () => void {
  const previous = new Map<string, string | undefined>();
  for (const [key, value] of Object.entries(values)) {
    previous.set(key, Deno.env.get(key));
    Deno.env.set(key, value);
  }
  return () => {
    for (const [key, value] of previous) {
      if (value === undefined) Deno.env.delete(key);
      else Deno.env.set(key, value);
    }
  };
}

Deno.test('admin-csv HTTP preflight reflects an allowed origin', async () => {
  const { client } = fakeClient();
  const { server, baseUrl } = await serve(
    createAdminCsvHandler({ authenticate: async () => ({ client, role: 'admin', user: {} as never }) }),
  );
  try {
    const response = await fetch(`${baseUrl}/admin-csv`, {
      method: 'OPTIONS',
      headers: { Origin: TEST_ORIGIN },
    });
    assertEquals(response.status, 204);
    assertEquals(response.headers.get('access-control-allow-origin'), TEST_ORIGIN);
    assertStringIncludes(
      response.headers.get('access-control-allow-headers') ?? '',
      'authorization',
    );
  } finally {
    await server.shutdown();
  }
});

Deno.test('admin-csv HTTP rejects a disallowed browser origin', async () => {
  const { client } = fakeClient();
  const { server, baseUrl } = await serve(
    createAdminCsvHandler({ authenticate: async () => ({ client, role: 'admin', user: {} as never }) }),
  );
  try {
    const response = await fetch(`${baseUrl}/admin-csv`, {
      method: 'POST',
      headers: {
        Origin: 'https://attacker.example.test',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ action: 'export', resource: 'products' }),
    });
    assertEquals(response.status, 403);
    assertEquals((await response.json()).error, 'Origin not allowed');
  } finally {
    await server.shutdown();
  }
});

Deno.test('admin-csv HTTP enforces POST and a JSON body', async () => {
  const { client } = fakeClient();
  const { server, baseUrl } = await serve(
    createAdminCsvHandler({ authenticate: async () => ({ client, role: 'admin', user: {} as never }) }),
  );
  try {
    const getResponse = await fetch(`${baseUrl}/admin-csv`);
    assertEquals(getResponse.status, 405);

    const invalidResponse = await fetch(`${baseUrl}/admin-csv`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{not json',
    });
    assertEquals(invalidResponse.status, 400);
    assertEquals(
      (await invalidResponse.json()).error,
      'A JSON request body is required',
    );
  } finally {
    await server.shutdown();
  }
});

Deno.test('admin-csv HTTP maps handler errors to CORS responses', async () => {
  const { server, baseUrl } = await serve(
    createAdminCsvHandler({
      authenticate: async () => {
        throw new HttpError(401, 'Invalid or expired session');
      },
    }),
  );
  try {
    const response = await fetch(`${baseUrl}/admin-csv`, {
      method: 'POST',
      headers: {
        Origin: TEST_ORIGIN,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ action: 'export', resource: 'rules' }),
    });
    assertEquals(response.status, 401);
    assertEquals(response.headers.get('access-control-allow-origin'), TEST_ORIGIN);
    assertEquals((await response.json()).error, 'Invalid or expired session');
  } finally {
    await server.shutdown();
  }
});

Deno.test('admin-csv HTTP imports through the admin RPC', async () => {
  const { client, rpcCalls } = fakeClient(
    {},
    { data: { imported: 2, updated: 0 }, error: null },
  );
  const { server, baseUrl } = await serve(
    createAdminCsvHandler({ authenticate: async () => ({ client, role: 'admin', user: {} as never }) }),
  );
  try {
    const response = await fetch(`${baseUrl}/admin-csv`, {
      method: 'POST',
      headers: {
        Origin: TEST_ORIGIN,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        action: 'import',
        rows: [{ slug: 'atomic-product' }, { slug: 'second-product' }],
        publish: true,
      }),
    });
    assertEquals(response.status, 200);
    assertEquals(await response.json(), { imported: 2, updated: 0 });
    assertEquals(rpcCalls.length, 1);
    assertEquals(rpcCalls[0].name, 'admin_bulk_import_products');
    assertEquals(rpcCalls[0].args.p_rows, [
      { slug: 'atomic-product' },
      { slug: 'second-product' },
    ]);
    assertEquals(rpcCalls[0].args.p_publish, true);
  } finally {
    await server.shutdown();
  }
});

Deno.test('admin-csv HTTP exports CSV with a spreadsheet-safe BOM', async () => {
  const { client } = fakeClient({
    products: [
      {
        id: 'product-1',
        slug: 'aurora-x1',
        name: 'Aurora X1',
        model_number: 'X1',
        release_year: 2025,
        summary: 'Synthetic fixture',
        technical_notes: null,
        connector_type: null,
        form_factor: null,
        status: 'published',
        brand_id: 'brand-1',
        category_id: 'category-1',
      },
    ],
    brands: [{ id: 'brand-1', slug: 'sample-electronics' }],
    categories: [{ id: 'category-1', slug: 'batteries' }],
  });
  const { server, baseUrl } = await serve(
    createAdminCsvHandler({ authenticate: async () => ({ client, role: 'admin', user: {} as never }) }),
  );
  try {
    const response = await fetch(`${baseUrl}/admin-csv`, {
      method: 'POST',
      headers: {
        Origin: TEST_ORIGIN,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ action: 'export', resource: 'products' }),
    });
    assertEquals(response.status, 200);
    assertEquals(response.headers.get('content-type'), 'text/csv; charset=utf-8');
    const bytes = new Uint8Array(await response.arrayBuffer());
    assertEquals([...bytes.slice(0, 3)], [0xef, 0xbb, 0xbf]);
    const body = new TextDecoder().decode(bytes);
    assertStringIncludes(body, 'slug,name,brand_slug,category_slug');
    assertStringIncludes(body, 'aurora-x1,Aurora X1,sample-electronics,batteries');
  } finally {
    await server.shutdown();
  }
});

Deno.test('purge HTTP enforces the cron secret over a real request', async () => {
  const restore = withEnv({
    SUPABASE_URL: 'http://127.0.0.1:54321',
    SUPABASE_SERVICE_ROLE_KEY: 'service-role-test-key',
    PURGE_CRON_SECRET: 'cron-secret-test',
  });
  const calls: Array<{ name: string; args: Record<string, unknown> }> = [];
  const admin: PurgeClient = {
    rpc: async (name, args) => {
      calls.push({ name, args });
      return { data: { purged: 3 }, error: null };
    },
  };
  const { server, baseUrl } = await serve(
    createPurgeHandler({ createAdminClient: () => admin }),
  );
  try {
    const unauthorized = await fetch(`${baseUrl}/purge`, {
      method: 'POST',
      headers: { 'x-cron-secret': 'wrong-secret' },
    });
    assertEquals(unauthorized.status, 401);

    const invalidLimit = await fetch(`${baseUrl}/purge`, {
      method: 'POST',
      headers: { 'x-cron-secret': 'cron-secret-test', 'x-purge-limit': '0' },
    });
    assertEquals(invalidLimit.status, 400);

    const response = await fetch(`${baseUrl}/purge`, {
      method: 'POST',
      headers: { 'x-cron-secret': 'cron-secret-test', 'x-purge-limit': '25' },
    });
    assertEquals(response.status, 200);
    assertEquals(await response.json(), { ok: true, purged: 3 });
    assertEquals(calls, [
      { name: 'purge_pending_accounts', args: { p_limit: 25 } },
    ]);
  } finally {
    await server.shutdown();
    restore();
  }
});

Deno.test('purge HTTP reports incomplete configuration', async () => {
  const restore = withEnv({
    SUPABASE_URL: '',
    SUPABASE_SERVICE_ROLE_KEY: '',
    PURGE_CRON_SECRET: '',
  });
  const { server, baseUrl } = await serve(createPurgeHandler());
  try {
    const response = await fetch(`${baseUrl}/purge`, {
      method: 'POST',
      headers: { 'x-cron-secret': 'anything' },
    });
    assertEquals(response.status, 503);
    assertEquals((await response.json()).error, 'Function environment is incomplete');
  } finally {
    await server.shutdown();
    restore();
  }
});
