import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import TeamsPage from './TeamsPage';
import { useAuth } from '../auth/KeycloakProvider';

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
  useAuth: vi.fn(),
}));

const mockUseAuth = vi.mocked(useAuth);

function setAuth(overrides: Partial<Record<string, unknown>> = {}) {
  mockUseAuth.mockReturnValue({
    authenticated: true,
    token: 't',
    displayName: 'Owner',
    isAdmin: false,
    myTeamRoles: { platform: 'OWNER' },
    teamRoleOf: (slug: string) => (slug === 'platform' ? 'OWNER' : null),
    login: vi.fn(),
    logout: vi.fn(),
    ...overrides,
  } as never);
}

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
  setAuth();
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
});

test('non-admin sees only own teams in the list', async () => {
  renderPage();
  expect(await screen.findByText('Platform')).toBeInTheDocument();
  expect(screen.queryByText('Other')).not.toBeInTheDocument();
});

test('search filters teams by name and slug', async () => {
  setAuth({ isAdmin: true, myTeamRoles: {}, teamRoleOf: () => null });
  const user = userEvent.setup();
  renderPage();
  await screen.findByText('Platform');
  await user.type(screen.getByPlaceholderText('Поиск команд'), 'plat');
  expect(screen.queryByText('Other')).not.toBeInTheDocument();
  expect(screen.getAllByText('Platform').length).toBeGreaterThan(0);
  await user.clear(screen.getByPlaceholderText('Поиск команд'));
  await user.type(screen.getByPlaceholderText('Поиск команд'), 'zzz');
  expect(await screen.findByText('Ничего не найдено')).toBeInTheDocument();
});

test('outsider with no teams sees empty state', async () => {
  setAuth({ myTeamRoles: {}, teamRoleOf: () => null });
  renderPage();
  expect(await screen.findByText('Вы не состоите ни в одной команде')).toBeInTheDocument();
  expect(screen.getByText('Выберите команду')).toBeInTheDocument();
});

test('role menu offers roles and calls changeRole', async () => {
  const user = userEvent.setup();
  patchMock.mockResolvedValue({});
  renderPage();
  await screen.findByText('Пётр Петров');
  await user.click(screen.getAllByRole('button', { name: 'Действия участника' })[1]);
  await user.click(await screen.findByRole('menuitem', { name: 'Редактор' }));
  expect(patchMock).toHaveBeenCalledWith('/api/teams/platform/members/u2', { role: 'MAINTAINER' });
});
