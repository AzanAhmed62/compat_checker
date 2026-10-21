import { createClient } from '@supabase/supabase-js';

const url = import.meta.env.VITE_SUPABASE_URL?.trim();
const anonKey = import.meta.env.VITE_SUPABASE_ANON_KEY?.trim();

export const configurationError =
  url && anonKey
    ? null
    : 'VITE_SUPABASE_URL and VITE_SUPABASE_ANON_KEY are required. Copy apps/admin_panel/.env.example to .env.local and use the local Supabase anon key.';

export const supabase =
  url && anonKey
    ? createClient(url, anonKey, {
        auth: {
          persistSession: true,
          autoRefreshToken: true,
          detectSessionInUrl: true,
        },
      })
    : null;

export function requireClient() {
  if (!supabase) {
    throw new Error(configurationError ?? 'Supabase is not configured');
  }
  return supabase;
}
