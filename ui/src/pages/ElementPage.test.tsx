import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ElementPage from './ElementPage';
import type { VersionResponse } from '../types';

const element = vi.hoisted(() => ({
  slug: 'pdf-skill', type: 'SKILL' as const, name: 'PDF Skill', description: 'desc',
  team: 'platform', category: null, tags: ['pdf'], visibility: 'PUBLIC' as const,
  latestVersion: '1.0.0', downloadsCount: 3,
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

vi.mock('../auth/KeycloakProvider', () => ({
  useAuth: () => ({
    authenticated: true, token: 't', displayName: 'A',
    isAdmin: false, myTeamRoles: { platform: 'OWNER' },
    teamRoleOf: (slug: string) => (slug === 'platform' ? 'OWNER' : null),
    login: vi.fn(), logout: vi.fn(),
  }),
}));

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/elements/pdf-skill']}>
        <Routes>
          <Route path="/elements/:slug" element={<ElementPage />} />
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
  const btn = await screen.findByRole('button', { name: 'Команда установки' });
  await userEvent.click(btn);
  expect(writeText).toHaveBeenCalledWith('skillhub install pdf-skill');
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
});
