import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import UploadPage from './UploadPage';
import { SnackbarProvider } from '../layout/SnackbarContext';

const { createMock, publishMock, meMock, categoriesMock } = vi.hoisted(() => ({
  createMock: vi.fn(),
  publishMock: vi.fn(),
  meMock: vi.fn(),
  categoriesMock: vi.fn(),
}));

vi.mock('../api/elements', () => ({
  elements: { create: createMock, publishVersion: publishMock },
}));

vi.mock('../api/me', () => ({
  me: { get: meMock },
}));

vi.mock('../api/categories', () => ({
  categories: { list: categoriesMock },
}));

vi.mock('../api/client', () => ({
  toApiError: (e: unknown) => ({
    status: (e as { status?: number }).status ?? 0,
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
      <MemoryRouter initialEntries={['/upload']}>
        <SnackbarProvider>
          <Routes>
            <Route path="/upload" element={<UploadPage />} />
            <Route path="/elements/:slug" element={<div>element-page</div>} />
          </Routes>
        </SnackbarProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

beforeEach(() => {
  createMock.mockReset().mockResolvedValue({ slug: 'test-element' });
  publishMock.mockReset().mockResolvedValue({ version: '1.0.0', status: 'PUBLISHED' });
  meMock.mockReset().mockResolvedValue({
    username: 'alice',
    admin: false,
    teams: [{ slug: 'core', name: 'Core Team', role: 'OWNER' }],
  });
  categoriesMock.mockReset().mockResolvedValue([
    { slug: 'dev-tools', name: 'Dev Tools', parent: null, icon: null },
  ]);
});

async function fillForm() {
  await userEvent.type(await screen.findByLabelText(/^Название/), 'Test Element');
  await userEvent.click(screen.getByLabelText(/^Тип/));
  await userEvent.click(await screen.findByRole('option', { name: 'SKILL' }));
  await userEvent.click(screen.getByLabelText(/^Команда/));
  await userEvent.click(await screen.findByRole('option', { name: 'Core Team' }));
  await userEvent.click(screen.getByLabelText(/^Видимость/));
  await userEvent.click(await screen.findByRole('option', { name: /PUBLIC — доступен всем/ }));
  await userEvent.upload(
    screen.getByTestId('version-file'),
    new File(['data'], 'element.zip', { type: 'application/zip' })
  );
}

test('accepts a file via drag and drop', async () => {
  renderPage();
  await screen.findByLabelText(/^Название/);
  fireEvent.drop(screen.getByText(/Перетащите файл сюда/), {
    dataTransfer: { files: [new File(['data'], 'dropped.zip', { type: 'application/zip' })] },
  });
  expect(await screen.findByText('dropped.zip')).toBeInTheDocument();
});

test('renders form and loads teams and categories', async () => {
  renderPage();
  expect(await screen.findByLabelText(/^Название/)).toBeInTheDocument();
  expect(meMock).toHaveBeenCalled();
  expect(categoriesMock).toHaveBeenCalled();
  await userEvent.click(screen.getByLabelText(/^Команда/));
  expect(await screen.findByRole('option', { name: 'Core Team' })).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText(/^Категория/));
  expect(await screen.findByRole('option', { name: 'Dev Tools' })).toBeInTheDocument();
});

test('auto-generates slug from name until slug is edited manually', async () => {
  renderPage();
  const slugField = await screen.findByLabelText(/^Slug/);
  await userEvent.type(screen.getByLabelText(/^Название/), 'Test Element');
  expect(slugField).toHaveValue('test-element');
  await userEvent.type(slugField, 'x');
  expect(slugField).toHaveValue('test-elementx');
  await userEvent.type(screen.getByLabelText(/^Название/), '!');
  expect(slugField).toHaveValue('test-elementx');
});

test('disables submit until required fields are filled', async () => {
  renderPage();
  await screen.findByLabelText(/^Название/);
  expect(screen.getByRole('button', { name: /Опубликовать/i })).toBeDisabled();
  expect(createMock).not.toHaveBeenCalled();
});

test('creates element, publishes version and navigates to element page', async () => {
  renderPage();
  await fillForm();
  await userEvent.click(screen.getByRole('button', { name: /Опубликовать/i }));

  await waitFor(() =>
    expect(createMock).toHaveBeenCalledWith({
      slug: 'test-element',
      type: 'SKILL',
      name: 'Test Element',
      description: undefined,
      team: 'core',
      category: undefined,
      tags: [],
      visibility: 'PUBLIC',
    })
  );
  expect(publishMock).toHaveBeenCalledTimes(1);
  expect(publishMock.mock.calls[0][0]).toBe('test-element');
  expect(publishMock.mock.calls[0][1]).toBeInstanceOf(File);
  expect(await screen.findByText('element-page')).toBeInTheDocument();
});

test('shows slug field error on 409 conflict', async () => {
  createMock.mockRejectedValue({ status: 409 });
  renderPage();
  await fillForm();
  await userEvent.click(screen.getByRole('button', { name: /Опубликовать/i }));

  expect(await screen.findByText(/уже существует/i)).toBeInTheDocument();
  expect(publishMock).not.toHaveBeenCalled();
});

test('shows snackbar with api message on non-409 publish error', async () => {
  publishMock.mockRejectedValue(Object.assign(new Error('Некорректный файл'), { status: 400 }));
  renderPage();
  await fillForm();
  await userEvent.click(screen.getByRole('button', { name: /Опубликовать/i }));

  expect(await screen.findByText(/Некорректный файл/)).toBeInTheDocument();
  expect(screen.queryByText(/уже существует/i)).not.toBeInTheDocument();
});

test('team can be cleared after selection', async () => {
  renderPage();
  await userEvent.click(await screen.findByLabelText('Команда'));
  await userEvent.click(await screen.findByRole('option', { name: 'Core Team' }));
  await userEvent.click(screen.getByLabelText('Команда'));
  await userEvent.click(await screen.findByRole('option', { name: 'Без команды' }));
  await userEvent.click(screen.getByLabelText(/^Видимость/));
  expect(await screen.findByRole('option', { name: /TEAM — только команде/ }))
    .toHaveAttribute('aria-disabled', 'true');
  await userEvent.click(screen.getByRole('option', { name: /PUBLIC — доступен всем/ }));
  await userEvent.type(await screen.findByLabelText(/^Название/), 'Solo');
  await userEvent.click(screen.getByLabelText(/^Тип/));
  await userEvent.click(await screen.findByRole('option', { name: 'SCRIPT' }));
  await userEvent.upload(
    screen.getByTestId('version-file'),
    new File(['data'], 'element.zip', { type: 'application/zip' })
  );
  await userEvent.click(screen.getByRole('button', { name: /Опубликовать/i }));
  await waitFor(() => expect(createMock).toHaveBeenCalled());
  expect(createMock.mock.calls[0][0].team).toBeUndefined();
  expect(createMock.mock.calls[0][0].visibility).toBe('PUBLIC');
});

test('TEAM visibility is disabled without a team', async () => {
  renderPage();
  await userEvent.click(await screen.findByLabelText('Видимость'));
  expect(await screen.findByRole('option', { name: /TEAM — только команде/ }))
    .toHaveAttribute('aria-disabled', 'true');
});

test('selecting a team enables TEAM visibility', async () => {
  renderPage();
  await userEvent.click(await screen.findByLabelText('Команда'));
  await userEvent.click(await screen.findByRole('option', { name: 'Core Team' }));
  await userEvent.click(screen.getByLabelText(/^Видимость/));
  expect(await screen.findByRole('option', { name: /TEAM — только команде/ }))
    .not.toHaveAttribute('aria-disabled', 'true');
});

test('publish 409 shows publish-step snackbar and no slug error', async () => {
  publishMock.mockRejectedValue({ status: 409 });
  renderPage();
  await fillForm();
  await userEvent.click(screen.getByRole('button', { name: /Опубликовать/i }));

  expect(
    await screen.findByText(/Элемент создан, но не удалось загрузить версию/)
  ).toBeInTheDocument();
  expect(screen.queryByText(/уже существует/i)).not.toBeInTheDocument();
});
