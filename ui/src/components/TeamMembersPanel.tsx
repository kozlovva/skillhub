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
  team, isMember, canManage, ownRoleLabel,
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
