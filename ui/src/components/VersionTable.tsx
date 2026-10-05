import {
  Table, TableHead, TableRow, TableCell, TableBody, Button, Chip, IconButton, Tooltip, Stack,
} from '@mui/material';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import type { VersionResponse } from '../types';

export default function VersionTable({ versions, onDownload, onCopyInstall }: {
  versions: VersionResponse[];
  onDownload: (version: string) => void;
  onCopyInstall: (version: string) => void;
}) {
  return (
    <Table size="small">
      <TableHead>
        <TableRow>
          <TableCell>Версия</TableCell>
          <TableCell>Статус</TableCell>
          <TableCell>Changelog</TableCell>
          <TableCell>Размер</TableCell>
          <TableCell>Действия</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {versions.map((v) => (
          <TableRow key={v.version}>
            <TableCell>{v.version}</TableCell>
            <TableCell>
              <Chip
                size="small"
                label={v.status}
                color={v.status === 'PUBLISHED' ? 'success' : v.status === 'DEPRECATED' ? 'default' : 'warning'}
              />
            </TableCell>
            <TableCell>{v.changelog}</TableCell>
            <TableCell>{Math.round(v.sizeBytes / 1024)} KB</TableCell>
            <TableCell>
              <Stack direction="row" spacing={1} alignItems="center">
                <Button size="small" onClick={() => onDownload(v.version)}>Скачать</Button>
                <Tooltip title="Скопировать команду установки">
                  <IconButton
                    size="small"
                    aria-label="Скопировать команду установки"
                    onClick={() => onCopyInstall(v.version)}
                  >
                    <ContentCopyIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              </Stack>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
