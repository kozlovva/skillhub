import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Typography, Paper, Table, TableHead, TableRow, TableCell, TableBody,
  Button, TextField, Dialog, DialogTitle, DialogContent, DialogActions,
} from '@mui/material';
import { api, toApiError } from '../api/client';
import { useSnackbar } from '../layout/SnackbarContext';

interface TokenItem {
  name: string;
  createdAt: string;
  lastUsedAt: string | null;
  expiresAt: string | null;
}

export default function TokensPage() {
  const qc = useQueryClient();
  const { showError, showSuccess } = useSnackbar();
  const [name, setName] = useState('');
  const [rawToken, setRawToken] = useState<string | null>(null);

  const { data: tokens } = useQuery({
    queryKey: ['tokens'],
    queryFn: async () => (await api.get<TokenItem[]>('/api/tokens')).data,
  });

  const createMutation = useMutation({
    mutationFn: async () => {
      const res = await api.post<{ token: string; name: string }>('/api/tokens', { name });
      return res.data;
    },
    onSuccess: (data) => {
      setRawToken(data.token);
      setName('');
      showSuccess('Токен создан');
      qc.invalidateQueries({ queryKey: ['tokens'] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="h5" sx={{ mb: 2 }}>API-токены</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Токены используются CLI и AI-агентами (заголовок Authorization: Bearer).
      </Typography>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Имя</TableCell>
            <TableCell>Создан</TableCell>
            <TableCell>Последнее использование</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {(tokens ?? []).map((t) => (
            <TableRow key={t.name}>
              <TableCell>{t.name}</TableCell>
              <TableCell>{new Date(t.createdAt).toLocaleString()}</TableCell>
              <TableCell>{t.lastUsedAt ? new Date(t.lastUsedAt).toLocaleString() : '—'}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <TextField
        size="small"
        placeholder="Имя токена"
        value={name}
        onChange={(e) => setName(e.target.value)}
        sx={{ mt: 2, mr: 1 }}
      />
      <Button
        variant="contained"
        sx={{ mt: 2 }}
        onClick={() => createMutation.mutate()}
        disabled={!name.trim()}
      >
        Создать токен
      </Button>
      <Dialog open={rawToken !== null} onClose={() => setRawToken(null)}>
        <DialogTitle>Токен создан</DialogTitle>
        <DialogContent>
          <Typography variant="body2" sx={{ mb: 1 }}>
            Скопируйте токен сейчас — он больше не будет показан:
          </Typography>
          <Typography
            component="code"
            sx={{
              display: 'block',
              p: 1,
              bgcolor: 'action.hover',
              fontFamily: 'monospace',
              wordBreak: 'break-all',
            }}
          >
            {rawToken}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRawToken(null)}>Закрыть</Button>
        </DialogActions>
      </Dialog>
    </Paper>
  );
}
