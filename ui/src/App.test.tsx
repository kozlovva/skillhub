import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import App from './App';

vi.mock('./auth/KeycloakProvider', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useAuth: () => ({ authenticated: true, displayName: 'Test', login: vi.fn(), logout: vi.fn(), token: null }),
}));

test('renders catalog page with search', () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>
  );
  expect(screen.getByRole('heading', { name: /skillhub/i })).toBeInTheDocument();
});
