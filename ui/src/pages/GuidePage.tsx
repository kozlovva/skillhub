import type { ReactNode } from 'react';
import ReactMarkdown, { type Components } from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Stack, Link, Paper, Typography, Box } from '@mui/material';
import guide from '../content/user-guide.md?raw';
import PageHeader from '../components/PageHeader';

const heading =
  (variant: 'h4' | 'h5' | 'h6', component: 'h1' | 'h2' | 'h3') =>
  ({ children }: { children?: ReactNode }) => (
    <Typography variant={variant} component={component} sx={{ mt: 4, mb: 1.5 }} gutterBottom>
      {children}
    </Typography>
  );

const components: Components = {
  h2: heading('h5', 'h2'),
  h3: heading('h6', 'h3'),
  p: ({ children }) => (
    <Typography variant="body1" sx={{ mb: 1.5 }}>
      {children}
    </Typography>
  ),
  a: ({ href, children }) => (
    <Link href={href} target="_blank" rel="noreferrer">
      {children}
    </Link>
  ),
  code: ({ className, children }) =>
    /language-/.test(className ?? '') ? (
      <code className={className}>{children}</code>
    ) : (
      <Box
        component="code"
        sx={{
          fontFamily: 'monospace',
          fontSize: '0.875em',
          bgcolor: 'action.hover',
          borderRadius: 0.5,
          px: 0.5,
          py: 0.25,
          wordBreak: 'break-word',
        }}
      >
        {children}
      </Box>
    ),
  pre: ({ children }) => (
    <Paper
      variant="outlined"
      sx={{
        mb: 2,
        bgcolor: '#23272d',
        color: '#ece9e2',
        borderRadius: 1,
      }}
    >
      <Box component="pre" sx={{ m: 0, p: 2, overflowX: 'auto', fontFamily: 'monospace', fontSize: 14 }}>
        {children}
      </Box>
    </Paper>
  ),
  table: ({ children }) => (
    <Box component="table" sx={{ display: 'table', width: '100%', mb: 2, borderCollapse: 'collapse' }}>
      {children}
    </Box>
  ),
  th: ({ children }) => (
    <Box
      component="th"
      sx={{
        border: 1,
        borderColor: 'divider',
        p: 1,
        textAlign: 'left',
        bgcolor: 'action.hover',
        fontWeight: 600,
      }}
    >
      {children}
    </Box>
  ),
  td: ({ children }) => (
    <Box component="td" sx={{ border: 1, borderColor: 'divider', p: 1, verticalAlign: 'top' }}>
      {children}
    </Box>
  ),
};

export default function GuidePage() {
  return (
    <Stack spacing={3}>
      <PageHeader title="Инструкция" />
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
        {guide}
      </ReactMarkdown>
    </Stack>
  );
}
