import { Box, Chip, Typography, Stack } from '@mui/material';
import type { CategoryResponse } from '../types';

export default function FiltersSidebar({ facetsByType, categories, type, category, onChange }: {
  facetsByType: Record<string, number>;
  categories: CategoryResponse[];
  type: string | null;
  category: string | null;
  onChange: (next: { type?: string | null; category?: string | null }) => void;
}) {
  return (
    <Stack spacing={2}>
      <Box>
        <Typography variant="subtitle2" sx={{ mb: 1 }}>Тип</Typography>
        <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
          <Chip label="Все" onClick={() => onChange({ type: null })} color={type === null ? 'primary' : 'default'} />
          {Object.entries(facetsByType).map(([t, count]) => (
            <Chip
              key={t}
              label={`${t} (${count})`}
              onClick={() => onChange({ type: t })}
              color={type === t ? 'primary' : 'default'}
            />
          ))}
        </Stack>
      </Box>
      <Box>
        <Typography variant="subtitle2" sx={{ mb: 1 }}>Категории</Typography>
        <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
          <Chip label="Все" onClick={() => onChange({ category: null })} color={category === null ? 'primary' : 'default'} />
          {categories.map((c) => (
            <Chip
              key={c.slug}
              label={c.name}
              onClick={() => onChange({ category: c.slug })}
              color={category === c.slug ? 'primary' : 'default'}
            />
          ))}
        </Stack>
      </Box>
    </Stack>
  );
}
