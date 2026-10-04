import { useRef, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  Alert, Autocomplete, Box, Button, MenuItem, Paper, Stack, TextField, Typography,
} from '@mui/material';
import UploadIcon from '@mui/icons-material/Upload';
import { elements as elementsApi } from '../api/elements';
import { categories as categoriesApi } from '../api/categories';
import { me as meApi } from '../api/me';
import { toApiError } from '../api/client';
import { useSnackbar } from '../layout/SnackbarContext';
import { useAuth } from '../auth/KeycloakProvider';
import PageHeader from '../components/PageHeader';
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
  const [dragOver, setDragOver] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const { data: meInfo } = useQuery({
    queryKey: ['me'],
    queryFn: meApi.get,
    enabled: authenticated,
  });
  const { data: categories } = useQuery({ queryKey: ['categories'], queryFn: categoriesApi.list });

  const createdRef = useRef<Set<string>>(new Set());
  const stepRef = useRef<'create' | 'publish'>('create');

  const submitMutation = useMutation({
    mutationFn: async () => {
      if (!createdRef.current.has(slug)) {
        stepRef.current = 'create';
        await elementsApi.create({
          slug,
          type,
          name,
          description: description || undefined,
          team: team || undefined,
          category: category || undefined,
          tags,
          visibility,
        });
        createdRef.current.add(slug);
      }
      stepRef.current = 'publish';
      return elementsApi.publishVersion(slug, file as File, changelog || undefined);
    },
    onSuccess: () => {
      showSuccess('Элемент опубликован');
      navigate(`/elements/${slug}`);
    },
    onError: (e) => {
      const err = toApiError(e);
      if (stepRef.current === 'publish') {
        showError(`Элемент создан, но не удалось загрузить версию: ${err.message}`);
      } else if (err.status === 409) {
        setSlugError('Элемент с таким slug уже существует');
      } else {
        showError(err.message);
      }
    },
  });

  if (!authenticated) return <Navigate to="/" replace />;

  const canSubmit =
    !!name.trim() && SLUG_PATTERN.test(slug) && !!type
    && (!team || !!visibility) && !!file;
  const slugInvalid = slug.length > 0 && !SLUG_PATTERN.test(slug);

  const handleSubmit = () => {
    setSlugError(null);
    submitMutation.mutate();
  };

  return (
    <Stack spacing={3}>
      <PageHeader title="Загрузить элемент" />
      <Paper sx={{ p: 3, maxWidth: 720 }}>
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
            setSlugError(null);
          }}
          error={!!slugError || slugInvalid}
          helperText={
            slugError ??
            (slugInvalid ? 'Только строчные латинские буквы, цифры и дефисы' : undefined)
          }
        />
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
          <TextField
            select
            label="Тип"
            value={type}
            required
            onChange={(e) => setType(e.target.value as ElementType)}
          >
            {ELEMENT_TYPES.map((t) => (
              <MenuItem key={t} value={t}>{t}</MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label="Категория"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          >
            <MenuItem value="">—</MenuItem>
            {(categories ?? []).map((c) => (
              <MenuItem key={c.slug} value={c.slug}>{c.name}</MenuItem>
            ))}
          </TextField>
        </Box>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
          <TextField
            select
            label="Команда"
            value={team}
            onChange={(e) => {
              const next = e.target.value;
              setTeam(next);
              if (!next) setVisibility('PUBLIC');
            }}
          >
            <MenuItem value="">Без команды</MenuItem>
            {(meInfo?.teams ?? []).map((t) => (
              <MenuItem key={t.slug} value={t.slug}>{t.name}</MenuItem>
            ))}
          </TextField>
          <TextField
            select
            label="Видимость"
            value={visibility}
            required={!!team}
            onChange={(e) => setVisibility(e.target.value as 'PUBLIC' | 'TEAM')}
          >
            <MenuItem value="PUBLIC">PUBLIC — доступен всем</MenuItem>
            <MenuItem value="TEAM" disabled={!team}>TEAM — только команде</MenuItem>
          </TextField>
        </Box>
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
        <Box
          onClick={() => fileInputRef.current?.click()}
          onDragOver={(e) => {
            e.preventDefault();
            setDragOver(true);
          }}
          onDragLeave={() => setDragOver(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDragOver(false);
            const dropped = e.dataTransfer.files?.[0];
            if (dropped) setFile(dropped);
          }}
          sx={{
            border: 2,
            borderStyle: 'dashed',
            borderColor: dragOver ? 'primary.main' : 'divider',
            borderRadius: 1,
            p: 3,
            cursor: 'pointer',
            textAlign: 'center',
            bgcolor: dragOver ? 'action.hover' : 'transparent',
            '&:hover': { borderColor: 'primary.main' },
          }}
        >
          <input
            ref={fileInputRef}
            hidden
            type="file"
            data-testid="version-file"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          />
          <Stack alignItems="center" spacing={1}>
            <UploadIcon color={dragOver ? 'primary' : 'disabled'} />
            <Typography>
              {dragOver ? 'Отпустите файл здесь' : 'Перетащите файл сюда или нажмите для выбора'}
            </Typography>
            {file && (
              <Typography variant="body2" color="text.secondary">
                {file.name}
              </Typography>
            )}
          </Stack>
        </Box>
        <Alert severity="info">
          <Typography variant="subtitle2" gutterBottom>Требования к архиву</Typography>
          <Box component="ul" sx={{ m: 0, pl: 2.5, typography: 'body2' }}>
            <li>ZIP-архив до 50 МБ (распакованное содержимое до 200 МБ, не более 5000 файлов)</li>
            <li>
              В корне архива — <code>manifest.json</code> с обязательными полями:{' '}
              <code>name</code> и <code>version</code> (semver, например 1.2.3)
            </li>
            <li>Необязательные поля манифеста: <code>description</code>, <code>type</code></li>
            <li>Повторная загрузка той же версии элемента вернёт ошибку</li>
          </Box>
          <Box
            component="pre"
            sx={{ mt: 1, mb: 0, p: 1, borderRadius: 1, bgcolor: 'action.hover', overflowX: 'auto', typography: 'body2' }}
          >
{`{
  "name": "my-skill",
  "version": "1.0.0",
  "description": "Описание",
  "type": "SKILL"
}`}
          </Box>
        </Alert>
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
    </Stack>
  );
}
