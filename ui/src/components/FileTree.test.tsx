import { render, screen } from '@testing-library/react';
import FileTree from './FileTree';

test('renders nested files with indentation', () => {
  render(
    <FileTree
      files={[
        { path: 'SKILL.md', size: 10 },
        { path: 'scripts/run.sh', size: 20 },
        { path: 'scripts/lib/util.sh', size: 5 },
      ]}
      onOpenFile={vi.fn()}
    />
  );
  expect(screen.getByText('SKILL.md')).toBeInTheDocument();
  expect(screen.getByText('run.sh')).toBeInTheDocument();
  expect(screen.getByText('util.sh')).toBeInTheDocument();
});
