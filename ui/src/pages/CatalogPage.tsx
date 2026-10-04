import { useState, useEffect, useRef } from 'react';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import {
  TextField, InputAdornment, Typography, Box, Stack, Skeleton, Paper,
  Chip, Collapse, IconButton, useScrollTrigger, Divider,
  Button, Menu, MenuItem, ListItemIcon,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import SearchOffIcon from '@mui/icons-material/SearchOff';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import CheckIcon from '@mui/icons-material/Check';
import { search } from '../api/search';
import { categories as categoriesApi } from '../api/categories';
import { useTheme } from '@mui/material/styles';
import ElementCard from '../components/ElementCard';

function ChipGroup({ title, chips }: {
  title: string;
  chips: { key: string; label: string; selected: boolean; onClick: () => void }[];
}) {
  return (
    <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
      <Typography variant="overline" sx={{ color: 'text.secondary' }}>{title}</Typography>
      {chips.map((c) => (
        <Chip
          key={c.key}
          label={c.label}
          onClick={c.onClick}
          color={c.selected ? 'primary' : 'default'}
          size="small"
          variant={c.selected ? 'filled' : 'outlined'}
        />
      ))}
    </Stack>
  );
}

type SortOption = 'relevance' | 'rating' | 'published';

const sortLabels: Record<SortOption, string> = {
  relevance: 'Популярность',
  rating: 'Рейтинг',
  published: 'Дата',
};

export default function CatalogPage() {
  const theme = useTheme();
  const [q, setQ] = useState('');
  const [debouncedQ, setDebouncedQ] = useState('');
  const [type, setType] = useState<string | null>(null);
  const [category, setCategory] = useState<string | null>(null);
  const [sort, setSort] = useState<SortOption>('relevance');
  const [order, setOrder] = useState<'asc' | 'desc'>('desc');
  const [sortAnchor, setSortAnchor] = useState<HTMLElement | null>(null);
  const searchRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const t = setTimeout(() => setDebouncedQ(q), 300);
    return () => clearTimeout(t);
  }, [q]);

  const scrolled = useScrollTrigger({
    disableHysteresis: true,
    threshold: 96,
  });

  const { data, isPending } = useQuery({
    queryKey: ['search', debouncedQ, type, category, sort, order],
    queryFn: () => search.search({
      q: debouncedQ,
      type: type ?? undefined,
      category: category ?? undefined,
      sort: sort === 'relevance' ? undefined : sort,
      order: sort === 'relevance' ? undefined : order,
    }),
    placeholderData: keepPreviousData,
  });

  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: categoriesApi.list,
  });

  const focusSearch = () => {
    window.scrollTo({ top: 0, behavior: 'smooth' });
    const input = searchRef.current?.querySelector('input');
    input?.focus({ preventScroll: true });
  };

  const typeChips = [
    { key: 'all', label: 'Все', selected: type === null, onClick: () => setType(null) },
    ...Object.entries(data?.facetsByType ?? {}).map(([t, count]) => ({
      key: t, label: `${t} (${count})`, selected: type === t, onClick: () => setType(t),
    })),
  ];

  const categoryChips = [
    { key: 'all', label: 'Все', selected: category === null, onClick: () => setCategory(null) },
    ...(categories ?? []).map((c) => ({
      key: c.slug, label: c.name, selected: category === c.slug, onClick: () => setCategory(c.slug),
    })),
  ];

  const categoryNames = Object.fromEntries((categories ?? []).map((c) => [c.slug, c.name]));

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4" sx={{ mb: 0.5 }}>Каталог скилов</Typography>
        <Typography color="text.secondary">
          {data ? `Найдено: ${data.total}` : 'Библиотека элементов для вашей команды'}
        </Typography>
      </Box>

      <Box
        sx={{
          position: 'sticky',
          top: { xs: 56, md: 64 },
          zIndex: 3,
          py: 1,
          bgcolor: 'background.default',
          borderBottom: scrolled ? `1px solid ${theme.palette.divider}` : '1px solid transparent',
          transition: 'border-color 200ms ease',
        }}
      >
        <Collapse in={!scrolled}>
          <TextField
            ref={searchRef}
            fullWidth
            placeholder="Поиск"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            sx={{
              bgcolor: 'background.paper',
              mb: 1.5,
            }}
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon sx={{ color: 'text.secondary' }} />
                  </InputAdornment>
                ),
                endAdornment: (
                  <InputAdornment position="end">
                    <Button
                      size="small"
                      aria-haspopup="menu"
                      aria-expanded={sortAnchor ? 'true' : undefined}
                      aria-label={`Настройка сортировки: ${sortLabels[sort]}`}
                      endIcon={sort !== 'relevance' ? (
                        order === 'desc'
                          ? <ArrowDownwardIcon aria-hidden="true" />
                          : <ArrowUpwardIcon aria-hidden="true" />
                      ) : undefined}
                      onClick={(e) => setSortAnchor(e.currentTarget)}
                      sx={{ minWidth: 0 }}
                    >
                      {sortLabels[sort]}
                    </Button>
                  </InputAdornment>
                ),
              },
            }}
          />
        </Collapse>
        <Menu
          anchorEl={sortAnchor}
          open={sortAnchor !== null}
          onClose={() => setSortAnchor(null)}
        >
          <MenuItem
            selected={sort === 'relevance'}
            onClick={() => { setSort('relevance'); setSortAnchor(null); }}
          >
            {sort === 'relevance' && (
              <ListItemIcon><CheckIcon fontSize="small" /></ListItemIcon>
            )}
            Популярность
          </MenuItem>
          <MenuItem
            selected={sort === 'rating'}
            onClick={() => { setSort('rating'); setSortAnchor(null); }}
          >
            {sort === 'rating' && (
              <ListItemIcon><CheckIcon fontSize="small" /></ListItemIcon>
            )}
            Рейтинг
          </MenuItem>
          <MenuItem
            selected={sort === 'published'}
            onClick={() => { setSort('published'); setSortAnchor(null); }}
          >
            {sort === 'published' && (
              <ListItemIcon><CheckIcon fontSize="small" /></ListItemIcon>
            )}
            Дата
          </MenuItem>
          {sort !== 'relevance' && <Divider />}
          {sort !== 'relevance' && (
            <MenuItem
              selected={order === 'desc'}
              onClick={() => { setOrder('desc'); setSortAnchor(null); }}
            >
              {order === 'desc' && (
                <ListItemIcon><CheckIcon fontSize="small" /></ListItemIcon>
              )}
              По убыванию
            </MenuItem>
          )}
          {sort !== 'relevance' && (
            <MenuItem
              selected={order === 'asc'}
              onClick={() => { setOrder('asc'); setSortAnchor(null); }}
            >
              {order === 'asc' && (
                <ListItemIcon><CheckIcon fontSize="small" /></ListItemIcon>
              )}
              По возрастанию
            </MenuItem>
          )}
        </Menu>
        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
          {scrolled && (
            <IconButton
              onClick={focusSearch}
              aria-label="Поиск"
              size="small"
              sx={{
                border: `1px solid ${theme.palette.divider}`,
                borderRadius: 1,
                bgcolor: 'background.paper',
              }}
            >
              <SearchIcon fontSize="small" />
            </IconButton>
          )}
          <ChipGroup title="Тип" chips={typeChips} />
          <Divider orientation="vertical" flexItem sx={{ mx: 1, alignSelf: 'stretch', my: 0.5 }} />
          <ChipGroup title="Категория" chips={categoryChips} />
        </Stack>
      </Box>

      <Box>
        {isPending && (
          <Stack spacing={1.5}>
            {[0, 1, 2].map((i) => (
              <Skeleton key={i} variant="rounded" height={110} sx={{ }} />
            ))}
          </Stack>
        )}
        {!isPending && data && data.items.length === 0 && (
          <Paper
            variant="outlined"
            sx={{ p: 6, textAlign: 'center', borderStyle: 'dashed' }}
          >
            <SearchOffIcon sx={{ fontSize: 48, color: 'text.secondary', mb: 1 }} />
            <Typography variant="h6">Ничего не найдено</Typography>
            <Typography variant="body2" color="text.secondary">
              Попробуйте изменить запрос или сбросить фильтры
            </Typography>
          </Paper>
        )}
        {!isPending && data && data.items.map((el) => (
          <ElementCard
            key={el.slug}
            element={el}
            avgRating={el.avgRating ?? undefined}
            ratingCount={el.ratingCount ?? undefined}
            categoryNames={categoryNames}
          />
        ))}
      </Box>
    </Stack>
  );
}
