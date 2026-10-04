import type { ReactNode } from 'react';
import { Box, Typography } from '@mui/material';

interface PageHeaderProps {
  title: string;
  subtitle?: ReactNode;
}

export default function PageHeader({ title, subtitle }: PageHeaderProps) {
  return (
    <Box>
      <Typography variant="h4" component="h1" sx={{ mb: 0.5 }}>
        {title}
      </Typography>
      {subtitle && <Typography color="text.secondary">{subtitle}</Typography>}
    </Box>
  );
}
