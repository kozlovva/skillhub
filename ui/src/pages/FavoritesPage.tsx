import { Navigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Typography, Paper, Stack } from '@mui/material';
import { me as meApi } from '../api/me';
import { useAuth } from '../auth/KeycloakProvider';
import ElementCard from '../components/ElementCard';
import PageHeader from '../components/PageHeader';

export default function FavoritesPage() {
  const { authenticated } = useAuth();
  const { data: favorites } = useQuery({
    queryKey: ['favorites'],
    queryFn: meApi.favorites,
    enabled: authenticated,
  });

  if (!authenticated) return <Navigate to="/" replace />;

  return (
    <Stack spacing={3}>
      <PageHeader title="Избранное" />
      <Paper sx={{ p: 3 }}>
        {(favorites ?? []).length === 0 ? (
          <Typography color="text.secondary">Пока ничего в избранном</Typography>
        ) : (
          favorites!.map((e) => <ElementCard key={e.slug} element={e} />)
        )}
      </Paper>
    </Stack>
  );
}
