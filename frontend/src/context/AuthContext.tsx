import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { authApi } from '../api/authApi';
import {
  AUTH_EXPIRED_EVENT,
  clearStoredAuth,
  loadStoredAuth,
  saveStoredAuth,
} from '../api/client';
import type { Role } from '../types/api';

export interface AuthUser {
  id: number;
  username: string;
  role: Role;
}

interface AuthContextValue {
  user: AuthUser | null;
  initializing: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [initializing, setInitializing] = useState<boolean>(
    () => loadStoredAuth() !== null,
  );

  useEffect(() => {
    const stored = loadStoredAuth();
    if (!stored) {
      return;
    }

    let cancelled = false;

    authApi
      .me()
      .then((profile) => {
        if (!cancelled) {
          setUser({
            id: profile.id,
            username: profile.username,
            role: profile.role,
          });
        }
      })
      .catch(() => {
        if (!cancelled) {
          clearStoredAuth();
          setUser(null);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setInitializing(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    const onExpired = () => setUser(null);
    window.addEventListener(AUTH_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, onExpired);
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const response = await authApi.login(username, password);
    saveStoredAuth({
      token: response.token,
      username: response.username,
      role: response.role,
    });

    const profile = await authApi.me();
    setUser({
      id: profile.id,
      username: profile.username,
      role: profile.role,
    });
  }, []);

  const logout = useCallback(() => {
    clearStoredAuth();
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, initializing, login, logout }),
    [user, initializing, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

export function hasAnyRole(user: AuthUser | null, roles: Role[]): boolean {
  return user !== null && roles.includes(user.role);
}
