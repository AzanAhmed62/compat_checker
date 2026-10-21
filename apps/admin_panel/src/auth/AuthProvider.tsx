import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import type { Session } from '@supabase/supabase-js';
import { fetchMe } from '../lib/api';
import { errorMessage } from '../lib/errors';
import { configurationError, supabase } from '../lib/supabase';
import type { Me } from '../lib/types';

interface AuthContextValue {
  session: Session | null;
  me: Me | null;
  loading: boolean;
  error: string | null;
  signIn(email: string, password: string): Promise<void>;
  signOut(): Promise<void>;
  refresh(): Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [me, setMe] = useState<Me | null>(null);
  const [sessionLoading, setSessionLoading] = useState(true);
  const [profileLoading, setProfileLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadProfile = useCallback(async (activeSession: Session | null) => {
    if (!supabase || !activeSession) {
      setMe(null);
      setProfileLoading(false);
      return;
    }
    setProfileLoading(true);
    setError(null);
    try {
      const profile = await fetchMe();
      if (!profile) {
        throw new Error('No profile exists for this account.');
      }
      setMe(profile);
    } catch (cause) {
      setMe(null);
      setError(errorMessage(cause));
    } finally {
      setProfileLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!supabase) {
      setSessionLoading(false);
      return;
    }
    let active = true;
    void supabase.auth
      .getSession()
      .then(({ data, error: sessionError }) => {
        if (!active) return;
        if (sessionError) setError(errorMessage(sessionError));
        setSession(data.session);
      })
      .finally(() => {
        if (active) setSessionLoading(false);
      });

    const { data: listener } = supabase.auth.onAuthStateChange((_event, nextSession) => {
      setSession(nextSession);
      setSessionLoading(false);
    });
    return () => {
      active = false;
      listener.subscription.unsubscribe();
    };
  }, []);

  const userId = session?.user.id ?? null;
  useEffect(() => {
    if (!userId) {
      setMe(null);
      setProfileLoading(false);
      return;
    }
    void loadProfile(session);
  }, [userId, loadProfile]); // session identity changes only with userId

  const signIn = useCallback(async (email: string, password: string) => {
    if (!supabase) throw new Error(configurationError ?? 'Supabase is not configured');
    const { error: signInError } = await supabase.auth.signInWithPassword({
      email: email.trim(),
      password,
    });
    if (signInError) throw new Error(errorMessage(signInError));
  }, []);

  const signOut = useCallback(async () => {
    if (!supabase) return;
    await supabase.auth.signOut();
    setMe(null);
  }, []);

  const refresh = useCallback(async () => {
    await loadProfile(session);
  }, [loadProfile, session]);

  const value = useMemo<AuthContextValue>(
    () => ({
      session,
      me,
      loading: sessionLoading || profileLoading,
      error,
      signIn,
      signOut,
      refresh,
    }),
    [session, me, sessionLoading, profileLoading, error, signIn, signOut, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
