# Поиск команд и видимость списка по ролям — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Клиентский поиск по списку команд + не-админ видит в списке только свои команды; TeamsPage декомпозирована на `TeamListPanel` и `TeamMembersPanel`.

**Architecture:** Чисто фронтендовая задача: из `TeamsPage.tsx` извлекаются две панели — левая (`TeamListPanel`: форма создания, поиск, список, пустые состояния) и правая (`TeamMembersPanel`: состав, меню действий, confirm-диалог, добавление участника, смонтированная с `key={team.slug}` для сброса состояния при смене команды). Страница остаётся оркестратором: teams-запрос, фильтрация видимости по `myTeamRoles`/`isAdmin`, состояние выбора. Бэкенд не меняется.

**Tech Stack:** React 18 + MUI v6 + TanStack Query v5 + Vitest + Testing Library.

## Global Constraints

- Спека: `docs/superpowers/specs/2026-10-04-teams-search-and-visibility-design.md`
- Не-админ видит в списке только свои команды (любая роль из `myTeamRoles`); нет своих → «Вы не состоите ни в одной команде»; админ — все
- Поиск: TextField с иконкой, регистронезависимый `includes` по `name` и `slug`, мгновенный (без debounce); пустой результат → «Ничего не найдено»
- Выбранная команда вне фильтра не сбрасывается; состав виден по-прежнему только участникам/админам (заглушка «Состав виден только участникам команды» остаётся защитной веткой)
- No code comments; conventional lowercase commits
- Команды (в `ui/`): `npm test` (51 тест сейчас), `npm run build`

---

### Task 1: TeamListPanel + TeamMembersPanel + переработка страницы

**Files:**
- Create: `ui/src/components/TeamListPanel.tsx`
- Create: `ui/src/components/TeamMembersPanel.tsx`
- Modify: `ui/src/pages/TeamsPage.tsx` (полная переработка в оркестратор)
- Test: `ui/src/pages/TeamsPage.test.tsx` (переписать)

**Interfaces:**
- Consumes: `teamsApi.list/members/searchCandidates/addMember/changeRole/removeMember/create` (существующие), `useAuth()` → `{ authenticated, isAdmin, myTeamRoles, teamRoleOf }`, `TeamResponse`/`MemberCandidate`/`TeamMemberResponse` из `../types`.
- Produces:
  - `TeamListPanel({ teams, selected, onSelect, isAdmin, authenticated })` — `teams` уже отфильтрован по видимости
  - `TeamMembersPanel({ team, isMember, canManage, ownRoleLabel, isAdmin })` + именованный экспорт `ROLE_LABELS: Record<string, string>`

- [ ] **Step 1: Переписать тесты (RED)**

Заменить `ui/src/pages/TeamsPage.test.tsx` целиком:

```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import TeamsPage from './TeamsPage';
import { useAuth } from '../auth/KeycloakProvider';

const { getMock, patchMock, deleteMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  patchMock: vi.fn(),
  deleteMock: vi.fn(),
}));

vi.mock('../api/client', () => ({
  api: {
    get: getMock,
    patch: patchMock,
    delete: deleteMock,
    post: vi.fn(),
  },
  toApiError: (e: unknown) => ({
    status: 0, code: 'ERROR',
    message: e instanceof Error ? e.message : String(e), details: null,
  }),
}));

vi.mock('../auth/KeycloakProvider', () => ({
  useAuth: vi.fn(),
}));

const mockUseAuth = vi.mocked(useAuth);

function setAuth(overrides: Partial<Record<string, unknown>> = {}) {
  mockUseAuth.mockReturnValue({
    authenticated: true,
    token: 't',
    displayName: 'Owner',
    isAdmin: false,
    myTeamRoles: { platform: 'OWNER' },
    teamRoleOf: (slug: string) => (slug === 'platform' ? 'OWNER' : null),
    login: vi.fn(),
    logout: vi.fn(),
    ...overrides,
  } as never);
}

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <TeamsPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

const teams = [
  { slug: 'platform', name: 'Platform' },
  { slug: 'other', name: 'Other' },
];

const roster = [
  { userId: 'u1', username: 'petrov', displayName: 'Пётр Петров', role: 'OWNER' },
  { userId: 'u2', username: 'ivanov', displayName: 'Иван Иванов', role: 'MEMBER' },
];

beforeEach(() => {
  setAuth();
  getMock.mockImplementation((url: string) => {
    if (url === '/api/teams') {
      return Promise.resolve({ data: teams });
    }
    if (url === '/api/teams/platform/members') {
      return Promise.resolve({ data: roster });
    }
    return Promise.reject(new Error('unexpected ' + url));
  });
});

test('renders roster of selected team with role chips', async () => {
  renderPage();
  expect(await screen.findByText('Пётр Петров')).toBeInTheDocument();
  expect(screen.getByText('@petrov')).toBeInTheDocument();
  expect(screen.getAllByText('Владелец').length).toBeGreaterThan(0);
  expect(screen.getByText('@ivanov')).toBeInTheDocument();
});

test('non-admin sees only own teams in the list', async () => {
  renderPage();
  expect(await screen.findByText('Platform')).toBeInTheDocument();
  expect(screen.queryByText('Other')).not.toBeInTheDocument();
});

test('search filters teams by name and slug', async () => {
  setAuth({ isAdmin: true, myTeamRoles: {}, teamRoleOf: () => null });
  const user = userEvent.setup();
  renderPage();
  await screen.findByText('Platform');
  await user.type(screen.getByPlaceholderText('Поиск команд'), 'plat');
  expect(screen.queryByText('Other')).not.toBeInTheDocument();
  expect(screen.getByText('Platform')).toBeInTheDocument();
  await user.clear(screen.getByPlaceholderText('Поиск команд'));
  await user.type(screen.getByPlaceholderText('Поиск команд'), 'zzz');
  expect(await screen.findByText('Ничего не найдено')).toBeInTheDocument();
});

test('outsider with no teams sees empty state', async () => {
  setAuth({ myTeamRoles: {}, teamRoleOf: () => null });
  renderPage();
  expect(await screen.findByText('Вы не состоите ни в одной команде')).toBeInTheDocument();
  expect(screen.getByText('Выберите команду')).toBeInTheDocument();
});

test('role menu offers roles and calls changeRole', async () => {
  const user = userEvent.setup();
  patchMock.mockResolvedValue({});
  renderPage();
  await screen.findByText('Пётр Петров');
  await user.click(screen.getAllByRole('button', { name: 'Действия участника' })[1]);
  await user.click(await screen.findByRole('menuitem', { name: 'Редактор' }));
  expect(patchMock).toHaveBeenCalledWith('/api/teams/platform/members/u2', { role: 'MAINTAINER' });
});
```

Примечание: мок `useAuth` — `vi.fn()`; `setAuth` настраивает его под тест. Если реальный `AuthContextValue` требует больше полей — дополнить `setAuth` базой, не менять страницу (сверить с `ui/src/auth/KeycloakProvider.tsx`).

Run: `cd ui; npm test -- TeamsPage`
Expected: FAIL (нет поиска, не-админ видит все команды, нет текста «Вы не состоите ни в одной команде»).

- [ ] **Step 2: TeamListPanel**

Создать `ui/src/components/TeamListPanel.tsx`:

```tsx
import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Typography, Paper, List, ListItemButton, ListItemAvatar, Avatar, ListItemText,
  TextField, Button, Stack, InputAdornment,
} from '@mui/material';
import GroupsIcon from '@mui/icons-material/Groups';
import SearchIcon from '@mui/icons-material/Search';
import AddBusinessIcon from '@mui/icons-material/AddBusiness';
import { teams as teamsApi } from '../api/teams';
import { toApiError } from '../api/client';
import type { TeamResponse } from '../types';
import { useSnackbar } from '../layout/SnackbarContext';

interface TeamListPanelProps {
  teams: TeamResponse[];
  selected: string | null;
  onSelect: (slug: string) => void;
  isAdmin: boolean;
  authenticated: boolean;
}

export default function TeamListPanel({
  teams, selected, onSelect, isAdmin, authenticated,
}: TeamListPanelProps) {
  const qc = useQueryClient();
  const { showError, showSuccess } = useSnackbar();
  const [slug, setSlug] = useState('');
  const [name, setName] = useState('');
  const [search, setSearch] = useState('');

  const createMutation = useMutation({
    mutationFn: () => teamsApi.create({ slug, name }),
    onSuccess: () => {
      showSuccess('Команда создана');
      setSlug(''); setName('');
      qc.invalidateQueries({ queryKey: ['teams'] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  const q = search.trim().toLowerCase();
  const filtered = q
    ? teams.filter((t) =>
        t.name.toLowerCase().includes(q) || t.slug.toLowerCase().includes(q))
    : teams;

  return (
    <Paper sx={{ p: 3, width: { xs: '100%', md: 320 }, flexShrink: 0 }}>
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
        <GroupsIcon sx={{ color: 'primary.main' }} />
        <Typography variant="h6">Команды ({teams.length})</Typography>
      </Stack>
      {isAdmin && (
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mb: 2 }}>
          <TextField label="slug" value={slug}
            onChange={(e) => setSlug(e.target.value)} sx={{ width: 120 }} size="small" />
          <TextField label="Название" value={name}
            onChange={(e) => setName(e.target.value)} sx={{ width: 130 }} size="small" />
          <Button variant="contained" startIcon={<AddBusinessIcon />} onClick={() => createMutation.mutate()}
            disabled={!slug.trim() || !name.trim()}>
            Создать
          </Button>
        </Stack>
      )}
      {teams.length === 0 && (
        <Typography color="text.secondary">
          {isAdmin ? 'Команд пока нет' : 'Вы не состоите ни в одной команде'}
        </Typography>
      )}
      {teams.length > 0 && (
        <TextField
          fullWidth
          size="small"
          placeholder="Поиск команд"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          sx={{ mb: 2 }}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" />
              </InputAdornment>
            ),
          }}
        />
      )}
      {teams.length > 0 && filtered.length === 0 && (
        <Typography color="text.secondary">Ничего не найдено</Typography>
      )}
      {filtered.length > 0 && (
        <List disablePadding>
          {filtered.map((t) => (
            <ListItemButton key={t.slug} selected={t.slug === selected}
              onClick={() => onSelect(t.slug)}
              sx={{ borderRadius: 1, mb: 0.5 }}>
              <ListItemAvatar>
                <Avatar sx={{ bgcolor: 'rgba(29, 94, 89, 0.12)', color: 'primary.main', fontFamily: '"Onest", sans-serif' }}>
                  {t.name.charAt(0).toUpperCase()}
                </Avatar>
              </ListItemAvatar>
              <ListItemText primary={t.name} secondary={t.slug} />
            </ListItemButton>
          ))}
        </List>
      )}
    </Paper>
  );
}
```

Примечание: если проект на MUI v6.4+, вместо `InputProps` использовать `slotProps={{ input: { startAdornment: ... } }}` — сверить с тем, как другие страницы рендерят TextField с иконками; главное — без предупреждений в тестах.

- [ ] **Step 3: TeamMembersPanel**

Создать `ui/src/components/TeamMembersPanel.tsx` — перенести правую карточку, меню и диалог из текущей `TeamsPage.tsx` (код ниже — полная замена логики правой панели; запросы ключуются от `team.slug`):

```tsx
import { useEffect, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Typography, Paper, List, ListItem, ListItemAvatar, Avatar, ListItemText,
  TextField, Button, MenuItem, Stack, Divider, Autocomplete, Chip, IconButton,
  Menu, Tooltip, Dialog, DialogTitle, DialogContentText, DialogActions,
} from '@mui/material';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import LockIcon from '@mui/icons-material/Lock';
import { teams as teamsApi } from '../api/teams';
import { toApiError } from '../api/client';
import type { MemberCandidate, TeamMemberResponse, TeamResponse } from '../types';
import { useSnackbar } from '../layout/SnackbarContext';

export const ROLE_LABELS: Record<string, string> = {
  OWNER: 'Владелец',
  MAINTAINER: 'Редактор',
  MEMBER: 'Участник',
};

function roleChipProps(role: string): { color?: 'primary' | 'secondary'; variant?: 'filled' | 'outlined' } {
  if (role === 'OWNER') return { color: 'primary' };
  if (role === 'MAINTAINER') return { color: 'secondary' };
  return { variant: 'outlined' };
}

interface TeamMembersPanelProps {
  team: TeamResponse;
  isMember: boolean;
  canManage: boolean;
  ownRoleLabel: string | null;
  isAdmin: boolean;
}

export default function TeamMembersPanel({
  team, isMember, canManage, ownRoleLabel, isAdmin,
}: TeamMembersPanelProps) {
  const qc = useQueryClient();
  const { showError, showSuccess } = useSnackbar();
  const [memberUser, setMemberUser] = useState<MemberCandidate | null>(null);
  const [memberQuery, setMemberQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [memberRole, setMemberRole] = useState('MEMBER');
  const [menuFor, setMenuFor] = useState<TeamMemberResponse | null>(null);
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null);
  const [removeTarget, setRemoveTarget] = useState<TeamMemberResponse | null>(null);

  useEffect(() => {
    const t = setTimeout(() => setDebouncedQuery(memberQuery.trim()), 300);
    return () => clearTimeout(t);
  }, [memberQuery]);

  const { data: members } = useQuery({
    queryKey: ['team-members', team.slug],
    queryFn: () => teamsApi.members(team.slug),
    enabled: isMember,
  });

  const { data: candidates } = useQuery({
    queryKey: ['member-candidates', team.slug, debouncedQuery],
    queryFn: () => teamsApi.searchCandidates(team.slug, debouncedQuery),
    enabled: canManage && debouncedQuery.length >= 2,
  });

  const owners = (members ?? []).filter((m) => m.role === 'OWNER');
  const lastOwner = owners.length === 1 ? owners[0].userId : null;

  const addMemberMutation = useMutation({
    mutationFn: () => teamsApi.addMember(team.slug, memberUser!.userId, memberRole),
    onSuccess: () => {
      showSuccess('Участник добавлен');
      setMemberUser(null);
      setMemberQuery('');
      qc.invalidateQueries({ queryKey: ['team-members', team.slug] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  const changeRoleMutation = useMutation({
    mutationFn: (v: { userId: string; role: string }) =>
      teamsApi.changeRole(team.slug, v.userId, v.role),
    onSuccess: () => {
      showSuccess('Роль изменена');
      setMenuFor(null);
      qc.invalidateQueries({ queryKey: ['team-members', team.slug] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  const removeMemberMutation = useMutation({
    mutationFn: (userId: string) => teamsApi.removeMember(team.slug, userId),
    onSuccess: () => {
      showSuccess('Участник исключён');
      setRemoveTarget(null);
      qc.invalidateQueries({ queryKey: ['team-members', team.slug] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  const roster = members ?? [];

  return (
    <Paper sx={{ p: 3, flex: 1, width: '100%' }} data-testid="team-detail">
      {!isMember && (
        <Stack spacing={1} alignItems="flex-start">
          <Typography variant="h6">{team.name}</Typography>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ color: 'text.secondary' }}>
            <LockIcon fontSize="small" />
            <Typography>Состав виден только участникам команды</Typography>
          </Stack>
        </Stack>
      )}
      {isMember && (
        <>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }} flexWrap="wrap" useFlexGap>
            <Typography variant="h6">{team.name}</Typography>
            <Typography variant="body2" color="text.secondary">{team.slug}</Typography>
            {ownRoleLabel && <Chip label={ownRoleLabel} size="small" />}
          </Stack>
          <List disablePadding>
            {roster.map((m) => (
              <ListItem key={m.userId} disableGutters sx={{ py: 0.75 }}>
                <ListItemAvatar>
                  <Avatar sx={{ bgcolor: 'rgba(29, 94, 89, 0.12)', color: 'primary.main', fontFamily: '"Onest", sans-serif' }}>
                    {m.displayName.charAt(0).toUpperCase()}
                  </Avatar>
                </ListItemAvatar>
                <ListItemText
                  primary={m.displayName}
                  secondary={`@${m.username}`}
                />
                <Chip {...roleChipProps(m.role)} label={ROLE_LABELS[m.role]} size="small" sx={{ mr: 1 }} />
                {canManage && (
                  <Tooltip title={m.userId === lastOwner ? 'Последнего владельца нельзя убрать' : ''}>
                    <span>
                      <IconButton
                        aria-label="Действия участника"
                        onClick={(e) => { setMenuFor(m); setMenuAnchor(e.currentTarget); }}
                      >
                        <MoreVertIcon />
                      </IconButton>
                    </span>
                  </Tooltip>
                )}
              </ListItem>
            ))}
          </List>
          {canManage && (
            <>
              <Divider sx={{ my: 2 }} />
              <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
                <PersonAddIcon sx={{ color: 'primary.main' }} />
                <Typography variant="h6">Добавить участника</Typography>
              </Stack>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Autocomplete
                  sx={{ width: 260 }}
                  options={candidates ?? []}
                  value={memberUser}
                  onChange={(_, v) => setMemberUser(v)}
                  onInputChange={(_, v) => setMemberQuery(v)}
                  getOptionLabel={(o) => `${o.displayName} (${o.username})`}
                  isOptionEqualToValue={(o, v) => o.userId === v.userId}
                  filterOptions={(o) => o}
                  freeSolo={false}
                  renderInput={(params) => (
                    <TextField {...params} label="Пользователь"
                      placeholder="Начните вводить имя или логин" />
                  )}
                />
                <TextField select label="Роль" value={memberRole}
                  onChange={(e) => setMemberRole(e.target.value)} sx={{ width: 140 }}>
                  <MenuItem value="OWNER">Владелец</MenuItem>
                  <MenuItem value="MAINTAINER">Редактор</MenuItem>
                  <MenuItem value="MEMBER">Участник</MenuItem>
                </TextField>
                <Button variant="contained" startIcon={<PersonAddIcon />}
                  onClick={() => addMemberMutation.mutate()}
                  disabled={!memberUser}>
                  Добавить
                </Button>
              </Stack>
            </>
          )}
        </>
      )}
      <Menu
        anchorEl={menuAnchor}
        open={menuFor != null}
        onClose={() => { setMenuFor(null); setMenuAnchor(null); }}
      >
        {menuFor && (
          <>
            {(['OWNER', 'MAINTAINER', 'MEMBER'] as const).map((r) => (
              <Tooltip key={r} title={
                menuFor.userId === lastOwner && r !== 'OWNER'
                  ? 'Последнего владельца нельзя убрать' : ''
              }>
                <span>
                  <MenuItem
                    disabled={menuFor.userId === lastOwner && r !== 'OWNER'}
                    selected={menuFor.role === r}
                    onClick={() => changeRoleMutation.mutate({ userId: menuFor.userId, role: r })}
                  >
                    {ROLE_LABELS[r]}
                  </MenuItem>
                </span>
              </Tooltip>
            ))}
            <Divider />
            <Tooltip title={menuFor.userId === lastOwner ? 'Последнего владельца нельзя убрать' : ''}>
              <span>
                <MenuItem disabled={menuFor.userId === lastOwner}
                  onClick={() => { setRemoveTarget(menuFor); setMenuFor(null); setMenuAnchor(null); }}
                  sx={{ color: 'error.main' }}>
                  Исключить
                </MenuItem>
              </span>
            </Tooltip>
          </>
        )}
      </Menu>
      <Dialog open={removeTarget != null} onClose={() => setRemoveTarget(null)}>
        <DialogTitle>Исключить участника</DialogTitle>
        <DialogContentText sx={{ px: 3 }}>
          {removeTarget ? `Исключить ${removeTarget.displayName} из команды?` : ''}
        </DialogContentText>
        <DialogActions>
          <Button onClick={() => setRemoveTarget(null)}>Отмена</Button>
          <Button color="error" variant="contained"
            onClick={() => removeMemberMutation.mutate(removeTarget!.userId)}>
            Исключить
          </Button>
        </DialogActions>
      </Dialog>
    </Paper>
  );
}
```

- [ ] **Step 4: Оркестратор TeamsPage.tsx**

Полная замена `ui/src/pages/TeamsPage.tsx`:

```tsx
import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Box, Paper, Typography, Stack } from '@mui/material';
import { teams as teamsApi } from '../api/teams';
import { useAuth } from '../auth/KeycloakProvider';
import PageHeader from '../components/PageHeader';
import TeamListPanel from '../components/TeamListPanel';
import TeamMembersPanel, { ROLE_LABELS } from '../components/TeamMembersPanel';

export default function TeamsPage() {
  const { authenticated, isAdmin, myTeamRoles, teamRoleOf } = useAuth();
  const [selected, setSelected] = useState<string | null>(null);

  const { data: teams } = useQuery({ queryKey: ['teams'], queryFn: teamsApi.list });
  const teamList = teams ?? [];
  const visibleTeams = isAdmin
    ? teamList
    : teamList.filter((t) => myTeamRoles[t.slug] != null);

  useEffect(() => {
    if (!selected && visibleTeams.length > 0) {
      setSelected(visibleTeams[0].slug);
    }
  }, [selected, visibleTeams]);

  const selectedTeam = teamList.find((t) => t.slug === selected);
  const ownRole = selected ? teamRoleOf(selected) : null;
  const ownRoleLabel = ownRole ? ROLE_LABELS[ownRole] : (isAdmin ? 'Админ' : null);

  return (
    <Stack spacing={3}>
      <PageHeader
        title="Команды"
        subtitle="Управление командами и доступом к элементам"
      />
      <Box sx={{ display: 'flex', gap: 3, flexDirection: { xs: 'column', md: 'row' }, alignItems: 'flex-start' }}>
        <TeamListPanel
          teams={visibleTeams}
          selected={selected}
          onSelect={setSelected}
          isAdmin={isAdmin}
          authenticated={authenticated}
        />
        {selectedTeam ? (
          <TeamMembersPanel
            key={selectedTeam.slug}
            team={selectedTeam}
            isMember={isAdmin || myTeamRoles[selectedTeam.slug] != null}
            canManage={isAdmin || myTeamRoles[selectedTeam.slug] === 'OWNER'}
            ownRoleLabel={ownRoleLabel}
            isAdmin={isAdmin}
          />
        ) : (
          <Paper sx={{ p: 3, flex: 1, width: '100%' }} data-testid="team-detail">
            <Typography color="text.secondary">Выберите команду</Typography>
          </Paper>
        )}
      </Box>
    </Stack>
  );
}
```

Примечания:
- `key={selectedTeam.slug}` — перемонтирование панели при смене команды сбрасывает внутреннее состояние (поиск кандидатов, меню, диалог) — заменяет старый сброс `memberUser`/`memberQuery` в `onSelect`
- Заглушка «Состав виден только участникам команды» остаётся защитной веткой (JWT-роли могут устареть); штатно недостижима, т.к. список отфильтрован по `myTeamRoles`
- `ownRoleLabel` вычисляется в странице; для админа без роли в команде — «Админ»

- [ ] **Step 5: Проверка**

Run: `cd ui; npm test -- TeamsPage`
Expected: PASS (5 тестов).

Run: `cd ui; npm test`
Expected: PASS (все 53).

Run: `cd ui; npm run build`
Expected: tsc + vite build clean.

- [ ] **Step 6: Коммит**

```bash
git add ui/src/components/TeamListPanel.tsx ui/src/components/TeamMembersPanel.tsx ui/src/pages/TeamsPage.tsx ui/src/pages/TeamsPage.test.tsx
git commit -m "feat: team list search, role-based visibility and panel components"
```
