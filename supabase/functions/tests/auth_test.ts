import { assert, assertEquals } from 'jsr:@std/assert@1';
import {
  allowedAdminOrigins,
  corsHeaders,
  csvCell,
  handleOptions,
  json,
  rejectDisallowedOrigin,
  toCsv,
} from '../_shared/auth.ts';

const ENV_KEYS = ['ADMIN_ORIGIN'] as const;

function withEnv(values: Partial<Record<(typeof ENV_KEYS)[number], string | undefined>>): () => void {
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

Deno.test('CORS defaults to local admin origins and never wildcard', () => {
  const restore = withEnv({ ADMIN_ORIGIN: undefined });
  try {
    assertEquals(allowedAdminOrigins(), ['http://localhost:5173', 'http://127.0.0.1:5173']);
    const local = corsHeaders(
      new Request('https://example.test', { headers: { Origin: 'http://localhost:5173' } }),
    );
    assertEquals(local['Access-Control-Allow-Origin'], 'http://localhost:5173');

    const foreign = corsHeaders(
      new Request('https://example.test', { headers: { Origin: 'https://evil.example' } }),
    );
    assertEquals(foreign['Access-Control-Allow-Origin'], 'http://localhost:5173');
    assertEquals(foreign['Vary'], 'Origin');
  } finally {
    restore();
  }
});

Deno.test('CORS accepts exact configured origins and rejects wildcard', () => {
  const restore = withEnv({ ADMIN_ORIGIN: 'https://admin.example.com/, *' });
  try {
    assertEquals(allowedAdminOrigins(), ['https://admin.example.com']);
    const allowed = new Request('https://example.test', {
      method: 'OPTIONS',
      headers: { Origin: 'https://admin.example.com' },
    });
    const allowedOptions = handleOptions(allowed);
    assertEquals(allowedOptions?.status, 204);
    assertEquals(allowedOptions?.headers.get('Access-Control-Allow-Origin'), 'https://admin.example.com');

    const blocked = new Request('https://example.test', {
      method: 'OPTIONS',
      headers: { Origin: 'https://evil.example' },
    });
    assertEquals(handleOptions(blocked)?.status, 403);
    assertEquals(rejectDisallowedOrigin(blocked)?.status, 403);
    assertEquals(rejectDisallowedOrigin(new Request('https://example.test', { method: 'POST' })), null);
  } finally {
    restore();
  }
});

Deno.test('JSON responses carry restrictive CORS and JSON content type', async () => {
  const restore = withEnv({ ADMIN_ORIGIN: 'https://admin.example.com' });
  try {
    const response = json(
      { ok: true },
      201,
      new Request('https://example.test', { headers: { Origin: 'https://admin.example.com' } }),
    );
    assertEquals(response.status, 201);
    assertEquals(response.headers.get('Access-Control-Allow-Origin'), 'https://admin.example.com');
    assertEquals(response.headers.get('Content-Type'), 'application/json; charset=utf-8');
    assertEquals(await response.json(), { ok: true });
  } finally {
    restore();
  }
});

Deno.test('CSV cells neutralize spreadsheet formulas and quote delimiters', () => {
  assertEquals(csvCell('=1+1'), "'=1+1");
  assertEquals(csvCell('+SUM(A1)'), "'+SUM(A1)");
  assertEquals(csvCell('-2'), "'-2");
  assertEquals(csvCell('@cmd'), "'@cmd");
  assertEquals(csvCell('plain'), 'plain');
  assertEquals(csvCell('a,b'), '"a,b"');
  assertEquals(csvCell('a"b'), '"a""b"');
  assertEquals(csvCell('a\nb'), '"a\nb"');
});

Deno.test('CSV export has a BOM, header, and neutralized values', () => {
  const csv = toCsv(['name', 'notes'], [{ name: '=SUM(A1)', notes: 'hello, world' }]);
  assert(csv.startsWith('\uFEFFname,notes\r\n'));
  assert(csv.includes("'=SUM(A1)"));
  assert(csv.includes('"hello, world"'));
  assert(csv.endsWith('\r\n'));
});
