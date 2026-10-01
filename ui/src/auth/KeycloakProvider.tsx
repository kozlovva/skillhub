import { createContext, useContext, type ReactNode } from 'react';

export interface AuthContextValue {
  authenticated: boolean;
  displayName: string | null;
  token: string | null;
  login: () => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue>({
  authenticated: false,
  displayName: null,
  token: null,
  login: () => {},
  logout: () => {},
});

export function useAuth() {
  return useContext(AuthContext);
}

export default function KeycloakProvider({ children }: { children: ReactNode }) {
  return <AuthContext.Provider value={{ authenticated: false, displayName: null, token: null, login: () => {}, logout: () => {} }}>{children}</AuthContext.Provider>;
}
