import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Typography, Paper, List, ListItem, ListItemText, TextField, Button, Stack, Avatar,
} from '@mui/material';
import { categories as categoriesApi } from '../api/categories';
import { toApiError } from '../api/client';
import { useSnackbar } from '../layout/SnackbarContext';
import { useAuth } from '../auth/KeycloakProvider';
import PageHeader from '../components/PageHeader';

export default function AdminCategoriesPage() {
  const qc = useQueryClient();
  const { showError, showSuccess } = useSnackbar();
  const { isAdmin, authenticated } = useAuth();
  const [slug, setSlug] = useState('');
  const [name, setName] = useState('');
  const [parentSlug, setParentSlug] = useState('');

  const { data: categories } = useQuery({ queryKey: ['categories'], queryFn: categoriesApi.list });

  const createMutation = useMutation({
    mutationFn: () => categoriesApi.create({
      slug, name, parentSlug: parentSlug || undefined,
    }),
    onSuccess: () => {
      showSuccess('Категория создана');
      setSlug(''); setName(''); setParentSlug('');
      qc.invalidateQueries({ queryKey: ['categories'] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  return (
    <Stack spacing={3}>
      <PageHeader title="Категории" />
      <Paper sx={{ p: 3 }}>
        {isAdmin ? (
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mb: 2 }}>
            <TextField label="slug" value={slug}
              onChange={(e) => setSlug(e.target.value)} sx={{ width: 160 }} />
            <TextField label="Название" value={name}
              onChange={(e) => setName(e.target.value)} sx={{ width: 200 }} />
            <TextField label="Родитель (slug)" value={parentSlug}
              onChange={(e) => setParentSlug(e.target.value)} sx={{ width: 200 }} />
            <Button variant="contained" onClick={() => createMutation.mutate()}
              disabled={!slug.trim() || !name.trim()}>
              Создать
            </Button>
          </Stack>
        ) : (
          <Typography color="text.secondary" sx={{ mb: 2 }}>
            {authenticated
              ? 'Управлять категориями могут только администраторы'
              : 'Войдите под учётной записью администратора'}
          </Typography>
        )}
        <List disablePadding>
        {(categories ?? []).map((c) => (
          <ListItem key={c.slug} disableGutters sx={{ py: 0.75 }}>
            <Avatar sx={{ bgcolor: 'rgba(29, 94, 89, 0.12)', color: 'primary.main', mr: 2, width: 36, height: 36 }}>
              {c.name.charAt(0).toUpperCase()}
            </Avatar>
            <ListItemText
              primary={c.name}
              secondary={c.parent ? `${c.slug} (в «${c.parent}»)` : c.slug}
            />
          </ListItem>
        ))}
        </List>
      </Paper>
    </Stack>
  );
}
