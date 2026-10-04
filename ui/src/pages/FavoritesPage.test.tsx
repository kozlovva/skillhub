import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import FavoritesPage from './FavoritesPage';

const { getMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
}));

vi.mock('../api/client', () => ({
  api: { get: getMock },
  toApiError: (e: unknown) => ({
    status: 0, code: 'ERROR',
    message: e instanceof Error ? e.message : String(e), details: null,
  }),
}));

vi.mock('../auth/KeycloakProvider', () => ({
  useAuth: () => ({ authenticated: true, token: 't', displayName: 'A', login: vi.fn(), logout: vi.fn() }),
}));

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <FavoritesPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

test('renders favorite elements', async () => {
  getMock.mockResolvedValue({
    data: [{ slug: 'my-skill', type: 'SKILL', name: 'My Skill', description: 'd',
      team: null, category: null, tags: [], visibility: 'PUBLIC',
      latestVersion: '1.0.0', downloadsCount: 1 }],
  });
  renderPage();
  expect(await screen.findByText('My Skill')).toBeInTheDocument();
  expect(screen.getByText('my-skill')).toBeInTheDocument();
});

test('shows empty state without favorites', async () => {
  getMock.mockResolvedValue({ data: [] });
  renderPage();
  expect(await screen.findByText('Пока ничего в избранном')).toBeInTheDocument();
});
