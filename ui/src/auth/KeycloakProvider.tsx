import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import Keycloak from 'keycloak-js';
import { setAuthToken } from '../api/client';
import { me as meApi } from '../api/me';

export interface AuthContextValue {
  authenticated: boolean;
  token: string | null;
  displayName: string | null;
  isAdmin: boolean;
  myTeamRoles: Record<string, string>;
  teamRoleOf: (teamSlug: string) => string | null;
  login: () => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue>({
  authenticated: false,
  token: null,
  displayName: null,
  isAdmin: false,
  myTeamRoles: {},
  teamRoleOf: () => null,
  login: () => {},
  logout: () => {},
});

export function useAuth() {
  return useContext(AuthContext);
}

const keycloak = new Keycloak({
  // VITE_OIDC_URL is the Keycloak auth-server BASE URL (e.g. http://localhost:8180), not the issuer.
  url: import.meta.env.VITE_OIDC_URL ?? 'http://localhost:8180',
  realm: import.meta.env.VITE_OIDC_REALM ?? 'skillhub',
  clientId: import.meta.env.VITE_OIDC_CLIENT_ID ?? 'skillhub-ui',
});

export default function KeycloakProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  const [token, setToken] = useState<string | null>(null);
  const [isAdmin, setIsAdmin] = useState(false);
  const [myTeamRoles, setMyTeamRoles] = useState<Record<string, string>>({});

  useEffect(() => {
    keycloak.init({ onLoad: 'check-sso', pkceMethod: 'S256' }).then(() => {
      setAuthenticated(keycloak.authenticated);
      setToken(keycloak.token ?? null);
      setAuthToken(keycloak.token ?? null);
      setReady(true);
    }).catch(() => {
      // Keycloak недоступен — показываем UI в анонимном режиме (публичный каталог)
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

  useEffect(() => {
    if (!authenticated) {
      setIsAdmin(false);
      setMyTeamRoles({});
      return;
    }
    meApi.get().then((info) => {
      setIsAdmin(info.admin);
      const roles: Record<string, string> = {};
      for (const t of info.teams) roles[t.slug] = t.role;
      setMyTeamRoles(roles);
    }).catch(() => {
      setIsAdmin(false);
      setMyTeamRoles({});
    });
  }, [authenticated]);

  if (!ready) return null;

  return (
    <AuthContext.Provider
      value={{
        authenticated,
        token,
        displayName: keycloak.tokenParsed
          ? (keycloak.tokenParsed as { preferred_username?: string }).preferred_username ?? null
          : null,
        isAdmin,
        myTeamRoles,
        teamRoleOf: (teamSlug: string) => myTeamRoles[teamSlug] ?? null,
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
