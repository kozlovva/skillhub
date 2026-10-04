import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import AppLayout from './AppLayout';

const { authState } = vi.hoisted(() => ({
  authState: { authenticated: true, displayName: 'Alice' as string | null },
}));

vi.mock('../auth/KeycloakProvider', () => ({
  useAuth: () => ({ token: 'x', isAdmin: false, login: vi.fn(), logout: vi.fn(), ...authState }),
}));

function renderLayout() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/" element={<div />} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
}

test('renders navigation with all menu items and user name', () => {
  authState.authenticated = true;
  renderLayout();
  expect(screen.getByText('SkillHub')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Каталог' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Команды' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'API-токены' })).toBeInTheDocument();
  expect(screen.getByText('Alice')).toBeInTheDocument();
});

test('shows upload button when authenticated', () => {
  authState.authenticated = true;
  renderLayout();
  expect(screen.getByRole('link', { name: 'Загрузить элемент' })).toBeInTheDocument();
});

test('hides upload button when not authenticated', () => {
  authState.authenticated = false;
  renderLayout();
  expect(screen.queryByRole('link', { name: 'Загрузить элемент' })).not.toBeInTheDocument();
  expect(screen.queryByText('Alice')).not.toBeInTheDocument();
});
