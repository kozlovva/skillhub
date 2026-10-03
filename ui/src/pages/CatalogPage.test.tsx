import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import CatalogPage from './CatalogPage';

vi.mock('../api/search', () => ({
  search: {
    search: vi.fn().mockResolvedValue({
      items: [{
        slug: 'pdf-skill', type: 'SKILL', name: 'PDF Skill', description: 'd',
        team: 'platform', category: null, tags: [], visibility: 'PUBLIC',
        latestVersion: '1.0.0', downloadsCount: 5,
      }],
      total: 1,
      facetsByType: { SKILL: 1 },
    }),
  },
}));

vi.mock('../auth/KeycloakProvider', () => ({
  useAuth: () => ({ authenticated: true, token: 't', displayName: 'A', login: vi.fn(), logout: vi.fn() }),
}));

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <CatalogPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

test('shows elements from search', async () => {
  renderPage();
  expect(await screen.findByText('PDF Skill')).toBeInTheDocument();
  expect(screen.getByText('SKILL')).toBeInTheDocument();
});

test('search field triggers query', async () => {
  const { search } = await import('../api/search');
  renderPage();
  await screen.findByText('PDF Skill');
  await userEvent.type(screen.getByPlaceholderText('Поиск'), 'pdf');
  await waitFor(() => expect(search.search).toHaveBeenCalledWith(
    expect.objectContaining({ q: 'pdf' })
  ));
});
