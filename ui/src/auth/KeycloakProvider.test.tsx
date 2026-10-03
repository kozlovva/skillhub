import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

const keycloakMock = vi.hoisted(() => ({
  init: vi.fn().mockResolvedValue(true),
  authenticated: false,
  token: 'jwt-token',
  tokenParsed: undefined as { preferred_username?: string } | undefined,
  login: vi.fn(),
  logout: vi.fn(),
  updateToken: vi.fn().mockResolvedValue(true),
}));

vi.mock('keycloak-js', () => ({ default: vi.fn(() => keycloakMock) }));

vi.mock('../api/client', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/client')>()),
  setAuthToken: vi.fn(),
}));

function Probe() {
  const auth = useAuth();
  if (!auth.authenticated) return <button onClick={auth.login}>login</button>;
  return <span>{auth.displayName}</span>;
}

function renderProvider() {
  return render(
    <KeycloakProvider>
      <MemoryRouter>
        <Probe />
      </MemoryRouter>
    </KeycloakProvider>
  );
}

// NOTE: destructuring `KeycloakProvider` from a dynamic import of this module is
// broken by a vitest/vite namespace interop quirk, so the module is imported
// statically and vi.mock factories access shared state lazily.
import KeycloakProvider, { useAuth } from './KeycloakProvider';

describe('KeycloakProvider', () => {
  beforeEach(() => {
    vi.mocked(keycloakMock.init).mockClear();
    keycloakMock.authenticated = false;
    keycloakMock.tokenParsed = undefined;
  });

  it('renders children and shows login button when unauthenticated', async () => {
    renderProvider();
    expect(await screen.findByRole('button', { name: 'login' })).toBeInTheDocument();
  });

  it('sets auth token on init and exposes displayName when authenticated', async () => {
    const client = await import('../api/client');
    keycloakMock.authenticated = true;
    keycloakMock.tokenParsed = { preferred_username: 'alice' };
    renderProvider();
    expect(await screen.findByText('alice')).toBeInTheDocument();
    expect(client.setAuthToken).toHaveBeenCalledWith('jwt-token');
  });
});
