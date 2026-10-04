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
      {!isAdmin && (
        <Typography color="text.secondary" sx={{ mb: 2 }} variant="body2">
          {authenticated
            ? 'Создавать команды могут только администраторы'
            : 'Войдите, чтобы управлять командами'}
        </Typography>
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
          slotProps={{
            htmlInput: { 'aria-label': 'Поиск команд' },
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              ),
            },
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
