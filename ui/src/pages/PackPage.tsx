import { useState } from 'react';
import { useParams, Link as RouterLink } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Typography, Paper, Button, Table, TableHead, TableRow,
  TableCell, TableBody, Chip, Stack, Box,
} from '@mui/material';
import DownloadIcon from '@mui/icons-material/Download';
import LibraryAddIcon from '@mui/icons-material/LibraryAdd';
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
    <Paper sx={{ p: 3, }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }} sx={{ mb: 2 }}>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="h4" component="h1">
            Пак: <Typography variant="inherit" component="span">{pack.slug}</Typography>
          </Typography>
          <Typography color="text.secondary">{pack.contents.length} элементов</Typography>
        </Box>
        {authenticated && (
          <Button variant="contained" startIcon={<LibraryAddIcon />} onClick={() => setDialogOpen(true)}>
            Добавить элемент
          </Button>
        )}
        <Button variant="outlined" startIcon={<DownloadIcon />} onClick={() => { void handleDownload(); }}>
          Скачать пак
        </Button>
      </Stack>
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
              <TableCell>
                <RouterLink
                  to={`/elements/${c.element}`}
                  style={{ color: 'inherit', fontWeight: 500, textDecoration: 'none' }}
                >
                  {c.element}
                </RouterLink>
              </TableCell>
              <TableCell>{c.version ? `v${c.version}` : '—'}</TableCell>
              <TableCell>
                <Chip size="small" label={c.versionConstraint} />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
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
