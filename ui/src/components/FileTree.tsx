import { List, ListItemButton, ListItemText, Typography } from '@mui/material';
import InsertDriveFileIcon from '@mui/icons-material/InsertDriveFile';
import type { FileDto } from '../types';

export default function FileTree({ files, onOpenFile }: {
  files: FileDto[];
  onOpenFile: (path: string) => void;
}) {
  return (
    <List dense>
      {files.map((f) => {
        const parts = f.path.split('/');
        const depth = parts.length - 1;
        return (
          <ListItemButton
            key={f.path}
            onClick={() => onOpenFile(f.path)}
            sx={{ pl: 2 + depth * 2 }}
          >
            <InsertDriveFileIcon sx={{ mr: 1, fontSize: 16 }} />
            <ListItemText
              primary={parts[parts.length - 1]}
              secondary={depth > 0 ? f.path : undefined}
            />
            <Typography variant="caption">{f.size} B</Typography>
          </ListItemButton>
        );
      })}
    </List>
  );
}
