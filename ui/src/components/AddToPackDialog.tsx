import { useState } from 'react';
import {
  Dialog, DialogTitle, DialogContent, DialogActions, Button,
  TextField, MenuItem, FormControl, InputLabel, Select,
} from '@mui/material';
import type { ElementResponse } from '../types';

export default function AddToPackDialog({ elements, open, onClose, onAdd }: {
  elements: ElementResponse[];
  open: boolean;
  onClose: () => void;
  onAdd: (element: string, versionConstraint: string) => void;
}) {
  const [element, setElement] = useState(elements[0]?.slug ?? '');
  const [constraint, setConstraint] = useState('latest');

  return (
    <Dialog open={open} onClose={onClose}>
      <DialogTitle>Добавить элемент в пак</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 400, mt: 1 }}>
        <FormControl fullWidth size="small">
          <InputLabel>Элемент</InputLabel>
          <Select
            value={element}
            label="Элемент"
            onChange={(e) => {
              setElement(e.target.value);
              const el = elements.find((x) => x.slug === e.target.value);
              setConstraint(el?.latestVersion ?? 'latest');
            }}
          >
            {elements.map((el) => (
              <MenuItem key={el.slug} value={el.slug}>
                {el.name} ({el.slug})
              </MenuItem>
            ))}
          </Select>
        </FormControl>
        <TextField
          size="small"
          label="Версия (latest или точная)"
          value={constraint}
          onChange={(e) => setConstraint(e.target.value)}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Отмена</Button>
        <Button
          onClick={() => onAdd(element, constraint)}
          disabled={!element || !constraint.trim()}
        >
          Добавить
        </Button>
      </DialogActions>
    </Dialog>
  );
}
