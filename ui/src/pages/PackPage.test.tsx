import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import PackPage from './PackPage';

vi.mock('../api/packs', () => ({
  packs: {
    get: vi.fn().mockResolvedValue({
      slug: 'my-pack',
      contents: [{ element: 'pdf-skill', version: '1.0.0', versionConstraint: '1.0.0' }],
    }),
    addContent: vi.fn().mockResolvedValue({ slug: 'my-pack', contents: [] }),
    downloadPackUrl: vi.fn(() => 'http://test/pack'),
  },
}));

vi.mock('../api/elements', () => ({
  elements: {
    list: vi.fn().mockResolvedValue([
      { slug: 'logo-skill', type: 'SKILL', name: 'Logo', description: 'd', team: 't',
        category: null, tags: [], visibility: 'PUBLIC', latestVersion: '2.0.0', downloadsCount: 0 },
    ]),
    get: vi.fn(), create: vi.fn(), versions: vi.fn(), publishVersion: vi.fn(),
    downloadVersionUrl: vi.fn(), downloadFileUrl: vi.fn(),
  },
}));

vi.mock('../auth/KeycloakProvider', () => ({
  useAuth: () => ({ authenticated: true, token: 't', displayName: 'A', login: vi.fn(), logout: vi.fn() }),
}));

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/packs/my-pack']}>
        <Routes>
          <Route path="/packs/:slug" element={<PackPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

test('shows pack contents and download button', async () => {
  renderPage();
  expect(await screen.findByRole('heading', { name: 'Пак: my-pack' })).toBeInTheDocument();
  expect(screen.getByText('pdf-skill')).toBeInTheDocument();
  expect(screen.getByText('1.0.0')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Скачать пак' })).toBeInTheDocument();
});

test('add content calls API', async () => {
  const { packs } = await import('../api/packs');
  renderPage();
  await userEvent.click(await screen.findByRole('button', { name: 'Добавить элемент' }));
  await userEvent.click(await screen.findByRole('button', { name: 'Добавить' }));
  await waitFor(() => expect(packs.addContent).toHaveBeenCalledWith(
    'my-pack', 'logo-skill', 'latest'));
});
