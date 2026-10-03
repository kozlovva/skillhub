import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Typography, Paper, List, ListItem, ListItemText, TextField, Button, Stack,
} from '@mui/material';
import { categories as categoriesApi } from '../api/categories';
import { toApiError } from '../api/client';
import { useSnackbar } from '../layout/SnackbarContext';

export default function AdminCategoriesPage() {
  const qc = useQueryClient();
  const { showError, showSuccess } = useSnackbar();
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
    <Paper sx={{ p: 2 }}>
      <Typography variant="h5" sx={{ mb: 2 }}>Категории</Typography>
      <List>
        {(categories ?? []).map((c) => (
          <ListItem key={c.slug}>
            <ListItemText primary={c.name} secondary={c.parent ? `${c.slug} (в «${c.parent}»)` : c.slug} />
          </ListItem>
        ))}
      </List>
      <Stack direction="row" spacing={1} sx={{ mt: 1 }} flexWrap="wrap" useFlexGap>
        <TextField size="small" label="slug" value={slug}
          onChange={(e) => setSlug(e.target.value)} />
        <TextField size="small" label="Название" value={name}
          onChange={(e) => setName(e.target.value)} />
        <TextField size="small" label="Родитель (slug)" value={parentSlug}
          onChange={(e) => setParentSlug(e.target.value)} />
        <Button variant="contained" onClick={() => createMutation.mutate()}
          disabled={!slug.trim() || !name.trim()}>
          Создать
        </Button>
      </Stack>
    </Paper>
  );
}
