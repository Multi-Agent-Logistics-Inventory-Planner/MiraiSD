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
import { validateToken } from "@/lib/api/auth";
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

  const validateAndSetUser = useCallback(
    async (supabaseUser: SupabaseUser, accessToken: string) => {
      try {
        const validation = await validateToken(accessToken);

        if (validation.valid && validation.role) {
          setUser({
            id: supabaseUser.id,
            email: supabaseUser.email || "",
            role: validation.role,
            personId: validation.personId,
            personName: validation.personName,
          });
        } else {
          // Backend validation failed - clear auth state
          const supabase = getSupabaseClient();
          if (supabase) {
            await supabase.auth.signOut();
          }
          setUser(null);
          setSession(null);
        }
      } catch {
        // Validation failed - user might not have backend access
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

      if (event === "SIGNED_OUT" || !newSession) {
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
