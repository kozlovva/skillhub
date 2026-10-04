import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Typography, Paper, Table, TableHead, TableRow, TableCell, TableBody,
  Button, TextField, Dialog, DialogTitle, DialogContent, DialogActions, Stack, Box,
} from '@mui/material';
import KeyIcon from '@mui/icons-material/Key';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
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
    <Paper sx={{ p: 3, }}>
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
        <KeyIcon sx={{ color: 'primary.main' }} />
        <Typography variant="h4" component="h1">API-токены</Typography>
      </Stack>
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
              <TableCell sx={{ fontWeight: 500 }}>{t.name}</TableCell>
              <TableCell>{new Date(t.createdAt).toLocaleString()}</TableCell>
              <TableCell>{t.lastUsedAt ? new Date(t.lastUsedAt).toLocaleString() : '—'}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <Stack direction="row" spacing={1} sx={{ mt: 2 }} flexWrap="wrap" useFlexGap>
        <TextField
          label="Имя токена"
          value={name}
          onChange={(e) => setName(e.target.value)}
          sx={{ width: 220 }}
        />
        <Button
          variant="contained"
          onClick={() => createMutation.mutate()}
          disabled={!name.trim()}
        >
          Создать токен
        </Button>
      </Stack>
      <Dialog open={rawToken !== null} onClose={() => setRawToken(null)} fullWidth maxWidth="sm">
        <DialogTitle>Токен создан</DialogTitle>
        <DialogContent>
          <Typography variant="body2" sx={{ mb: 1 }}>
            Скопируйте токен сейчас — он больше не будет показан:
          </Typography>
          <Stack
            direction="row"
            spacing={1}
            alignItems="center"
            sx={{
              bgcolor: 'background.paper',
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: 1,
              p: 1,
            }}
          >
            <Box
              component="code"
              sx={{
                fontFamily: '"JetBrains Mono", ui-monospace, monospace',
                fontSize: 13,
                lineHeight: '20px',
                px: 1,
                wordBreak: 'break-all',
                flexGrow: 1,
                userSelect: 'all',
              }}
            >
              {rawToken}
            </Box>
            <Button
              size="small"
              startIcon={<ContentCopyIcon />}
              onClick={() => {
                void navigator.clipboard?.writeText(rawToken ?? '');
                showSuccess('Скопировано');
              }}
            >
              Копировать
            </Button>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRawToken(null)}>Закрыть</Button>
        </DialogActions>
      </Dialog>
    </Paper>
  );
}
