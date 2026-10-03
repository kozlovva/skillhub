import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Typography, Paper, List, ListItem, ListItemText, TextField, Button,
  MenuItem, Select, FormControl, InputLabel, Stack,
} from '@mui/material';
import { teams as teamsApi } from '../api/teams';
import { toApiError } from '../api/client';
import { useSnackbar } from '../layout/SnackbarContext';

export default function TeamsPage() {
  const qc = useQueryClient();
  const { showError, showSuccess } = useSnackbar();
  const [slug, setSlug] = useState('');
  const [name, setName] = useState('');
  const [memberTeam, setMemberTeam] = useState('');
  const [memberSubject, setMemberSubject] = useState('');
  const [memberRole, setMemberRole] = useState('MEMBER');

  const { data: teams } = useQuery({ queryKey: ['teams'], queryFn: teamsApi.list });

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
      <Paper sx={{ p: 2 }}>
        <Typography variant="h5" sx={{ mb: 2 }}>Команды</Typography>
        <List>
          {(teams ?? []).map((t) => (
            <ListItem key={t.slug}>
              <ListItemText primary={t.name} secondary={t.slug} />
            </ListItem>
          ))}
        </List>
        <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
          <TextField size="small" label="slug" value={slug}
            onChange={(e) => setSlug(e.target.value)} />
          <TextField size="small" label="Название" value={name}
            onChange={(e) => setName(e.target.value)} />
          <Button variant="contained" onClick={() => createMutation.mutate()}
            disabled={!slug.trim() || !name.trim()}>
            Создать
          </Button>
        </Stack>
      </Paper>
      <Paper sx={{ p: 2 }}>
        <Typography variant="h6" sx={{ mb: 2 }}>Добавить участника</Typography>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <FormControl size="small" sx={{ minWidth: 160 }}>
            <InputLabel>Команда</InputLabel>
            <Select value={memberTeam} label="Команда"
              onChange={(e) => setMemberTeam(e.target.value)}>
              {(teams ?? []).map((t) => (
                <MenuItem key={t.slug} value={t.slug}>{t.slug}</MenuItem>
              ))}
            </Select>
          </FormControl>
          <TextField size="small" label="SSO subject" value={memberSubject}
            onChange={(e) => setMemberSubject(e.target.value)} />
          <FormControl size="small" sx={{ minWidth: 140 }}>
            <InputLabel>Роль</InputLabel>
            <Select value={memberRole} label="Роль"
              onChange={(e) => setMemberRole(e.target.value)}>
              <MenuItem value="OWNER">OWNER</MenuItem>
              <MenuItem value="MAINTAINER">MAINTAINER</MenuItem>
              <MenuItem value="MEMBER">MEMBER</MenuItem>
            </Select>
          </FormControl>
          <Button variant="contained" onClick={() => addMemberMutation.mutate()}
            disabled={!memberTeam || !memberSubject.trim()}>
            Добавить
          </Button>
        </Stack>
      </Paper>
    </Stack>
  );
}
