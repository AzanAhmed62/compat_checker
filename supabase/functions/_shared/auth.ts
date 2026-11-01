import { createClient, type SupabaseClient, type User } from 'npm:@supabase/supabase-js@2.57.4';

const LOCAL_ADMIN_ORIGINS = ['http://localhost:5173', 'http://127.0.0.1:5173'];

/**
 * ADMIN_ORIGIN is a comma-separated list of exact browser origins allowed to
 * call the functions. It is required for a deployment; local development has a
 * deliberately narrow localhost-only fallback. A wildcard is never honored.
 */
export function allowedAdminOrigins(): string[] {
  const configured = (Deno.env.get('ADMIN_ORIGIN') ?? '')
    .split(',')
    .map((origin) => origin.trim().replace(/\/$/, ''))
    .filter((origin) => origin.length > 0 && origin !== '*');
  return configured.length > 0 ? configured : LOCAL_ADMIN_ORIGINS;
}

export function isAdminOriginAllowed(origin: string): boolean {
  return allowedAdminOrigins().includes(origin);
}

/**
 * Build response headers for one request. A browser origin is reflected only
 * when it is explicitly allowed; otherwise the configured primary origin is
 * returned so the browser rejects the response.
 */
export function corsHeaders(request?: Request): Record<string, string> {
  const origins = allowedAdminOrigins();
  const origin = request?.headers.get('Origin');
  const allowedOrigin = origin && origins.includes(origin) ? origin : origins[0];
  return {
    'Access-Control-Allow-Origin': allowedOrigin,
    'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type, x-cron-secret',
    'Access-Control-Allow-Methods': 'POST, OPTIONS',
    'Access-Control-Max-Age': '600',
    'Vary': 'Origin',
  };
}

export class HttpError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

function supabaseUrl(): string | undefined {
  return Deno.env.get('SUPABASE_URL')?.trim() || undefined;
}

function anonKey(): string | undefined {
  return Deno.env.get('SUPABASE_ANON_KEY')?.trim() || undefined;
}

export function json(body: unknown, status = 200, request?: Request): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders(request), 'Content-Type': 'application/json; charset=utf-8' },
  });
}

export function handleOptions(request: Request): Response | null {
  if (request.method !== 'OPTIONS') return null;
  const origin = request.headers.get('Origin');
  const allowed = !origin || isAdminOriginAllowed(origin);
  return new Response(null, {
    status: allowed ? 204 : 403,
    headers: corsHeaders(request),
  });
}

/** Reject browser requests from origins outside ADMIN_ORIGIN. */
export function rejectDisallowedOrigin(request: Request): Response | null {
  const origin = request.headers.get('Origin');
  if (origin && !isAdminOriginAllowed(origin)) {
    return json({ error: 'Origin not allowed' }, 403, request);
  }
  return null;
}

export function requirePost(request: Request): void {
  if (request.method !== 'POST') {
    throw new HttpError(405, 'Method not allowed');
  }
}

/**
 * Build a caller-scoped client. RLS is evaluated as the signed-in user; this
 * worker never uses the service role for user-initiated reads or writes.
 */
export function userClient(request: Request): SupabaseClient {
  const url = supabaseUrl();
  const key = anonKey();
  if (!url || !key) {
    throw new HttpError(503, 'Function environment is incomplete');
  }
  const authorization = request.headers.get('Authorization');
  if (!authorization?.startsWith('Bearer ')) {
    throw new HttpError(401, 'A user access token is required');
  }
  return createClient(url, key, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
}

export interface AdminContext {
  client: SupabaseClient;
  user: User;
  role: string;
}

export async function requireAdmin(request: Request): Promise<AdminContext> {
  const client = userClient(request);
  const { data, error } = await client.auth.getUser();
  if (error || !data.user) {
    throw new HttpError(401, 'Invalid or expired session');
  }

  const { data: me, error: meError } = await client.rpc('me');
  if (meError) {
    throw new HttpError(403, 'Unable to resolve the current profile');
  }
  const role = String((me as { role?: string } | null)?.role ?? '');
  if (role !== 'admin') {
    throw new HttpError(403, 'Administrator role required');
  }
  return { client, user: data.user, role };
}

export function errorResponse(error: unknown, request?: Request): Response {
  if (error instanceof HttpError) {
    return json({ error: error.message }, error.status, request);
  }
  console.error('Unhandled function error', error);
  return json({ error: 'Internal server error' }, 500, request);
}

export function csvCell(value: unknown): string {
  if (value === null || value === undefined) return '';
  const text = String(value);
  // Spreadsheet applications may evaluate a cell that begins with one of these
  // characters even when the field is quoted. Prefix untrusted text with an
  // apostrophe so exported data is treated as text.
  const safe = /^[=+\-@\t\r]/.test(text) ? `'${text}` : text;
  return /[",\r\n]/.test(safe) ? `"${safe.replaceAll('"', '""')}"` : safe;
}

export function toCsv(headers: readonly string[], rows: readonly Record<string, unknown>[]): string {
  const lines = [headers.map(csvCell).join(',')];
  for (const row of rows) {
    lines.push(headers.map((header) => csvCell(row[header])).join(','));
  }
  // Excel-friendly UTF-8 BOM.
  return `\uFEFF${lines.join('\r\n')}\r\n`;
}
