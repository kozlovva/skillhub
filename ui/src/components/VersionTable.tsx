import { Table, TableHead, TableRow, TableCell, TableBody, Button, Chip } from '@mui/material';
import type { VersionResponse } from '../types';

export default function VersionTable({ versions, onDownload }: {
  versions: VersionResponse[];
  onDownload: (version: string) => void;
}) {
  return (
    <Table size="small">
      <TableHead>
        <TableRow>
          <TableCell>Версия</TableCell>
          <TableCell>Статус</TableCell>
          <TableCell>Changelog</TableCell>
          <TableCell>Размер</TableCell>
          <TableCell />
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
              <Button size="small" onClick={() => onDownload(v.version)}>Скачать</Button>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
