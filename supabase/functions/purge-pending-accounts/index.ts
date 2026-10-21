import { createClient } from 'npm:@supabase/supabase-js@2.57.4';
import { HttpError, errorResponse, handleOptions, json, requirePost } from '../_shared/auth.ts';

const SUPABASE_URL = Deno.env.get('SUPABASE_URL');
const SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
const CRON_SECRET = Deno.env.get('PURGE_CRON_SECRET');

function secretsMatch(provided: string | null, expected: string): boolean {
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

Deno.serve(async (request) => {
  const options = handleOptions(request);
  if (options) return options;

  try {
    requirePost(request);
    if (!SUPABASE_URL || !SERVICE_ROLE_KEY) {
      throw new HttpError(503, 'Function environment is incomplete');
    }
    if (!CRON_SECRET) {
      throw new HttpError(503, 'PURGE_CRON_SECRET is not configured');
    }
    if (!secretsMatch(request.headers.get('x-cron-secret'), CRON_SECRET)) {
      throw new HttpError(401, 'Invalid cron credential');
    }

    let limit = 100;
    const raw = request.headers.get('x-purge-limit');
    if (raw) {
      const parsed = Number.parseInt(raw, 10);
      if (!Number.isInteger(parsed) || parsed < 1 || parsed > 500) {
        throw new HttpError(400, 'x-purge-limit must be between 1 and 500');
      }
      limit = parsed;
    }

    const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, {
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data, error } = await admin.rpc('purge_pending_accounts', { p_limit: limit });
    if (error) {
      console.error('Account purge failed', error.message);
      throw new HttpError(500, 'Account purge failed');
    }

    return json({ ok: true, ...(data as Record<string, unknown>) });
  } catch (error) {
    return errorResponse(error);
  }
});
