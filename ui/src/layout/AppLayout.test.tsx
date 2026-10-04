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
  expect(screen.getByRole('link', { name: 'Создать' })).toBeInTheDocument();
});

test('hides upload button when not authenticated', () => {
  authState.authenticated = false;
  renderLayout();
  expect(screen.queryByRole('link', { name: 'Создать' })).not.toBeInTheDocument();
  expect(screen.queryByText('Alice')).not.toBeInTheDocument();
});

test('shows favorites nav item when authenticated', () => {
  authState.authenticated = true;
  renderLayout();
  expect(screen.getByRole('link', { name: 'Избранное' })).toBeInTheDocument();
});

test('renders favorites as icon link in the right cluster, after menu', () => {
  authState.authenticated = true;
  renderLayout();
  const fav = screen.getByRole('link', { name: 'Избранное' });
  expect(fav.querySelector('svg')).toBeInTheDocument();
  const links = screen.getAllByRole('link');
  const guide = screen.getByRole('link', { name: 'Инструкция' });
  expect(links.indexOf(fav)).toBeGreaterThan(links.indexOf(guide));
});

test('hides favorites nav item when not authenticated', () => {
  authState.authenticated = false;
  renderLayout();
  expect(screen.queryByRole('link', { name: 'Избранное' })).not.toBeInTheDocument();
});

test('shows guide nav item for all users', () => {
  authState.authenticated = false;
  renderLayout();
  expect(screen.getByRole('link', { name: 'Инструкция' })).toBeInTheDocument();
});

test('renders guide nav item last in the menu', () => {
  authState.authenticated = true;
  renderLayout();
  const navLinks = screen
    .getAllByRole('link')
    .map((link) => link.textContent)
    .filter((label) =>
      ['Каталог', 'Команды', 'API-токены', 'Избранное', 'Категории', 'Инструкция'].includes(label ?? ''),
    );
  expect(navLinks[navLinks.length - 1]).toBe('Инструкция');
  expect(navLinks.indexOf('Инструкция')).toBeGreaterThan(navLinks.indexOf('Избранное'));
});
