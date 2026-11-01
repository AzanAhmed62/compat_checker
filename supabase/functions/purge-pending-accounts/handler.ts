import { createClient } from 'npm:@supabase/supabase-js@2.57.4';
import {
  HttpError,
  errorResponse,
  handleOptions,
  json,
  rejectDisallowedOrigin,
  requirePost,
} from '../_shared/auth.ts';

export interface PurgeClient {
  rpc(
    name: string,
    args: Record<string, unknown>,
  ): Promise<{ data: unknown; error: { message: string } | null }>;
}

export type PurgeDependencies = {
  createAdminClient: (url: string, key: string) => PurgeClient;
};

function configured(name: string): string | undefined {
  return Deno.env.get(name)?.trim() || undefined;
}

export function secretsMatch(provided: string | null, expected: string): boolean {
  // Compare without early exit. This is a low-frequency cron credential, but
  // constant-time comparison costs nothing here.
  if (!provided) return false;
  const left = new TextEncoder().encode(provided);
  const right = new TextEncoder().encode(expected);
  let mismatch = left.length ^ right.length;
  const max = Math.max(left.length, right.length);
  for (let i = 0; i < max; i += 1) {
    mismatch |= (left[i] ?? 0) ^ (right[i] ?? 0);
  }
  return mismatch === 0;
}

export function parsePurgeLimit(raw: string | null): number {
  if (raw === null) return 100;
  if (!/^\d+$/.test(raw)) {
    throw new HttpError(400, 'x-purge-limit must be between 1 and 500');
  }
  const parsed = Number(raw);
  if (!Number.isSafeInteger(parsed) || parsed < 1 || parsed > 500) {
    throw new HttpError(400, 'x-purge-limit must be between 1 and 500');
  }
  return parsed;
}

function defaultClient(url: string, key: string): PurgeClient {
  return createClient(url, key, {
    auth: { persistSession: false, autoRefreshToken: false },
  }) as unknown as PurgeClient;
}

export function createPurgeHandler(
  dependencies: Partial<PurgeDependencies> = {},
): (request: Request) => Promise<Response> {
  const createAdminClient = dependencies.createAdminClient ?? defaultClient;

  return async function handlePurge(request: Request): Promise<Response> {
    const options = handleOptions(request);
    if (options) return options;

    const originError = rejectDisallowedOrigin(request);
    if (originError) return originError;

    try {
      requirePost(request);
      const supabaseUrl = configured('SUPABASE_URL');
      const serviceRoleKey = configured('SUPABASE_SERVICE_ROLE_KEY');
      const cronSecret = configured('PURGE_CRON_SECRET');
      if (!supabaseUrl || !serviceRoleKey) {
        throw new HttpError(503, 'Function environment is incomplete');
      }
      if (!cronSecret) {
        throw new HttpError(503, 'PURGE_CRON_SECRET is not configured');
      }
      if (!secretsMatch(request.headers.get('x-cron-secret'), cronSecret)) {
        throw new HttpError(401, 'Invalid cron credential');
      }

      const limit = parsePurgeLimit(request.headers.get('x-purge-limit'));
      const admin = createAdminClient(supabaseUrl, serviceRoleKey);
      const { data, error } = await admin.rpc('purge_pending_accounts', { p_limit: limit });
      if (error) {
        console.error('Account purge failed', error.message);
        throw new HttpError(500, 'Account purge failed');
      }

      const result = data && typeof data === 'object' ? (data as Record<string, unknown>) : {};
      return json({ ok: true, ...result }, 200, request);
    } catch (error) {
      return errorResponse(error, request);
    }
  };
}

export const handlePurge = createPurgeHandler();
