import { createClient, type SupabaseClient, type User } from 'npm:@supabase/supabase-js@2.57.4';

export const corsHeaders = {
  'Access-Control-Allow-Origin': Deno.env.get('ADMIN_ORIGIN') ?? '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type, x-cron-secret',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Vary': 'Origin',
};

export class HttpError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

const SUPABASE_URL = Deno.env.get('SUPABASE_URL');
const ANON_KEY = Deno.env.get('SUPABASE_ANON_KEY');

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, 'Content-Type': 'application/json; charset=utf-8' },
  });
}

export function handleOptions(request: Request): Response | null {
  if (request.method === 'OPTIONS') {
    return new Response(null, { status: 204, headers: corsHeaders });
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
  if (!SUPABASE_URL || !ANON_KEY) {
    throw new HttpError(503, 'Function environment is incomplete');
  }
  const authorization = request.headers.get('Authorization');
  if (!authorization?.startsWith('Bearer ')) {
    throw new HttpError(401, 'A user access token is required');
  }
  return createClient(SUPABASE_URL, ANON_KEY, {
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

export function errorResponse(error: unknown): Response {
  if (error instanceof HttpError) {
    return json({ error: error.message }, error.status);
  }
  console.error('Unhandled function error', error);
  return json({ error: 'Internal server error' }, 500);
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
