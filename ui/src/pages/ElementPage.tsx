import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Typography, Chip, Stack, Paper, Rating, Button, TextField,
  Dialog, DialogTitle, DialogContent, DialogActions,
} from '@mui/material';
import { elements } from '../api/elements';
import { social } from '../api/social';
import { downloadFile, toApiError } from '../api/client';
import { useSnackbar } from '../layout/SnackbarContext';
import { useAuth } from '../auth/KeycloakProvider';
import VersionTable from '../components/VersionTable';
import FavoriteButton from '../components/FavoriteButton';

export default function ElementPage() {
  const { slug } = useParams<{ slug: string }>();
  const qc = useQueryClient();
  const { showError, showSuccess } = useSnackbar();
  const { authenticated } = useAuth();
  const [reviewOpen, setReviewOpen] = useState(false);
  const [reviewRating, setReviewRating] = useState(5);
  const [reviewText, setReviewText] = useState('');

  const { data: element } = useQuery({
    queryKey: ['element', slug],
    queryFn: () => elements.get(slug!),
  });
  const { data: versions } = useQuery({
    queryKey: ['versions', slug],
    queryFn: () => elements.versions(slug!),
  });
  const { data: info } = useQuery({
    queryKey: ['social', slug],
    queryFn: () => social.info(slug!),
  });
  const { data: reviews } = useQuery({
    queryKey: ['reviews', slug],
    queryFn: () => social.reviews(slug!),
  });

  const favoriteMutation = useMutation({
    mutationFn: (next: boolean) => social.setFavorite(slug!, next),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['social', slug] }),
    onError: (e) => showError(toApiError(e).message),
  });

  const reviewMutation = useMutation({
    mutationFn: () => social.review(slug!, reviewRating, reviewText),
    onSuccess: () => {
      setReviewOpen(false);
      showSuccess('Отзыв сохранён');
      qc.invalidateQueries({ queryKey: ['reviews', slug] });
      qc.invalidateQueries({ queryKey: ['social', slug] });
    },
    onError: (e) => showError(toApiError(e).message),
  });

  if (!element) return null;

  return (
    <Stack spacing={3}>
      <Paper sx={{ p: 2 }}>
        <Stack direction="row" spacing={1} alignItems="center">
          <Typography variant="h5">{element.name}</Typography>
          <Chip label={element.type} color="primary" variant="outlined" size="small" />
          <FavoriteButton
            favorited={info?.favorited ?? false}
            onToggle={() => favoriteMutation.mutate(!info?.favorited)}
          />
        </Stack>
        <Typography color="text.secondary" sx={{ mt: 1 }}>{element.description}</Typography>
        <Stack direction="row" spacing={2} sx={{ mt: 1 }}>
          {element.tags.map((t) => <Chip key={t} label={t} size="small" />)}
        </Stack>
        <Stack direction="row" spacing={2} sx={{ mt: 2 }} alignItems="center">
          <Rating value={info?.avgRating ?? 0} precision={0.1} readOnly />
          <Typography variant="caption">({info?.ratingCount ?? 0} оценок)</Typography>
          {authenticated && (
            <Button size="small" onClick={() => setReviewOpen(true)}>Написать отзыв</Button>
          )}
        </Stack>
      </Paper>

      <Paper sx={{ p: 2 }}>
        <Typography variant="h6" sx={{ mb: 1 }}>Версии</Typography>
        {versions && versions.length > 0 ? (
          <VersionTable
            versions={versions}
            onDownload={(v) => {
              downloadFile(elements.downloadVersionUrl(slug!, v), `${slug}-${v}.zip`)
                .catch((e) => showError(toApiError(e).message));
            }}
          />
        ) : (
          <Typography color="text.secondary">Версий пока нет</Typography>
        )}
      </Paper>

      <Paper sx={{ p: 2 }}>
        <Typography variant="h6" sx={{ mb: 1 }}>Отзывы</Typography>
        {(reviews ?? []).map((r) => (
          <Stack key={r.createdAt + r.author} spacing={0.5} sx={{ mb: 1.5 }}>
            <Stack direction="row" spacing={1} alignItems="center">
              <Typography variant="subtitle2">{r.author}</Typography>
              <Rating value={r.rating} size="small" readOnly />
            </Stack>
            <Typography variant="body2">{r.text}</Typography>
          </Stack>
        ))}
      </Paper>

      <Dialog open={reviewOpen} onClose={() => setReviewOpen(false)}>
        <DialogTitle>Новый отзыв</DialogTitle>
        <DialogContent>
          <Rating
            value={reviewRating}
            onChange={(_, v) => setReviewRating(v ?? 5)}
            sx={{ my: 2 }}
          />
          <TextField
            fullWidth
            multiline
            minRows={3}
            label="Текст отзыва"
            value={reviewText}
            onChange={(e) => setReviewText(e.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setReviewOpen(false)}>Отмена</Button>
          <Button onClick={() => reviewMutation.mutate()} disabled={!reviewText.trim()}>
            Отправить
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
