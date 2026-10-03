import { Card, CardContent, Typography, Chip, Stack } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import type { ElementResponse } from '../types';
import RatingBadge from './RatingBadge';

export default function ElementCard({ element, avgRating, ratingCount }: {
  element: ElementResponse;
  avgRating?: number;
  ratingCount?: number;
}) {
  return (
    <Card
      component={RouterLink}
      to={`/elements/${element.slug}`}
      sx={{ textDecoration: 'none', mb: 1.5, '&:hover': { boxShadow: 6 } }}
    >
      <CardContent>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
          <Typography variant="h6">{element.name}</Typography>
          <Chip size="small" label={element.type} color="primary" variant="outlined" />
          {element.visibility === 'TEAM' && <Chip size="small" label="team-only" />}
        </Stack>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          {element.description}
        </Typography>
        <Stack direction="row" spacing={2} alignItems="center">
          <Typography variant="caption">команда: {element.team}</Typography>
          {element.latestVersion && (
            <Typography variant="caption">v{element.latestVersion}</Typography>
          )}
          <Typography variant="caption">↓ {element.downloadsCount}</Typography>
          <RatingBadge avg={avgRating ?? 0} count={ratingCount ?? 0} />
        </Stack>
      </CardContent>
    </Card>
  );
}
