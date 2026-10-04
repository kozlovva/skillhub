import { describe, expect, it } from 'vitest';
import { buildProgram } from '../src/index';

describe('skillhub CLI', () => {
  it('registers commands', () => {
    const program = buildProgram();
    const names = program.commands.map((c) => c.name());
    expect(names).toContain('install');
    expect(names).toContain('publish');
  });
});
