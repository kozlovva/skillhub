import { render, screen } from '@testing-library/react';
import PageHeader from './PageHeader';

test('renders title as level-1 heading', () => {
  render(<PageHeader title="Избранное" />);
  expect(screen.getByRole('heading', { level: 1, name: 'Избранное' })).toBeInTheDocument();
});

test('renders optional subtitle', () => {
  render(<PageHeader title="Команды" subtitle="Управление командами" />);
  expect(screen.getByText('Управление командами')).toBeInTheDocument();
});

test('renders without subtitle', () => {
  render(<PageHeader title="Избранное" />);
  expect(screen.queryByText('Управление командами')).not.toBeInTheDocument();
});
