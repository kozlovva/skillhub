import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Typography, Paper, Button, Table, TableHead, TableRow,
  TableCell, TableBody, Chip, Stack,
} from '@mui/material';
import { packs } from '../api/packs';
import { elements as elementsApi } from '../api/elements';
import { downloadFile, toApiError } from '../api/client';
import { useSnackbar } from '../layout/SnackbarContext';
import { useAuth } from '../auth/KeycloakProvider';
import AddToPackDialog from '../components/AddToPackDialog';

export default function PackPage() {
  const { slug } = useParams<{ slug: string }>();
  const qc = useQueryClient();
  const { showError, showSuccess } = useSnackbar();
  const { authenticated } = useAuth();
  const [dialogOpen, setDialogOpen] = useState(false);

  const { data: pack } = useQuery({ queryKey: ['pack', slug], queryFn: () => packs.get(slug!) });
  const { data: allElements } = useQuery({
    queryKey: ['elements'],
    queryFn: elementsApi.list,
  });

  const addMutation = useMutation({
    mutationFn: (p: { element: string; constraint: string }) =>
      packs.addContent(slug!, p.element, p.constraint),
    onSuccess: () => {
      setDialogOpen(false);
      showSuccess('Элемент добавлен в пак');
      qc.invalidateQueries({ queryKey: ['pack', slug] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  const handleDownload = async () => {
    try {
      await downloadFile(packs.downloadPackUrl(slug!), `${slug}.zip`);
    } catch (e) {
      showError(toApiError(e).message);
    }
  };

  if (!pack) return null;

  return (
    <Paper sx={{ p: 2 }}>
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
        <Typography variant="h5">Пак:</Typography>
        <Typography variant="h5">{pack.slug}</Typography>
      </Stack>
      {authenticated && (
        <Button variant="contained" sx={{ mb: 2 }} onClick={() => setDialogOpen(true)}>
          Добавить элемент
        </Button>
      )}
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Элемент</TableCell>
            <TableCell>Версия</TableCell>
            <TableCell>Ограничение</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {pack.contents.map((c) => (
            <TableRow key={c.element}>
              <TableCell>{c.element}</TableCell>
              <TableCell>{c.version ? `v${c.version}` : '—'}</TableCell>
              <TableCell>
                <Chip size="small" label={c.versionConstraint} />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <Button
        variant="outlined"
        sx={{ mt: 2 }}
        onClick={() => { void handleDownload(); }}
      >
        Скачать пак
      </Button>
      {dialogOpen && (
        <AddToPackDialog
          elements={(allElements ?? []).filter((e) => e.type !== 'PACK')}
          open
          onClose={() => setDialogOpen(false)}
          onAdd={(element, constraint) => addMutation.mutate({ element, constraint })}
        />
      )}
    </Paper>
  );
}
