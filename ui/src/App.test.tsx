import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import App from './App';

vi.mock('./auth/KeycloakProvider', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useAuth: () => ({ authenticated: true, displayName: 'Test', login: vi.fn(), logout: vi.fn(), token: null }),
}));

test('renders catalog page with search', () => {
  render(
    <MemoryRouter initialEntries={['/']}>
      <App />
    </MemoryRouter>
  );
  expect(screen.getByRole('heading', { name: /skillhub/i })).toBeInTheDocument();
});
