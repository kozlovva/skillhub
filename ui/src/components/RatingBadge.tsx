import { Chip } from '@mui/material';
import StarIcon from '@mui/icons-material/Star';

export default function RatingBadge({ avg, count }: { avg: number; count: number }) {
  if (count === 0) return <Chip size="small" label="Нет оценок" />;
  return (
    <Chip
      size="small"
      icon={<StarIcon />}
      label={`${avg.toFixed(1)} (${count})`}
    />
  );
}
