import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Typography, Paper, List, ListItem, ListItemAvatar, Avatar, ListItemText, TextField, Button,
  MenuItem, Stack, Divider,
} from '@mui/material';
import GroupsIcon from '@mui/icons-material/Groups';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import AddBusinessIcon from '@mui/icons-material/AddBusiness';
import { teams as teamsApi } from '../api/teams';
import { toApiError } from '../api/client';
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
  const [memberSubject, setMemberSubject] = useState('');
  const [memberRole, setMemberRole] = useState('MEMBER');

  const { data: teams } = useQuery({ queryKey: ['teams'], queryFn: teamsApi.list });

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
    mutationFn: () => teamsApi.addMember(memberTeam, memberSubject, memberRole),
    onSuccess: () => {
      showSuccess('Участник добавлен');
      setMemberSubject('');
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
            <TextField label="SSO subject" value={memberSubject}
              onChange={(e) => setMemberSubject(e.target.value)} sx={{ width: 220 }} />
            <TextField select label="Роль" value={memberRole}
              onChange={(e) => setMemberRole(e.target.value)} sx={{ width: 140 }}>
              <MenuItem value="OWNER">Владелец</MenuItem>
              <MenuItem value="MAINTAINER">Редактор</MenuItem>
              <MenuItem value="MEMBER">Участник</MenuItem>
            </TextField>
            <Button variant="contained" onClick={() => addMemberMutation.mutate()}
              disabled={!memberTeam || !memberSubject.trim()}>
              Добавить
            </Button>
          </Stack>
        </Paper>
      )}
    </Stack>
  );
}
