import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Typography, Paper, Table, TableHead, TableRow, TableCell, TableBody,
  Button, TextField, Dialog, DialogTitle, DialogContent, DialogActions, Stack, Box,
  MenuItem, Select, FormControl, InputLabel,
} from '@mui/material';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import { api, toApiError } from '../api/client';
import { useSnackbar } from '../layout/SnackbarContext';
import PageHeader from '../components/PageHeader';

interface TokenItem {
  id: string;
  name: string;
  createdAt: string;
  lastUsedAt: string | null;
  expiresAt: string | null;
  revokedAt: string | null;
}

const LIFETIME_OPTIONS = [
  { value: 7, label: '7 дней' },
  { value: 30, label: '30 дней' },
  { value: 90, label: '90 дней' },
  { value: 0, label: 'Бессрочно' },
];

function formatExpiry(expiresAt: string | null): string {
  return expiresAt ? new Date(expiresAt).toLocaleString() : '—';
}

export default function TokensPage() {
  const qc = useQueryClient();
  const { showError, showSuccess } = useSnackbar();
  const [name, setName] = useState('');
  const [lifetimeDays, setLifetimeDays] = useState<number>(0);
  const [rawToken, setRawToken] = useState<string | null>(null);
  const [rawTokenExpiresAt, setRawTokenExpiresAt] = useState<string | null>(null);
  const [tokenToRevoke, setTokenToRevoke] = useState<TokenItem | null>(null);

  const { data: tokens } = useQuery({
    queryKey: ['tokens'],
    queryFn: async () => (await api.get<TokenItem[]>('/api/tokens')).data,
  });

  const createMutation = useMutation({
    mutationFn: async () => {
      const res = await api.post<{ token: string; name: string }>('/api/tokens', {
        name,
        lifetimeDays: lifetimeDays === 0 ? null : lifetimeDays,
      });
      return res.data;
    },
    onSuccess: (data) => {
      setRawToken(data.token);
      setRawTokenExpiresAt(
        lifetimeDays === 0
          ? null
          : new Date(Date.now() + lifetimeDays * 24 * 60 * 60 * 1000).toISOString(),
      );
      setName('');
      setLifetimeDays(0);
      showSuccess('Токен создан');
      qc.invalidateQueries({ queryKey: ['tokens'] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  const revokeMutation = useMutation({
    mutationFn: async (id: string) => {
      await api.delete(`/api/tokens/${id}`);
    },
    onSuccess: () => {
      setTokenToRevoke(null);
      showSuccess('Токен отозван');
      qc.invalidateQueries({ queryKey: ['tokens'] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  return (
    <Stack spacing={3}>
      <PageHeader
        title="API-токены"
        subtitle="Токены используются CLI и AI-агентами (заголовок Authorization: Bearer)."
      />
      <Paper sx={{ p: 3 }}>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Имя</TableCell>
            <TableCell>Создан</TableCell>
            <TableCell>Истекает</TableCell>
            <TableCell>Последнее использование</TableCell>
            <TableCell>Действия</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {(tokens ?? []).map((t) => {
            const expired =
              t.expiresAt !== null && new Date(t.expiresAt).getTime() < Date.now();
            return (
              <TableRow key={t.id}>
                <TableCell sx={{ fontWeight: 500 }}>{t.name}</TableCell>
                <TableCell>{new Date(t.createdAt).toLocaleString()}</TableCell>
                <TableCell sx={expired ? { color: 'text.disabled' } : undefined}>
                  {formatExpiry(t.expiresAt)}
                </TableCell>
                <TableCell>{t.lastUsedAt ? new Date(t.lastUsedAt).toLocaleString() : '—'}</TableCell>
                <TableCell>
                  {t.revokedAt ? (
                    <Typography variant="body2" color="text.disabled">
                      Отозван {new Date(t.revokedAt).toLocaleString()}
                    </Typography>
                  ) : (
                    <Button size="small" color="error" onClick={() => setTokenToRevoke(t)}>
                      Отозвать
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
      <Stack direction="row" spacing={1} sx={{ mt: 2 }} flexWrap="wrap" useFlexGap alignItems="center">
        <TextField
          label="Имя токена"
          value={name}
          onChange={(e) => setName(e.target.value)}
          sx={{ width: 220 }}
        />
        <FormControl sx={{ width: 160 }}>
          <InputLabel id="token-lifetime-label">Срок жизни</InputLabel>
          <Select
            labelId="token-lifetime-label"
            label="Срок жизни"
            value={lifetimeDays}
            onChange={(e) => setLifetimeDays(Number(e.target.value))}
          >
            {LIFETIME_OPTIONS.map((o) => (
              <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>
            ))}
          </Select>
        </FormControl>
        <Box sx={{ flexGrow: 1 }} />
        <Button
          variant="contained"
          onClick={() => createMutation.mutate()}
          disabled={!name.trim() || createMutation.isPending}
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
          <Typography variant="body2" sx={{ mt: 2 }}>
            Срок действия: {rawTokenExpiresAt ? new Date(rawTokenExpiresAt).toLocaleString() : 'бессрочно'}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRawToken(null)}>Закрыть</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={tokenToRevoke !== null} onClose={() => setTokenToRevoke(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Отозвать токен?</DialogTitle>
        <DialogContent>
          <Typography variant="body2">
            Токен «{tokenToRevoke?.name}» перестанет работать немедленно. Действие необратимо.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTokenToRevoke(null)}>Отмена</Button>
          <Button
            color="error"
            variant="contained"
            disabled={revokeMutation.isPending}
            onClick={() => tokenToRevoke && revokeMutation.mutate(tokenToRevoke.id)}
          >
            Отозвать
          </Button>
        </DialogActions>
      </Dialog>
      </Paper>
    </Stack>
  );
}
