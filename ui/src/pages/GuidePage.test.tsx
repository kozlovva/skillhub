import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import GuidePage from './GuidePage';

function renderPage() {
  return render(
    <MemoryRouter>
      <GuidePage />
    </MemoryRouter>
  );
}

test('renders guide sections', () => {
  renderPage();
  expect(screen.getByRole('heading', { name: 'Инструкция' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Быстрый старт: установка скиллов' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Поиск' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Публикация' })).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'FAQ' })).toBeInTheDocument();
});

test('renders markdown as components, not raw text', () => {
  renderPage();
  expect(screen.getByRole('heading', { level: 1, name: 'Инструкция' })).toBeInTheDocument();
  expect(screen.getAllByText(/skillhub install/).length).toBeGreaterThan(0);
  expect(screen.getAllByText(/skillhub publish/).length).toBeGreaterThan(0);
});

test('renders command reference table (remark-gfm)', () => {
  renderPage();
  expect(screen.getByRole('table')).toBeInTheDocument();
  expect(screen.getByText('Команда')).toBeInTheDocument();
});
