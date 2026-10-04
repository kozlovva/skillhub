import { useEffect, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Typography, Paper, List, ListItem, ListItemAvatar, Avatar, ListItemText, TextField, Button,
  MenuItem, Stack, Divider, Autocomplete,
} from '@mui/material';
import GroupsIcon from '@mui/icons-material/Groups';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import AddBusinessIcon from '@mui/icons-material/AddBusiness';
import { teams as teamsApi } from '../api/teams';
import { toApiError } from '../api/client';
import type { MemberCandidate } from '../types';
import { useSnackbar } from '../layout/SnackbarContext';
import { useAuth } from '../auth/KeycloakProvider';
import PageHeader from '../components/PageHeader';

export default function TeamsPage() {
  const qc = useQueryClient();
  const { showError, showSuccess } = useSnackbar();
  const { authenticated, isAdmin, myTeamRoles, teamRoleOf } = useAuth();
  const [slug, setSlug] = useState('');
  const [name, setName] = useState('');
  const [memberTeam, setMemberTeam] = useState('');
  const [memberUser, setMemberUser] = useState<MemberCandidate | null>(null);
  const [memberQuery, setMemberQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [memberRole, setMemberRole] = useState('MEMBER');

  useEffect(() => {
    const t = setTimeout(() => setDebouncedQuery(memberQuery.trim()), 300);
    return () => clearTimeout(t);
  }, [memberQuery]);

  const { data: teams } = useQuery({ queryKey: ['teams'], queryFn: teamsApi.list });

  const { data: candidates } = useQuery({
    queryKey: ['member-candidates', memberTeam, debouncedQuery],
    queryFn: () => teamsApi.searchCandidates(memberTeam, debouncedQuery),
    enabled: !!memberTeam && debouncedQuery.length >= 2,
  });

  const canAddMembers = isAdmin || Object.values(myTeamRoles).includes('OWNER');
  const manageableTeams = isAdmin ? (teams ?? []) : (teams ?? []).filter((t) => teamRoleOf(t.slug) === 'OWNER');

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
    mutationFn: () => teamsApi.addMember(memberTeam, memberUser!.userId, memberRole),
    onSuccess: () => {
      showSuccess('Участник добавлен');
      setMemberUser(null);
      setMemberQuery('');
      qc.invalidateQueries({ queryKey: ['teams'] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  return (
    <Stack spacing={3}>
      <PageHeader
        title="Команды"
        subtitle="Управление командами и доступом к элементам"
      />
      <Paper sx={{ p: 3, }}>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
          <GroupsIcon sx={{ color: 'primary.main' }} />
          <Typography variant="h6">Команды ({(teams ?? []).length})</Typography>
        </Stack>
        {teams && teams.length === 0 && (
          <Typography color="text.secondary" sx={{ mb: 1 }}>
            Команд пока нет
          </Typography>
        )}
        <Divider />
        {isAdmin ? (
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ my: 2 }}>
            <TextField label="slug" value={slug}
              onChange={(e) => setSlug(e.target.value)} sx={{ width: 160 }} />
            <TextField label="Название" value={name}
              onChange={(e) => setName(e.target.value)} sx={{ width: 200 }} />
            <Button variant="contained" startIcon={<AddBusinessIcon />} onClick={() => createMutation.mutate()}
              disabled={!slug.trim() || !name.trim()}>
              Создать
            </Button>
          </Stack>
        ) : (
          <Typography color="text.secondary" sx={{ my: 2 }}>
            {authenticated
              ? 'Создавать команды могут только администраторы'
              : 'Войдите, чтобы управлять командами'}
          </Typography>
        )}
        <List disablePadding>
          {(teams ?? []).map((t) => (
            <ListItem key={t.slug} disableGutters sx={{ py: 1 }}>
              <ListItemAvatar>
                <Avatar sx={{ bgcolor: 'rgba(29, 94, 89, 0.12)', color: 'primary.main', fontFamily: '"Onest", sans-serif' }}>
                  {t.name.charAt(0).toUpperCase()}
                </Avatar>
              </ListItemAvatar>
              <ListItemText primary={t.name} secondary={t.slug} />
            </ListItem>
          ))}
        </List>
      </Paper>
      {canAddMembers && (
        <Paper sx={{ p: 3, }}>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
            <PersonAddIcon sx={{ color: 'primary.main' }} />
            <Typography variant="h6">Добавить участника</Typography>
          </Stack>
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            <TextField select label="Команда" value={memberTeam}
              onChange={(e) => setMemberTeam(e.target.value)} sx={{ width: 160 }}>
              {manageableTeams.map((t) => (
                <MenuItem key={t.slug} value={t.slug}>{t.slug}</MenuItem>
              ))}
            </TextField>
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
            <Button variant="contained" onClick={() => addMemberMutation.mutate()}
              disabled={!memberTeam || !memberUser}>
              Добавить
            </Button>
          </Stack>
        </Paper>
      )}
    </Stack>
  );
}
