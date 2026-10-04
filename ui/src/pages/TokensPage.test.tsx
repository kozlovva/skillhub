import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import TokensPage from './TokensPage';

const { postMock, getMock } = vi.hoisted(() => ({
  postMock: vi.fn().mockResolvedValue({
    data: { token: 'skh_newtoken123', name: 'cli' },
  }),
  getMock: vi.fn().mockResolvedValue({
    data: [{ name: 'cli', createdAt: '2026-01-01T00:00:00Z', lastUsedAt: null, expiresAt: null }],
  }),
}));

vi.mock('../api/client', () => ({
  api: { post: postMock, get: getMock },
  toApiError: (e: unknown) => ({
    status: 0,
    code: 'ERROR',
    message: e instanceof Error ? e.message : String(e),
    details: null,
  }),
}));

vi.mock('../auth/KeycloakProvider', () => ({
  useAuth: () => ({ authenticated: true, token: 't', displayName: 'A', login: vi.fn(), logout: vi.fn() }),
}));

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <TokensPage />
    </QueryClientProvider>
  );
}

test('lists tokens and creates new one showing raw token once', async () => {
  renderPage();
  expect(await screen.findByText('cli')).toBeInTheDocument();

  await userEvent.type(screen.getByLabelText('Имя токена'), 'new-cli');
  await userEvent.click(screen.getByRole('button', { name: 'Создать токен' }));

  await waitFor(() => expect(postMock).toHaveBeenCalled());
  expect(await screen.findByText(/skh_newtoken123/)).toBeInTheDocument();
});
