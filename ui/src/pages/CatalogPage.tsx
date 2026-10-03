import { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { TextField, CircularProgress, Typography, Box, Stack } from '@mui/material';
import { search } from '../api/search';
import { categories as categoriesApi } from '../api/categories';
import ElementCard from '../components/ElementCard';
import FiltersSidebar from '../components/FiltersSidebar';

export default function CatalogPage() {
  const [q, setQ] = useState('');
  const [debouncedQ, setDebouncedQ] = useState('');
  const [type, setType] = useState<string | null>(null);
  const [category, setCategory] = useState<string | null>(null);

  useEffect(() => {
    const t = setTimeout(() => setDebouncedQ(q), 300);
    return () => clearTimeout(t);
  }, [q]);

  const { data, isLoading } = useQuery({
    queryKey: ['search', debouncedQ, type, category],
    queryFn: () => search.search({ q: debouncedQ, type: type ?? undefined, category: category ?? undefined }),
  });

  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: categoriesApi.list,
  });

  return (
    <>
      <TextField
        fullWidth
        placeholder="Поиск"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        sx={{ mb: 2 }}
      />
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={3}>
        <Box sx={{ width: { xs: '100%', md: 260 }, flexShrink: 0 }}>
          <FiltersSidebar
            facetsByType={data?.facetsByType ?? {}}
            categories={categories ?? []}
            type={type}
            category={category}
            onChange={(next) => {
              if ('type' in next) setType(next.type ?? null);
              if ('category' in next) setCategory(next.category ?? null);
            }}
          />
        </Box>
        <Box sx={{ flexGrow: 1 }}>
          {isLoading && <CircularProgress />}
          {data && data.items.length === 0 && (
            <Typography color="text.secondary">Ничего не найдено</Typography>
          )}
          <Box>
            {data?.items.map((el) => (
              <ElementCard key={el.slug} element={el} />
            ))}
          </Box>
        </Box>
      </Stack>
    </>
  );
}
