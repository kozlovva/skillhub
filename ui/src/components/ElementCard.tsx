import { Card, CardContent, Typography, Chip, Stack, Box } from '@mui/material';
import DownloadIcon from '@mui/icons-material/Download';
import TagIcon from '@mui/icons-material/Tag';
import GroupsIcon from '@mui/icons-material/Groups';
import { Link as RouterLink } from 'react-router-dom';
import type { ElementResponse } from '../types';
import RatingBadge from './RatingBadge';

export default function ElementCard({ element, avgRating, ratingCount, categoryNames }: {
  element: ElementResponse;
  avgRating?: number;
  ratingCount?: number;
  categoryNames?: Record<string, string>;
}) {
  return (
    <Card
      component={RouterLink}
      to={`/elements/${element.slug}`}
      sx={{
        display: 'block',
        textDecoration: 'none',
        mb: 1.5,
        transition: 'box-shadow 200ms ease, transform 200ms ease',
        '&:hover': {
          boxShadow: 3,
          transform: 'translateY(-2px)',
          borderColor: 'primary.light',
        },
      }}
    >
      <CardContent>
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
          <Typography variant="h6" component="h3">{element.name}</Typography>
          <Typography variant="caption" sx={{ fontFamily: 'monospace', color: 'text.secondary' }}>
            {element.slug}
          </Typography>
          <Chip size="small" label={element.type} color="primary" variant="outlined" />
          {element.category && (
            <Chip
              size="small"
              label={categoryNames?.[element.category] ?? element.category}
              variant="outlined"
            />
          )}
          {element.visibility === 'TEAM' && <Chip size="small" label="team-only" color="warning" />}
        </Stack>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
          {element.description}
        </Typography>
        <Stack direction="row" spacing={2} alignItems="center" sx={{ color: 'text.secondary' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            <GroupsIcon sx={{ fontSize: 16 }} />
            <Typography variant="caption">{element.team ?? 'Личный'}</Typography>
          </Box>
          {element.latestVersion && (
            <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
              v{element.latestVersion}
            </Typography>
          )}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            <DownloadIcon sx={{ fontSize: 16 }} />
            <Typography variant="caption">{element.downloadsCount}</Typography>
          </Box>
          {element.tags.length > 0 && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
              <TagIcon sx={{ fontSize: 16 }} />
              <Typography variant="caption">{element.tags.slice(0, 3).join(', ')}</Typography>
            </Box>
          )}
          <Box sx={{ flexGrow: 1 }} />
          <RatingBadge avg={avgRating ?? 0} count={ratingCount ?? 0} />
        </Stack>
      </CardContent>
    </Card>
  );
}
