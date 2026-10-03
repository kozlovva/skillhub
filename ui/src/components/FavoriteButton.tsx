import { IconButton } from '@mui/material';
import FavoriteIcon from '@mui/icons-material/Favorite';
import FavoriteBorderIcon from '@mui/icons-material/FavoriteBorder';

export default function FavoriteButton({ favorited, onToggle }: {
  favorited: boolean;
  onToggle: () => void;
}) {
  return (
    <IconButton onClick={onToggle} aria-label="favorite">
      {favorited ? <FavoriteIcon color="error" /> : <FavoriteBorderIcon />}
    </IconButton>
  );
}
