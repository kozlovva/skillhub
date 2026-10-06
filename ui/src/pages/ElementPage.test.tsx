import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ElementPage from './ElementPage';
import { elements } from '../api/elements';
import type { VersionResponse } from '../types';

const snackbar = vi.hoisted(() => ({ showSuccess: vi.fn(), showError: vi.fn() }));

vi.mock('../layout/SnackbarContext', () => ({ useSnackbar: () => snackbar }));

const element = vi.hoisted(() => ({
  slug: 'pdf-skill', type: 'SKILL' as const, name: 'PDF Skill', description: 'desc',
  team: 'platform', category: null, tags: ['pdf'], visibility: 'PUBLIC' as const,
  latestVersion: '1.0.0', downloadsCount: 3, authorId: 'u-1',
}));

vi.mock('../api/elements', () => ({
  elements: {
    get: vi.fn().mockResolvedValue(element),
    versions: vi.fn().mockResolvedValue([
      { version: '1.0.0', status: 'PUBLISHED', changelog: 'initial', sizeBytes: 100, files: [] },
    ] as VersionResponse[]),
    downloadVersionUrl: vi.fn(() => 'http://test/download'),
    downloadFileUrl: vi.fn(),
    publishVersion: vi.fn(),
    create: vi.fn(),
    list: vi.fn(),
    remove: vi.fn().mockResolvedValue(undefined),
    removeVersion: vi.fn().mockResolvedValue(undefined),
  },
}));

vi.mock('../api/social', () => ({
  social: {
    info: vi.fn().mockResolvedValue({ avgRating: 4.5, ratingCount: 2, favorited: false }),
    reviews: vi.fn().mockResolvedValue([
      { author: 'Bob', rating: 5, text: 'great', createdAt: '2026-01-01T00:00:00Z' },
    ]),
    rate: vi.fn().mockResolvedValue(undefined),
    review: vi.fn().mockResolvedValue(undefined),
    setFavorite: vi.fn().mockResolvedValue(undefined),
  },
}));

const auth = vi.hoisted(() => {
  const make = () => ({
    authenticated: true, token: 't', displayName: 'A', userId: 'u-1',
    isAdmin: false, myTeamRoles: { platform: 'OWNER' } as Record<string, string>,
    teamRoleOf: (slug: string): string | null => (slug === 'platform' ? 'OWNER' : null),
    login: vi.fn(), logout: vi.fn(),
  });
  const obj: { current: ReturnType<typeof make>; reset: () => void } = {
    current: make(),
    reset() { obj.current = make(); },
  };
  return obj;
});

vi.mock('../auth/KeycloakProvider', () => ({
  useAuth: () => auth.current,
}));

beforeEach(() => {
  snackbar.showSuccess.mockClear();
  snackbar.showError.mockClear();
  auth.reset();
  vi.mocked(elements.remove).mockClear();
  vi.mocked(elements.removeVersion).mockClear();
  vi.mocked(elements.get).mockResolvedValue({ ...element });
});

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/elements/pdf-skill']}>
        <Routes>
          <Route path="/elements/:slug" element={<ElementPage />} />
          <Route path="/catalog" element={<div data-testid="catalog-page" />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

test('shows element info, versions and reviews', async () => {
  renderPage();
  expect(await screen.findByText('PDF Skill')).toBeInTheDocument();
  expect(await screen.findByText('1.0.0')).toBeInTheDocument();
  expect(await screen.findByText('great')).toBeInTheDocument();
});

test('favorite toggle calls setFavorite', async () => {
  const { social } = await import('../api/social');
  renderPage();
  const btn = await screen.findByRole('button', { name: 'favorite' });
  await userEvent.click(btn);
  await waitFor(() => expect(social.setFavorite).toHaveBeenCalledWith('pdf-skill', true));
});

test('hero install button copies skillhub install command', async () => {
  const writeText = vi.fn();
  Object.defineProperty(navigator, 'clipboard', {
    value: { writeText }, configurable: true,
  });
  renderPage();
  const btn = await screen.findByRole('button', { name: 'skillhub install pdf-skill' });
  await userEvent.click(btn);
  expect(writeText).toHaveBeenCalledWith('skillhub install pdf-skill');
  await waitFor(() => expect(snackbar.showSuccess).toHaveBeenCalledWith('Команда скопирована'));
});

test('version copy button copies versioned install command', async () => {
  const writeText = vi.fn();
  Object.defineProperty(navigator, 'clipboard', {
    value: { writeText }, configurable: true,
  });
  renderPage();
  const btn = await screen.findByRole('button', { name: 'Скопировать команду установки' });
  await userEvent.click(btn);
  expect(writeText).toHaveBeenCalledWith('skillhub install pdf-skill@1.0.0');
  await waitFor(() => expect(snackbar.showSuccess).toHaveBeenCalledWith('Команда скопирована'));
});

test('owner sees delete element button, confirms and is redirected to catalog', async () => {
  const { elements } = await import('../api/elements');
  renderPage();
  const btn = await screen.findByRole('button', { name: 'Удалить элемент' });
  await userEvent.click(btn);
  const confirm = await screen.findByRole('button', { name: 'Удалить' });
  await userEvent.click(confirm);
  await waitFor(() => expect(elements.remove).toHaveBeenCalledWith('pdf-skill'));
  expect(await screen.findByTestId('catalog-page')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Удалить элемент' })).not.toBeInTheDocument();
});

test('version delete calls removeVersion after confirm', async () => {
  const { elements } = await import('../api/elements');
  renderPage();
  const btn = await screen.findByRole('button', { name: 'Удалить версию 1.0.0' });
  await userEvent.click(btn);
  const confirm = await screen.findByRole('button', { name: 'Удалить' });
  await userEvent.click(confirm);
  await waitFor(() => expect(elements.removeVersion).toHaveBeenCalledWith('pdf-skill', '1.0.0'));
});

test('team MAINTAINER does not see element or version delete buttons', async () => {
  auth.current.teamRoleOf = (slug: string): string | null =>
    (slug === 'platform' ? 'MAINTAINER' : null);
  renderPage();
  expect(await screen.findByText('PDF Skill')).toBeInTheDocument();
  expect(await screen.findByText('1.0.0')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Удалить элемент' })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Удалить версию 1.0.0' })).not.toBeInTheDocument();
});

test('admin sees element delete button', async () => {
  auth.current.isAdmin = true;
  auth.current.teamRoleOf = () => null;
  renderPage();
  expect(await screen.findByRole('button', { name: 'Удалить элемент' })).toBeInTheDocument();
});

test('personal author sees element delete button', async () => {
  vi.mocked(elements.get).mockResolvedValue({ ...element, team: null, authorId: 'u-1' });
  renderPage();
  expect(await screen.findByRole('button', { name: 'Удалить элемент' })).toBeInTheDocument();
});

test('non-author non-admin on personal element does not see delete button', async () => {
  vi.mocked(elements.get).mockResolvedValue({ ...element, team: null, authorId: 'someone-else' });
  renderPage();
  expect(await screen.findByText('PDF Skill')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Удалить элемент' })).not.toBeInTheDocument();
});
