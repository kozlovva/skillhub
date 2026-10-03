import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import Keycloak from 'keycloak-js';
import { setAuthToken } from '../api/client';

export interface AuthContextValue {
  authenticated: boolean;
  token: string | null;
  displayName: string | null;
  login: () => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue>({
  authenticated: false,
  token: null,
  displayName: null,
  login: () => {},
  logout: () => {},
});

export function useAuth() {
  return useContext(AuthContext);
}

const keycloak = new Keycloak({
  url: import.meta.env.VITE_OIDC_URL ?? 'http://localhost:8180',
  realm: 'skillhub',
  clientId: import.meta.env.VITE_OIDC_CLIENT_ID ?? 'skillhub-ui',
});

export default function KeycloakProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  const [token, setToken] = useState<string | null>(null);

  useEffect(() => {
    keycloak.init({ onLoad: 'check-sso', pkceMethod: 'S256' }).then(() => {
      setAuthenticated(keycloak.authenticated);
      setToken(keycloak.token ?? null);
      setAuthToken(keycloak.token ?? null);
      setReady(true);
    });
    const refresh = setInterval(() => {
      if (keycloak.authenticated) {
        keycloak.updateToken(60).then(() => {
          setToken(keycloak.token ?? null);
          setAuthToken(keycloak.token ?? null);
        }).catch(() => keycloak.login());
      }
    }, 30000);
    return () => clearInterval(refresh);
  }, []);

  if (!ready) return null;

  return (
    <AuthContext.Provider
      value={{
        authenticated,
        token,
        displayName: keycloak.tokenParsed
          ? (keycloak.tokenParsed as { preferred_username?: string }).preferred_username ?? null
          : null,
        login: () => keycloak.login(),
        logout: () => {
          setAuthToken(null);
          keycloak.logout();
        },
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export { keycloak };
