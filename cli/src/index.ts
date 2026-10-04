#!/usr/bin/env node
import { Command } from 'commander';
import { register as registerSearch } from './commands/search';
import { register as registerLogin } from './commands/login';
import { register as registerWhoami } from './commands/whoami';
import { register as registerInstall } from './commands/install';

export function buildProgram(): Command {
  const program = new Command();
  program.name('skillhub').description('SkillHub CLI').version('0.1.0');
  registerSearch(program);
  registerLogin(program);
  registerWhoami(program);
  registerInstall(program);
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
