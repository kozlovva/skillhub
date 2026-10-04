#!/usr/bin/env node
import { Command } from 'commander';

export function buildProgram(): Command {
  const program = new Command();
  program.name('skillhub').description('SkillHub CLI').version('0.1.0');
  program
    .command('install')
    .argument('<ref>', 'element or pack slug[@version]')
    .action(async () => {
      console.error('Not implemented yet');
      process.exit(1);
    });
  program
    .command('publish')
    .argument('<dir>', 'directory with manifest.json')
    .action(async () => {
      console.error('Not implemented yet');
      process.exit(1);
    });
  return program;
}

if (require.main === module) {
  buildProgram().parseAsync(process.argv).catch((err: Error) => {
    console.error(err.message ?? err);
    process.exit(1);
  });
}
