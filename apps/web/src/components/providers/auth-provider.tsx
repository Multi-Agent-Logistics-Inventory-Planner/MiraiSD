"use client";

import {
  createContext,
  useContext,
  useEffect,
  useState,
  useCallback,
  useRef,
} from "react";
import { useRouter } from "next/navigation";
import type { Session, User as SupabaseUser } from "@supabase/supabase-js";
import { getSupabaseClient } from "@/lib/supabase";
import { getAuthSession } from "@/lib/api/auth";
import {
  getCachedSession,
  setCachedSession,
  clearSessionCache,
} from "@/lib/auth-cache";
import { UserRole } from "@/types/api";

export interface AuthUser {
  id: string;
  email: string;
  role: UserRole;
  personId?: string;
  personName?: string;
}

interface AuthContextType {
  user: AuthUser | null;
  session: Session | null;
  isLoading: boolean;
  signOut: () => Promise<void>;
  refreshAuth: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

interface AuthProviderProps {
  children: React.ReactNode;
}

export function AuthProvider({ children }: AuthProviderProps) {
  const router = useRouter();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [session, setSession] = useState<Session | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  // Track if initial auth has been processed to prevent duplicate SIGNED_IN handling
  const initialAuthProcessedRef = useRef(false);

  const validateAndSetUser = useCallback(
    async (
      supabaseUser: SupabaseUser,
      accessToken: string,
      bypassCache = false
    ) => {
      try {
        // Check cache first (unless bypassing)
        if (!bypassCache) {
          const cached = getCachedSession(accessToken);
          if (cached && cached.valid && cached.role) {
            setUser({
              id: supabaseUser.id,
              email: supabaseUser.email || "",
              role: cached.user?.role ?? cached.role,
              personId: cached.user?.id ?? cached.personId,
              personName: cached.user?.fullName ?? cached.personName,
            });
            return;
          }
        }

        // Make single combined API call
        const sessionResponse = await getAuthSession(accessToken);

        if (sessionResponse.valid && sessionResponse.role) {
          // Use user object if available, fallback to session response fields
          setUser({
            id: supabaseUser.id,
            email: supabaseUser.email || "",
            role: sessionResponse.user?.role ?? sessionResponse.role,
            personId: sessionResponse.user?.id ?? sessionResponse.personId,
            personName:
              sessionResponse.user?.fullName ?? sessionResponse.personName,
          });
          // Cache the successful response
          setCachedSession(accessToken, sessionResponse);
        } else {
          // Backend validation failed - clear auth state
          clearSessionCache();
          const supabase = getSupabaseClient();
          if (supabase) {
            await supabase.auth.signOut();
          }
          setUser(null);
          setSession(null);
        }
      } catch {
        // Validation failed - clear cache and auth state
        clearSessionCache();
        setUser(null);
        setSession(null);
      }
    },
    []
  );

  useEffect(() => {
    const supabase = getSupabaseClient();
    if (!supabase) {
      setIsLoading(false);
      return;
    }

    // Get initial session
    const initializeAuth = async () => {
      try {
        const {
          data: { session: initialSession },
        } = await supabase.auth.getSession();

        if (initialSession?.user && initialSession.access_token) {
          setSession(initialSession);
          await validateAndSetUser(
            initialSession.user,
            initialSession.access_token
          );
          initialAuthProcessedRef.current = true;
        }
      } catch {
        // Failed to get session
      } finally {
        setIsLoading(false);
      }
    };

    initializeAuth();

    // Listen for auth changes
    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange(async (event, newSession) => {
      setSession(newSession);

      // Only handle SIGNED_OUT explicitly - don't redirect just because newSession is null
      // during initialization (INITIAL_SESSION event can have null session briefly)
      if (event === "SIGNED_OUT") {
        setUser(null);
        clearSessionCache();
        initialAuthProcessedRef.current = false;
        router.push("/login");
      } else if (
        (event === "SIGNED_IN" || event === "TOKEN_REFRESHED") &&
        newSession?.user &&
        newSession.access_token
      ) {
        // Skip SIGNED_IN if we already processed it during initialization
        // This prevents duplicate API calls when both initializeAuth and onAuthStateChange fire
        if (event === "SIGNED_IN" && initialAuthProcessedRef.current) {
          return;
        }
        await validateAndSetUser(newSession.user, newSession.access_token);
        initialAuthProcessedRef.current = true;
      }
    });

    return () => {
      subscription.unsubscribe();
    };
  }, [router, validateAndSetUser]);

  const signOut = useCallback(async () => {
    clearSessionCache();
    const supabase = getSupabaseClient();
    if (supabase) {
      await supabase.auth.signOut();
    }
    setUser(null);
    setSession(null);
    router.push("/login");
  }, [router]);

  const refreshAuth = useCallback(async () => {
    const supabase = getSupabaseClient();
    if (!supabase) return;

    const {
      data: { session: currentSession },
    } = await supabase.auth.getSession();

    if (currentSession?.user && currentSession.access_token) {
      setSession(currentSession);
      // Always bypass cache for refreshAuth - user explicitly wants fresh data
      await validateAndSetUser(
        currentSession.user,
        currentSession.access_token,
        true
      );
    }
  }, [validateAndSetUser]);

  return (
    <AuthContext.Provider
      value={{
        user,
        session,
        isLoading,
        signOut,
        refreshAuth,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuthContext() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error("useAuthContext must be used within an AuthProvider");
  }
  return context;
}
