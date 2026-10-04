import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import TeamsPage from './TeamsPage';

const { getMock, patchMock, deleteMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  patchMock: vi.fn(),
  deleteMock: vi.fn(),
}));

vi.mock('../api/client', () => ({
  api: {
    get: getMock,
    patch: patchMock,
    delete: deleteMock,
    post: vi.fn(),
  },
  toApiError: (e: unknown) => ({
    status: 0, code: 'ERROR',
    message: e instanceof Error ? e.message : String(e), details: null,
  }),
}));

vi.mock('../auth/KeycloakProvider', () => ({
  useAuth: () => ({
    authenticated: true,
    token: 't',
    displayName: 'Owner',
    isAdmin: false,
    myTeamRoles: { platform: 'OWNER' },
    teamRoleOf: (slug: string) => (slug === 'platform' ? 'OWNER' : null),
    login: vi.fn(),
    logout: vi.fn(),
  }),
}));

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <TeamsPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

const teams = [
  { slug: 'platform', name: 'Platform' },
  { slug: 'other', name: 'Other' },
];

const roster = [
  { userId: 'u1', username: 'petrov', displayName: 'Пётр Петров', role: 'OWNER' },
  { userId: 'u2', username: 'ivanov', displayName: 'Иван Иванов', role: 'MEMBER' },
];

beforeEach(() => {
  getMock.mockImplementation((url: string) => {
    if (url === '/api/teams') {
      return Promise.resolve({ data: teams });
    }
    if (url === '/api/teams/platform/members') {
      return Promise.resolve({ data: roster });
    }
    return Promise.reject(new Error('unexpected ' + url));
  });
});

test('renders roster of selected team with role chips', async () => {
  renderPage();
  expect(await screen.findByText('Пётр Петров')).toBeInTheDocument();
  expect(screen.getByText('@petrov')).toBeInTheDocument();
  expect(screen.getAllByText('Владелец').length).toBeGreaterThan(0);
  expect(screen.getByText('@ivanov')).toBeInTheDocument();
  expect(screen.getAllByText('Участник').length).toBeGreaterThan(0);
});

test('outsider sees restricted notice instead of roster', async () => {
  const user = userEvent.setup();
  renderPage();
  await screen.findByText('Пётр Петров');
  await user.click(screen.getByRole('button', { name: /Other/ }));
  expect(await screen.findByText('Состав виден только участникам команды')).toBeInTheDocument();
  expect(getMock).not.toHaveBeenCalledWith('/api/teams/other/members');
});

test('role menu offers three roles and calls changeRole', async () => {
  const user = userEvent.setup();
  patchMock.mockResolvedValue({});
  renderPage();
  await screen.findByText('Пётр Петров');
  await user.click(screen.getAllByRole('button', { name: 'Действия участника' })[1]);
  await user.click(await screen.findByRole('menuitem', { name: 'Редактор' }));
  expect(patchMock).toHaveBeenCalledWith('/api/teams/platform/members/u2', { role: 'MAINTAINER' });
});
