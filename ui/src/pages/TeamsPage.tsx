import { useEffect, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Typography, Paper, List, ListItem, ListItemButton, ListItemAvatar, Avatar, ListItemText,
  TextField, Button, MenuItem, Stack, Divider, Autocomplete, Chip, IconButton, Menu, Tooltip,
  Dialog, DialogTitle, DialogContentText, DialogActions, Box,
} from '@mui/material';
import GroupsIcon from '@mui/icons-material/Groups';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import AddBusinessIcon from '@mui/icons-material/AddBusiness';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import LockIcon from '@mui/icons-material/Lock';
import { teams as teamsApi } from '../api/teams';
import { toApiError } from '../api/client';
import type { MemberCandidate, TeamMemberResponse } from '../types';
import { useSnackbar } from '../layout/SnackbarContext';
import { useAuth } from '../auth/KeycloakProvider';
import PageHeader from '../components/PageHeader';

const ROLE_LABELS: Record<string, string> = {
  OWNER: 'Владелец',
  MAINTAINER: 'Редактор',
  MEMBER: 'Участник',
};

function roleChipProps(role: string): { color?: 'primary' | 'secondary'; variant?: 'filled' | 'outlined' } {
  if (role === 'OWNER') return { color: 'primary' };
  if (role === 'MAINTAINER') return { color: 'secondary' };
  return { variant: 'outlined' };
}

export default function TeamsPage() {
  const qc = useQueryClient();
  const { showError, showSuccess } = useSnackbar();
  const { authenticated, isAdmin, myTeamRoles, teamRoleOf } = useAuth();
  const [slug, setSlug] = useState('');
  const [name, setName] = useState('');
  const [selected, setSelected] = useState<string | null>(null);
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

  const { data: teams } = useQuery({ queryKey: ['teams'], queryFn: teamsApi.list });
  const teamList = teams ?? [];

  useEffect(() => {
    if (!selected && teamList.length > 0) {
      setSelected(teamList[0].slug);
    }
  }, [selected, teamList]);

  const isMember = selected != null && (isAdmin || myTeamRoles[selected] != null);
  const canManage = selected != null && (isAdmin || myTeamRoles[selected] === 'OWNER');

  const { data: members } = useQuery({
    queryKey: ['team-members', selected],
    queryFn: () => teamsApi.members(selected!),
    enabled: !!selected && isMember,
  });

  const { data: candidates } = useQuery({
    queryKey: ['member-candidates', selected, debouncedQuery],
    queryFn: () => teamsApi.searchCandidates(selected!, debouncedQuery),
    enabled: !!selected && canManage && debouncedQuery.length >= 2,
  });

  const lastOwnerUserId = (list: TeamMemberResponse[] | undefined) => {
    const owners = (list ?? []).filter((m) => m.role === 'OWNER');
    return owners.length === 1 ? owners[0].userId : null;
  };

  const createMutation = useMutation({
    mutationFn: () => teamsApi.create({ slug, name }),
    onSuccess: () => {
      showSuccess('Команда создана');
      setSlug(''); setName('');
      qc.invalidateQueries({ queryKey: ['teams'] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  const addMemberMutation = useMutation({
    mutationFn: () => teamsApi.addMember(selected!, memberUser!.userId, memberRole),
    onSuccess: () => {
      showSuccess('Участник добавлен');
      setMemberUser(null);
      setMemberQuery('');
      qc.invalidateQueries({ queryKey: ['team-members', selected] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  const changeRoleMutation = useMutation({
    mutationFn: (v: { userId: string; role: string }) => teamsApi.changeRole(selected!, v.userId, v.role),
    onSuccess: () => {
      showSuccess('Роль изменена');
      setMenuFor(null);
      qc.invalidateQueries({ queryKey: ['team-members', selected] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  const removeMemberMutation = useMutation({
    mutationFn: (userId: string) => teamsApi.removeMember(selected!, userId),
    onSuccess: () => {
      showSuccess('Участник исключён');
      setRemoveTarget(null);
      qc.invalidateQueries({ queryKey: ['team-members', selected] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  const roster = members ?? [];
  const lastOwner = lastOwnerUserId(members);
  const selectedTeam = teamList.find((t) => t.slug === selected);
  const ownRole = selected ? teamRoleOf(selected) : null;

  return (
    <Stack spacing={3}>
      <PageHeader
        title="Команды"
        subtitle="Управление командами и доступом к элементам"
      />
      <Box sx={{ display: 'flex', gap: 3, flexDirection: { xs: 'column', md: 'row' }, alignItems: 'flex-start' }}>
        <Paper sx={{ p: 3, width: { xs: '100%', md: 320 }, flexShrink: 0 }}>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
            <GroupsIcon sx={{ color: 'primary.main' }} />
            <Typography variant="h6">Команды ({teamList.length})</Typography>
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
          {!isAdmin && (
            <Typography color="text.secondary" sx={{ mb: 2 }} variant="body2">
              {authenticated
                ? 'Создавать команды могут только администраторы'
                : 'Войдите, чтобы управлять командами'}
            </Typography>
          )}
          <List disablePadding>
            {teamList.map((t) => (
              <ListItemButton key={t.slug} selected={t.slug === selected}
                onClick={() => { setSelected(t.slug); setMemberUser(null); setMemberQuery(''); }}
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
        </Paper>
        <Paper sx={{ p: 3, flex: 1, width: '100%' }} data-testid="team-detail">
          {!selectedTeam && (
            <Typography color="text.secondary">Выберите команду</Typography>
          )}
          {selectedTeam && !isMember && (
            <Stack spacing={1} alignItems="flex-start">
              <Typography variant="h6">{selectedTeam.name}</Typography>
              <Stack direction="row" spacing={1} alignItems="center" sx={{ color: 'text.secondary' }}>
                <LockIcon fontSize="small" />
                <Typography>Состав виден только участникам команды</Typography>
              </Stack>
            </Stack>
          )}
          {selectedTeam && isMember && (
            <>
              <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }} flexWrap="wrap" useFlexGap>
                <Typography variant="h6">{selectedTeam.name}</Typography>
                <Typography variant="body2" color="text.secondary">{selectedTeam.slug}</Typography>
                <Chip label={isAdmin && !ownRole ? 'Админ' : (ownRole ? ROLE_LABELS[ownRole] : '')} size="small" />
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
                      <Tooltip title={
                        m.userId === lastOwner ? 'Последнего владельца нельзя убрать' : ''
                      }>
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
        </Paper>
      </Box>
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
    </Stack>
  );
}
