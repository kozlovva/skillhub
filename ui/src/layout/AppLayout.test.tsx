import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import AppLayout from './AppLayout';

vi.mock('../auth/KeycloakProvider', () => ({
  useAuth: () => ({ authenticated: true, displayName: 'Alice', login: vi.fn(), logout: vi.fn(), token: 'x' }),
}));

test('renders navigation with all menu items and user name', () => {
  render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/" element={<div />} />
        </Route>
      </Routes>
    </MemoryRouter>
  );
  expect(screen.getByText('SkillHub')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Каталог' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Команды' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'API-токены' })).toBeInTheDocument();
  expect(screen.getByText('Alice')).toBeInTheDocument();
});
