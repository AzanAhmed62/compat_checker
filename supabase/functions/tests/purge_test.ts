import { assert, assertEquals } from 'jsr:@std/assert@1';
import {
  createPurgeHandler,
  parsePurgeLimit,
  secretsMatch,
  type PurgeClient,
} from '../purge-pending-accounts/handler.ts';

const ENV_KEYS = [
  'SUPABASE_URL',
  'SUPABASE_SERVICE_ROLE_KEY',
  'PURGE_CRON_SECRET',
] as const;

type EnvValues = Partial<Record<(typeof ENV_KEYS)[number], string | undefined>>;

function withEnv(values: EnvValues): () => void {
  const previous = new Map<string, string | undefined>();
  for (const key of ENV_KEYS) {
    previous.set(key, Deno.env.get(key));
    const value = values[key];
    if (value === undefined) Deno.env.delete(key);
    else Deno.env.set(key, value);
  }
  return () => {
    for (const key of ENV_KEYS) {
      const value = previous.get(key);
      if (value === undefined) Deno.env.delete(key);
      else Deno.env.set(key, value);
    }
  };
}

function purgeRequest(headers: Record<string, string> = {}): Request {
  return new Request('https://functions.example.test/purge-pending-accounts', {
    method: 'POST',
    headers,
  });
}

function fakePurge(result: { data: unknown; error: { message: string } | null }) {
  const calls: Array<{ url: string; key: string; name: string; args: Record<string, unknown> }> = [];
  return {
    calls,
    createAdminClient: (url: string, key: string): PurgeClient => ({
      rpc: async (name, args) => {
        calls.push({ url, key, name, args });
        return result;
      },
    }),
  };
}

Deno.test('secretsMatch compares full values without prefix acceptance', () => {
  assertEquals(secretsMatch('correct-horse', 'correct-horse'), true);
  assertEquals(secretsMatch('correct-hors', 'correct-horse'), false);
  assertEquals(secretsMatch('correct-horse-extra', 'correct-horse'), false);
  assertEquals(secretsMatch('', 'correct-horse'), false);
  assertEquals(secretsMatch(null, 'correct-horse'), false);
});

Deno.test('parsePurgeLimit enforces an integer 1-500 bound', () => {
  assertEquals(parsePurgeLimit(null), 100);
  assertEquals(parsePurgeLimit('1'), 1);
  assertEquals(parsePurgeLimit('500'), 500);
  for (const invalid of ['', '0', '501', '1.5', '1x', '-1', ' 2', '999999999999999999999']) {
    try {
      parsePurgeLimit(invalid);
      throw new Error(`Expected ${invalid} to be rejected`);
    } catch (error) {
      assert(error instanceof Error);
      assertEquals((error as { status?: number }).status, 400);
    }
  }
});

Deno.test('purge returns 503 when its server-only environment is missing', async () => {
  const restore = withEnv({
    SUPABASE_URL: undefined,
    SUPABASE_SERVICE_ROLE_KEY: undefined,
    PURGE_CRON_SECRET: undefined,
  });
  try {
    const response = await createPurgeHandler().call(
      undefined,
      purgeRequest({ 'x-cron-secret': 'secret' }),
    );
    assertEquals(response.status, 503);
    assertEquals(await response.json(), { error: 'Function environment is incomplete' });
  } finally {
    restore();
  }
});

Deno.test('purge returns 503 when the cron secret is not configured', async () => {
  const restore = withEnv({
    SUPABASE_URL: 'http://127.0.0.1:54321',
    SUPABASE_SERVICE_ROLE_KEY: 'service-key',
    PURGE_CRON_SECRET: undefined,
  });
  try {
    const response = await createPurgeHandler().call(
      undefined,
      purgeRequest({ 'x-cron-secret': 'secret' }),
    );
    assertEquals(response.status, 503);
    assertEquals(await response.json(), { error: 'PURGE_CRON_SECRET is not configured' });
  } finally {
    restore();
  }
});

Deno.test('purge rejects missing and incorrect cron credentials', async () => {
  const restore = withEnv({
    SUPABASE_URL: 'http://127.0.0.1:54321',
    SUPABASE_SERVICE_ROLE_KEY: 'service-key',
    PURGE_CRON_SECRET: 'correct-secret',
  });
  try {
    const cases: Array<Record<string, string>> = [{}, { 'x-cron-secret': 'wrong-secret' }];
    for (const headers of cases) {
      const response = await createPurgeHandler().call(undefined, purgeRequest(headers));
      assertEquals(response.status, 401);
      assertEquals(await response.json(), { error: 'Invalid cron credential' });
    }
  } finally {
    restore();
  }
});

Deno.test('purge rejects an invalid bounded limit before creating a privileged client', async () => {
  const restore = withEnv({
    SUPABASE_URL: 'http://127.0.0.1:54321',
    SUPABASE_SERVICE_ROLE_KEY: 'service-key',
    PURGE_CRON_SECRET: 'correct-secret',
  });
  try {
    const fake = fakePurge({ data: null, error: null });
    const handler = createPurgeHandler({ createAdminClient: fake.createAdminClient });
    const response = await handler(
      purgeRequest({ 'x-cron-secret': 'correct-secret', 'x-purge-limit': '501' }),
    );
    assertEquals(response.status, 400);
    assertEquals(fake.calls.length, 0);
  } finally {
    restore();
  }
});

Deno.test('purge calls the RPC with the service-role client and bounded limit', async () => {
  const restore = withEnv({
    SUPABASE_URL: 'http://127.0.0.1:54321',
    SUPABASE_SERVICE_ROLE_KEY: 'service-key',
    PURGE_CRON_SECRET: 'correct-secret',
  });
  try {
    const fake = fakePurge({
      data: { purged: 3, failed: 0 },
      error: null,
    });
    const handler = createPurgeHandler({ createAdminClient: fake.createAdminClient });
    const response = await handler(
      purgeRequest({ 'x-cron-secret': 'correct-secret', 'x-purge-limit': '5' }),
    );
    assertEquals(response.status, 200);
    assertEquals(await response.json(), { ok: true, purged: 3, failed: 0 });
    assertEquals(fake.calls.length, 1);
    assertEquals(fake.calls[0].url, 'http://127.0.0.1:54321');
    assertEquals(fake.calls[0].key, 'service-key');
    assertEquals(fake.calls[0].name, 'purge_pending_accounts');
    assertEquals(fake.calls[0].args, { p_limit: 5 });
  } finally {
    restore();
  }
});

Deno.test('purge defaults to 100 and does not expose database error details', async () => {
  const restore = withEnv({
    SUPABASE_URL: 'http://127.0.0.1:54321',
    SUPABASE_SERVICE_ROLE_KEY: 'service-key',
    PURGE_CRON_SECRET: 'correct-secret',
  });
  try {
    const failed = fakePurge({
      data: null,
      error: { message: 'internal database detail' },
    });
    const failedResponse = await createPurgeHandler({
      createAdminClient: failed.createAdminClient,
    })(purgeRequest({ 'x-cron-secret': 'correct-secret' }));
    assertEquals(failedResponse.status, 500);
    assertEquals(await failedResponse.json(), { error: 'Account purge failed' });
    assertEquals(failed.calls[0].args, { p_limit: 100 });

    const ok = fakePurge({ data: null, error: null });
    const okResponse = await createPurgeHandler({
      createAdminClient: ok.createAdminClient,
    })(purgeRequest({ 'x-cron-secret': 'correct-secret' }));
    assertEquals(okResponse.status, 200);
    assertEquals(await okResponse.json(), { ok: true });
  } finally {
    restore();
  }
});
