import { useRef, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  Autocomplete, Box, Button, MenuItem, Paper, Stack, TextField, Typography,
} from '@mui/material';
import UploadIcon from '@mui/icons-material/Upload';
import { elements as elementsApi } from '../api/elements';
import { categories as categoriesApi } from '../api/categories';
import { me as meApi } from '../api/me';
import { toApiError } from '../api/client';
import { useSnackbar } from '../layout/SnackbarContext';
import { useAuth } from '../auth/KeycloakProvider';
import type { ElementType } from '../types';

const ELEMENT_TYPES: ElementType[] = ['SKILL', 'SCRIPT', 'AGENT', 'HOOK', 'PACK', 'OTHER'];
const SLUG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;

const slugify = (value: string) =>
  value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');

export default function UploadPage() {
  const { authenticated } = useAuth();
  const { showError, showSuccess } = useSnackbar();
  const navigate = useNavigate();

  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [slugTouched, setSlugTouched] = useState(false);
  const [slugError, setSlugError] = useState<string | null>(null);
  const [type, setType] = useState<ElementType | ''>('');
  const [team, setTeam] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('');
  const [tags, setTags] = useState<string[]>([]);
  const [visibility, setVisibility] = useState<'PUBLIC' | 'TEAM' | ''>('');
  const [file, setFile] = useState<File | null>(null);
  const [changelog, setChangelog] = useState('');

  const { data: meInfo } = useQuery({
    queryKey: ['me'],
    queryFn: meApi.get,
    enabled: authenticated,
  });
  const { data: categories } = useQuery({ queryKey: ['categories'], queryFn: categoriesApi.list });

  const createdRef = useRef<Set<string>>(new Set());

  const submitMutation = useMutation({
    mutationFn: async () => {
      if (!createdRef.current.has(slug)) {
        await elementsApi.create({
          slug,
          type,
          name,
          description: description || undefined,
          team,
          category: category || undefined,
          tags,
          visibility,
        });
        createdRef.current.add(slug);
      }
      return elementsApi.publishVersion(slug, file as File, changelog || undefined);
    },
    onSuccess: () => {
      showSuccess('Элемент опубликован');
      navigate(`/elements/${slug}`);
    },
    onError: (e) => {
      const err = toApiError(e);
      if (err.status === 409) {
        setSlugError('Элемент с таким slug уже существует');
      } else {
        showError(err.message);
      }
    },
  });

  if (!authenticated) return <Navigate to="/" replace />;

  const canSubmit =
    !!name.trim() && SLUG_PATTERN.test(slug) && !!type && !!team && !!visibility && !!file;
  const slugInvalid = slug.length > 0 && !SLUG_PATTERN.test(slug);

  const handleSubmit = () => {
    setSlugError(null);
    submitMutation.mutate();
  };

  return (
    <Paper sx={{ p: 3, maxWidth: 720 }}>
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
        <UploadIcon sx={{ color: 'primary.main' }} />
        <Typography variant="h4" component="h1">Загрузить элемент</Typography>
      </Stack>
      <Stack spacing={2}>
        <TextField
          label="Название"
          value={name}
          required
          onChange={(e) => {
            setName(e.target.value);
            if (!slugTouched) setSlug(slugify(e.target.value));
          }}
        />
        <TextField
          label="Slug"
          value={slug}
          required
          onChange={(e) => {
            setSlugTouched(true);
            setSlug(e.target.value);
          }}
          error={!!slugError || slugInvalid}
          helperText={
            slugError ??
            (slugInvalid ? 'Только строчные латинские буквы, цифры и дефисы' : undefined)
          }
        />
        <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap>
          <TextField
            select
            label="Тип"
            value={type}
            required
            sx={{ minWidth: 160 }}
            onChange={(e) => setType(e.target.value as ElementType)}
          >
            {ELEMENT_TYPES.map((t) => (
              <MenuItem key={t} value={t}>{t}</MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label="Команда"
            value={team}
            required
            sx={{ minWidth: 200 }}
            onChange={(e) => setTeam(e.target.value)}
          >
            {(meInfo?.teams ?? []).map((t) => (
              <MenuItem key={t.slug} value={t.slug}>{t.name}</MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label="Видимость"
            value={visibility}
            required
            sx={{ minWidth: 220 }}
            onChange={(e) => setVisibility(e.target.value as 'PUBLIC' | 'TEAM')}
          >
            <MenuItem value="PUBLIC">PUBLIC — доступен всем</MenuItem>
            <MenuItem value="TEAM">TEAM — только команде</MenuItem>
          </TextField>
        </Stack>
        <TextField
          select
          label="Категория"
          value={category}
          sx={{ maxWidth: 240 }}
          onChange={(e) => setCategory(e.target.value)}
        >
          <MenuItem value="">—</MenuItem>
          {(categories ?? []).map((c) => (
            <MenuItem key={c.slug} value={c.slug}>{c.name}</MenuItem>
          ))}
        </TextField>
        <TextField
          label="Описание"
          value={description}
          multiline
          minRows={3}
          onChange={(e) => setDescription(e.target.value)}
        />
        <Autocomplete
          multiple
          freeSolo
          options={[]}
          value={tags}
          onChange={(_, newValue) => setTags(newValue as string[])}
          renderInput={(params) => (
            <TextField {...params} label="Теги" placeholder="Введите тег и нажмите Enter" />
          )}
        />
        <Stack direction="row" spacing={2} alignItems="center">
          <Button variant="outlined" component="label">
            Выбрать файл
            <input
              hidden
              type="file"
              data-testid="version-file"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
          </Button>
          <Typography variant="body2" color="text.secondary">
            {file ? file.name : 'Файл первой версии (например, .zip)'}
          </Typography>
        </Stack>
        <TextField label="Changelog" value={changelog} onChange={(e) => setChangelog(e.target.value)} />
        <Box>
          <Button
            variant="contained"
            startIcon={<UploadIcon />}
            onClick={handleSubmit}
            disabled={!canSubmit || submitMutation.isPending}
          >
            Опубликовать
          </Button>
        </Box>
      </Stack>
    </Paper>
  );
}
