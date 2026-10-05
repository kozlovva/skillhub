import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { zipSync, strToU8 } from 'fflate';
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

const VALID_MANIFEST = {
  name: 'Test Element',
  version: '1.0.0',
  description: 'Из манифеста',
  type: 'SKILL',
};

function zipFile(entries: Record<string, string>): File {
  const files: Record<string, Uint8Array> = {};
  for (const [k, v] of Object.entries(entries)) files[k] = strToU8(v);
  return new File([zipSync(files)], 'element.zip', { type: 'application/zip' });
}

function makeZip(manifest?: unknown, extra: Record<string, string> = {}): File {
  return zipFile(manifest == null ? extra : { 'manifest.json': JSON.stringify(manifest), ...extra });
}

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

async function passStep1(file: File) {
  await userEvent.upload(screen.getByTestId('version-file'), file);
  await screen.findByTestId('archive-summary');
  await userEvent.click(screen.getByRole('button', { name: 'Далее' }));
  await screen.findByLabelText(/^Команда/);
}

async function fillMetadata() {
  await userEvent.click(screen.getByLabelText(/^Команда/));
  await userEvent.click(await screen.findByRole('option', { name: 'Core Team' }));
  await userEvent.click(screen.getByLabelText(/^Видимость/));
  await userEvent.click(await screen.findByRole('option', { name: /PUBLIC — доступен всем/ }));
}

test('step 1: valid archive shows summary and enables next', async () => {
  renderPage();
  await screen.findByText('Требования к архиву');
  expect(screen.getByRole('button', { name: 'Далее' })).toBeDisabled();
  await userEvent.upload(
    screen.getByTestId('version-file'),
    makeZip(VALID_MANIFEST, { 'SKILL.md': '# hi' })
  );
  const summary = await screen.findByTestId('archive-summary');
  expect(summary).toHaveTextContent('Test Element');
  expect(summary).toHaveTextContent('1.0.0');
  expect(screen.getByRole('button', { name: 'Далее' })).toBeEnabled();
});

test('step 1: archive without manifest.json shows error and blocks step 2', async () => {
  renderPage();
  await screen.findByText('Требования к архиву');
  await userEvent.upload(
    screen.getByTestId('version-file'),
    makeZip(undefined, { 'SKILL.md': '# hi' })
  );
  expect(await screen.findByTestId('archive-error')).toHaveTextContent(/manifest\.json/i);
  expect(screen.getByRole('button', { name: 'Далее' })).toBeDisabled();
});

test('step 1: manifest validation errors block step 2', async () => {
  const cases: { manifest?: unknown; raw?: string; error: RegExp }[] = [
    { manifest: { version: '1.0.0' }, error: /поле name обязательно/ },
    { manifest: { name: 'x' }, error: /поле version обязательно/ },
    { manifest: { name: 'x', version: '1.0' }, error: /semver/ },
    { raw: '{not json', error: /корректным JSON/ },
  ];
  for (const c of cases) {
    const view = renderPage();
    const file = 'raw' in c ? zipFile({ 'manifest.json': c.raw! }) : makeZip(c.manifest);
    await userEvent.upload(screen.getByTestId('version-file'), file);
    expect(await screen.findByTestId('archive-error')).toHaveTextContent(c.error);
    view.unmount();
  }
});

test('step 1: non-zip file shows error and blocks step 2', async () => {
  renderPage();
  await userEvent.upload(
    screen.getByTestId('version-file'),
    new File(['hello'], 'broken.zip', { type: 'application/zip' })
  );
  expect(await screen.findByTestId('archive-error')).toHaveTextContent(/ZIP-архивом/);
  expect(screen.getByRole('button', { name: 'Далее' })).toBeDisabled();
});

test('step 1: valid archive replaces dropzone with summary and replace button', async () => {
  renderPage();
  await screen.findByText('Требования к архиву');
  await userEvent.upload(
    screen.getByTestId('version-file'),
    makeZip(VALID_MANIFEST, { 'SKILL.md': '# hi' })
  );
  await screen.findByTestId('archive-summary');
  expect(screen.queryByText(/Перетащите файл сюда/)).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Заменить архив' }));
  await userEvent.upload(
    screen.getByTestId('version-file'),
    makeZip({ name: 'Other', version: '2.0.0', description: 'Другое', type: 'SCRIPT' })
  );
  expect(await screen.findByTestId('archive-summary')).toHaveTextContent('Other');
});

test('accepts a file via drag and drop', async () => {
  renderPage();
  await screen.findByText(/Перетащите файл сюда/);
  fireEvent.drop(screen.getByText(/Перетащите файл сюда/), {
    dataTransfer: { files: [makeZip(VALID_MANIFEST)] },
  });
  expect(await screen.findByTestId('archive-summary')).toHaveTextContent('element.zip');
});

test('step 2: prefills name, slug, type and description from manifest', async () => {
  renderPage();
  await passStep1(makeZip(VALID_MANIFEST, { 'SKILL.md': '# hi' }));
  expect(screen.getByLabelText(/^Название/)).toHaveValue('Test Element');
  expect(screen.getByLabelText(/^Slug/)).toHaveValue('test-element');
  expect(screen.getByLabelText(/^Тип/)).toHaveTextContent('SKILL');
  expect(screen.getByLabelText(/Описание/)).toHaveValue('Из манифеста');
  expect(screen.getByTestId('archive-entries')).toHaveTextContent('SKILL.md');
});

test('step 2: publish stays disabled until type is chosen for unknown manifest type', async () => {
  renderPage();
  await passStep1(makeZip({ ...VALID_MANIFEST, type: 'WIDGET' }));
  const publish = screen.getByRole('button', { name: /Опубликовать/i });
  expect(publish).toBeDisabled();
  await userEvent.click(screen.getByLabelText(/^Тип/));
  await userEvent.click(await screen.findByRole('option', { name: 'SCRIPT' }));
  expect(screen.getByRole('button', { name: /Опубликовать/i })).toBeEnabled();
});

test('creates element, publishes version and navigates to element page', async () => {
  renderPage();
  await passStep1(makeZip(VALID_MANIFEST));
  await fillMetadata();
  await userEvent.click(screen.getByRole('button', { name: /Опубликовать/i }));

  await waitFor(() =>
    expect(createMock).toHaveBeenCalledWith({
      slug: 'test-element',
      type: 'SKILL',
      name: 'Test Element',
      description: 'Из манифеста',
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
  await passStep1(makeZip(VALID_MANIFEST));
  await fillMetadata();
  await userEvent.click(screen.getByRole('button', { name: /Опубликовать/i }));

  expect(await screen.findByText(/уже существует/i)).toBeInTheDocument();
  expect(publishMock).not.toHaveBeenCalled();
});

test('shows snackbar with api message on non-409 publish error', async () => {
  publishMock.mockRejectedValue(Object.assign(new Error('Некорректный файл'), { status: 400 }));
  renderPage();
  await passStep1(makeZip(VALID_MANIFEST));
  await fillMetadata();
  await userEvent.click(screen.getByRole('button', { name: /Опубликовать/i }));

  expect(await screen.findByText(/Некорректный файл/)).toBeInTheDocument();
});

test('publish 409 shows publish-step snackbar and no slug error', async () => {
  publishMock.mockRejectedValue({ status: 409 });
  renderPage();
  await passStep1(makeZip(VALID_MANIFEST));
  await fillMetadata();
  await userEvent.click(screen.getByRole('button', { name: /Опубликовать/i }));

  expect(
    await screen.findByText(/Элемент создан, но не удалось загрузить версию/)
  ).toBeInTheDocument();
  expect(screen.queryByText(/уже существует/i)).not.toBeInTheDocument();
});

test('slug auto-generates from manifest name until edited manually', async () => {
  renderPage();
  await passStep1(makeZip(VALID_MANIFEST));
  const slugField = screen.getByLabelText(/^Slug/);
  expect(slugField).toHaveValue('test-element');
  await userEvent.type(slugField, 'x');
  await userEvent.type(screen.getByLabelText(/^Название/), '!');
  expect(slugField).toHaveValue('test-elementx');
});

test('team can be cleared after selection', async () => {
  renderPage();
  await passStep1(makeZip(VALID_MANIFEST));
  await userEvent.click(screen.getByLabelText('Команда'));
  await userEvent.click(await screen.findByRole('option', { name: 'Core Team' }));
  await userEvent.click(screen.getByLabelText('Команда'));
  await userEvent.click(await screen.findByRole('option', { name: 'Без команды' }));
  await userEvent.click(screen.getByLabelText(/^Видимость/));
  await userEvent.click(await screen.findByRole('option', { name: /PUBLIC — доступен всем/ }));
  await userEvent.click(screen.getByRole('button', { name: /Опубликовать/i }));
  await waitFor(() => expect(createMock).toHaveBeenCalled());
  expect(createMock.mock.calls[0][0].team).toBeUndefined();
  expect(createMock.mock.calls[0][0].visibility).toBe('PUBLIC');
});

test('TEAM visibility is disabled without a team', async () => {
  renderPage();
  await passStep1(makeZip(VALID_MANIFEST));
  await userEvent.click(screen.getByLabelText(/^Видимость/));
  expect(await screen.findByRole('option', { name: /TEAM — только команде/ }))
    .toHaveAttribute('aria-disabled', 'true');
});

test('selecting a team enables TEAM visibility', async () => {
  renderPage();
  await passStep1(makeZip(VALID_MANIFEST));
  await userEvent.click(screen.getByLabelText('Команда'));
  await userEvent.click(await screen.findByRole('option', { name: 'Core Team' }));
  await userEvent.click(screen.getByLabelText(/^Видимость/));
  expect(await screen.findByRole('option', { name: /TEAM — только команде/ }))
    .not.toHaveAttribute('aria-disabled', 'true');
});

test('rapidly replacing the file applies only the last selected archive', async () => {
  renderPage();
  await screen.findByText('Требования к архиву');
  const input = screen.getByTestId('version-file');
  await userEvent.upload(
    input,
    makeZip({ name: 'Alpha', version: '1.0.0', description: '', type: 'SKILL' })
  );
  fireEvent.change(input, {
    target: {
      files: [makeZip({ name: 'Beta', version: '2.0.0', description: '', type: 'SKILL' })],
    },
  });
  const summary = await screen.findByTestId('archive-summary');
  await waitFor(() => expect(summary).toHaveTextContent('Beta'));
  expect(summary).toHaveTextContent('2.0.0');
  expect(screen.queryByText('Alpha')).toBeNull();
});

test('replacing the file overwrites only untouched fields', async () => {
  renderPage();
  await passStep1(makeZip(VALID_MANIFEST));
  const nameField = screen.getByLabelText(/^Название/);
  await userEvent.clear(nameField);
  await userEvent.type(nameField, 'Ручное имя');
  await userEvent.click(screen.getByRole('button', { name: 'Назад' }));
  await userEvent.upload(
    screen.getByTestId('version-file'),
    makeZip({ name: 'Other', version: '2.0.0', description: 'Другое', type: 'SCRIPT' })
  );
  await screen.findByTestId('archive-summary');
  await userEvent.click(screen.getByRole('button', { name: 'Далее' }));

  expect(screen.getByLabelText(/^Название/)).toHaveValue('Ручное имя');
  expect(screen.getByLabelText(/^Тип/)).toHaveTextContent('SCRIPT');
  expect(screen.getByLabelText(/Описание/)).toHaveValue('Другое');
  expect(screen.getByLabelText(/^Slug/)).toHaveValue('other');
});
