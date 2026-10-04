import { useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Typography, Chip, Stack, Paper, Rating, Button, TextField,
  Dialog, DialogTitle, DialogContent, DialogActions, Box, ButtonBase, Avatar,
} from '@mui/material';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import HistoryIcon from '@mui/icons-material/History';
import FolderIcon from '@mui/icons-material/Folder';
import ReviewsIcon from '@mui/icons-material/Reviews';
import { elements } from '../api/elements';
import { social } from '../api/social';
import { categories as categoriesApi } from '../api/categories';
import { downloadFile, toApiError } from '../api/client';
import { useSnackbar } from '../layout/SnackbarContext';
import { useAuth } from '../auth/KeycloakProvider';
import VersionTable from '../components/VersionTable';
import FileTree from '../components/FileTree';
import FavoriteButton from '../components/FavoriteButton';

function SectionTitle({ icon, children }: { icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1.5 }}>
      {icon}
      <Typography variant="h6">{children}</Typography>
    </Stack>
  );
}

export default function ElementPage() {
  const { slug } = useParams<{ slug: string }>();
  const qc = useQueryClient();
  const { showError, showSuccess } = useSnackbar();
  const { authenticated, isAdmin, teamRoleOf } = useAuth();
  const [reviewOpen, setReviewOpen] = useState(false);
  const [reviewRating, setReviewRating] = useState(5);
  const [reviewText, setReviewText] = useState('');
  const [selectedVersion, setSelectedVersion] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [changelog, setChangelog] = useState('');

  const { data: element } = useQuery({
    queryKey: ['element', slug],
    queryFn: () => elements.get(slug!),
  });
  const canPublish =
    isAdmin
    || (authenticated && element != null && element.team == null)
    || ['OWNER', 'MAINTAINER'].includes(teamRoleOf(element?.team ?? '') ?? '');

  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: categoriesApi.list,
  });
  const categoryName = element?.category
    ? categories?.find((c) => c.slug === element.category)?.name ?? element.category
    : null;
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

  const selected = versions?.find((v) => v.version === (selectedVersion ?? element?.latestVersion))
    ?? versions?.[0];

  const publishMutation = useMutation({
    mutationFn: (file: File) => elements.publishVersion(slug!, file, changelog || undefined),
    onSuccess: () => {
      showSuccess('Версия опубликована');
      setChangelog('');
      if (fileInputRef.current) fileInputRef.current.value = '';
      qc.invalidateQueries({ queryKey: ['versions', slug] });
      qc.invalidateQueries({ queryKey: ['element', slug] });
    },
    onError: (e) => showError(toApiError(e).message),
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
      <Paper sx={(t) => ({
        p: 3,
        borderRadius: '14px',
        background: t.palette.mode === 'dark'
          ? 'linear-gradient(135deg, rgba(108, 192, 180, 0.10), rgba(240, 138, 95, 0.14))'
          : 'linear-gradient(135deg, rgba(29, 94, 89, 0.06), rgba(185, 67, 28, 0.08))',
      })}>
        <Stack direction="row" spacing={1} alignItems="center">
          <Typography variant="h4" component="h1">{element.name}</Typography>
          <Typography
            variant="body2"
            sx={{ fontFamily: 'monospace', color: 'text.secondary', cursor: 'pointer' }}
            onClick={() => {
              navigator.clipboard?.writeText(element.slug);
              showSuccess(`Slug скопирован: ${element.slug}`);
            }}
            title="Нажмите, чтобы скопировать slug"
          >
            {element.slug}
          </Typography>
          <Chip label={element.type} color="primary" variant="outlined" size="small" />
          {categoryName && <Chip label={categoryName} variant="outlined" size="small" />}
          <FavoriteButton
            favorited={info?.favorited ?? false}
            onToggle={() => favoriteMutation.mutate(!info?.favorited)}
          />
        </Stack>
        <Typography color="text.secondary" sx={{ mt: 1 }}>{element.description}</Typography>
        <Stack direction="row" spacing={1} sx={{ mt: 1.5 }} flexWrap="wrap" useFlexGap>
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

      <Paper sx={{ p: 3, }}>
        <SectionTitle icon={<HistoryIcon sx={{ color: 'primary.main' }} />}>Версии</SectionTitle>
        {versions && versions.length > 0 ? (
          <VersionTable
            versions={versions}
            onDownload={(v) => {
              setSelectedVersion(v);
              downloadFile(elements.downloadVersionUrl(slug!, v), `${slug}-${v}.zip`)
                .catch((e) => showError(toApiError(e).message));
            }}
          />
        ) : (
          <Typography color="text.secondary">Версий пока нет</Typography>
        )}
      </Paper>

      {selected && selected.files.length > 0 && (
        <Paper sx={{ p: 3, }}>
          <SectionTitle icon={<FolderIcon sx={{ color: 'primary.main' }} />}>
            Файлы версии {selected.version}
          </SectionTitle>
          <FileTree
            files={selected.files}
            onOpenFile={(path) => {
              const fileName = path.split('/').pop();
              downloadFile(elements.downloadFileUrl(slug!, selected.version, path), fileName)
                .catch((e) => showError(toApiError(e).message));
            }}
          />
        </Paper>
      )}

      {authenticated && canPublish && (
        <Paper sx={{ p: 3, }}>
          <SectionTitle icon={<CloudUploadIcon sx={{ color: 'primary.main' }} />}>
            Опубликовать новую версию
          </SectionTitle>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }}>
            <ButtonBase
              component="label"
              sx={{
                px: 2, py: 1, borderRadius: 1,
                border: '1px dashed', borderColor: 'primary.main',
                color: 'primary.main',
                '&:hover': { backgroundColor: 'rgba(29, 94, 89, 0.08)' },
              }}
            >
              <CloudUploadIcon sx={{ mr: 1 }} /> Выбрать .zip
              <input
                type="file"
                accept=".zip"
                hidden
                ref={fileInputRef}
                onChange={(e) => {
                  const f = e.target.files?.[0];
                  if (f) publishMutation.mutate(f);
                }}
              />
            </ButtonBase>
            <TextField
              fullWidth
              label="Changelog"
              value={changelog}
              onChange={(e) => setChangelog(e.target.value)}
            />
          </Stack>
        </Paper>
      )}

      <Paper sx={{ p: 3, }}>
        <SectionTitle icon={<ReviewsIcon sx={{ color: 'primary.main' }} />}>Отзывы</SectionTitle>
        {(reviews ?? []).length === 0 && (
          <Typography color="text.secondary">Отзывов пока нет</Typography>
        )}
        <Stack spacing={2}>
          {(reviews ?? []).map((r) => (
            <Stack key={r.createdAt + r.author} direction="row" spacing={2}>
              <Avatar sx={{ bgcolor: 'primary.main', color: 'primary.contrastText', fontFamily: '"Onest", sans-serif' }}>
                {r.author.charAt(0).toUpperCase()}
              </Avatar>
              <Box>
                <Stack direction="row" spacing={1} alignItems="center">
                  <Typography variant="subtitle2">{r.author}</Typography>
                  <Rating value={r.rating} size="small" readOnly />
                </Stack>
                <Typography variant="body2" color="text.secondary">{r.text}</Typography>
              </Box>
            </Stack>
          ))}
        </Stack>
      </Paper>

      <Dialog open={reviewOpen} onClose={() => setReviewOpen(false)} fullWidth maxWidth="sm">
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
