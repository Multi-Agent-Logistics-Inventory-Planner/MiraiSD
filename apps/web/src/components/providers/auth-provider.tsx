"use client";

import {
  createContext,
  useContext,
  useEffect,
  useState,
  useCallback,
} from "react";
import { useRouter } from "next/navigation";
import type { Session, User as SupabaseUser } from "@supabase/supabase-js";
import { getSupabaseClient } from "@/lib/supabase";
import { validateToken, getCurrentUser } from "@/lib/api/auth";
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
  const [isInitialized, setIsInitialized] = useState(false);

  const validateAndSetUser = useCallback(
    async (supabaseUser: SupabaseUser, accessToken: string) => {
      try {
        console.log("[Auth] Validating token...");
        const validation = await validateToken(accessToken);
        console.log("[Auth] Validation result:", validation);

        if (validation.valid && validation.role) {
          // Fetch fresh user data from database
          try {
            console.log("[Auth] Fetching user from database...");
            const dbUser = await getCurrentUser(accessToken);
            console.log("[Auth] Database user:", dbUser);
            setUser({
              id: supabaseUser.id,
              email: supabaseUser.email || "",
              role: dbUser.role,
              personId: dbUser.id,
              personName: dbUser.fullName,
            });
          } catch (dbError) {
            // Fallback to JWT data if /me endpoint fails
            console.log("[Auth] Database fetch failed, using JWT data:", dbError);
            setUser({
              id: supabaseUser.id,
              email: supabaseUser.email || "",
              role: validation.role,
              personId: validation.personId,
              personName: validation.personName,
            });
          }
        } else {
          // Backend validation failed - clear auth state
          console.log("[Auth] Validation failed - no valid role:", validation);
          const supabase = getSupabaseClient();
          if (supabase) {
            await supabase.auth.signOut();
          }
          setUser(null);
          setSession(null);
        }
      } catch (error) {
        // Validation failed - user might not have backend access
        console.error("[Auth] Token validation error:", error);
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
        console.log("[Auth] Getting initial session...");
        const {
          data: { session: initialSession },
        } = await supabase.auth.getSession();
        console.log("[Auth] Initial session:", initialSession ? "exists" : "null");

        if (initialSession?.user && initialSession.access_token) {
          setSession(initialSession);
          await validateAndSetUser(
            initialSession.user,
            initialSession.access_token
          );
        } else {
          console.log("[Auth] No initial session found");
        }
      } catch (error) {
        console.error("[Auth] Failed to get session:", error);
      } finally {
        setIsLoading(false);
        setIsInitialized(true);
      }
    };

    initializeAuth();

    // Listen for auth changes
    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange(async (event, newSession) => {
      console.log("[Auth] onAuthStateChange:", event, newSession ? "has session" : "no session");
      setSession(newSession);

      // Only handle SIGNED_OUT explicitly - don't redirect just because newSession is null
      // during initialization (INITIAL_SESSION event can have null session briefly)
      if (event === "SIGNED_OUT") {
        console.log("[Auth] SIGNED_OUT event - redirecting to login");
        setUser(null);
        router.push("/login");
      } else if (
        (event === "SIGNED_IN" || event === "TOKEN_REFRESHED") &&
        newSession?.user &&
        newSession.access_token
      ) {
        await validateAndSetUser(newSession.user, newSession.access_token);
      }
    });

    return () => {
      subscription.unsubscribe();
    };
  }, [router, validateAndSetUser]);

  const signOut = useCallback(async () => {
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
      await validateAndSetUser(currentSession.user, currentSession.access_token);
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
