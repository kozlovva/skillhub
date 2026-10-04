#!/usr/bin/env node
import { Command } from 'commander';
import { ApiError } from './api';
import { ManifestError } from './manifest';
import { register as registerSearch } from './commands/search';
import { register as registerLogin } from './commands/login';
import { register as registerWhoami } from './commands/whoami';
import { register as registerInstall } from './commands/install';
import { register as registerPublish } from './commands/publish';

export function errorExitCode(err: unknown): 1 | 2 {
  if (err instanceof ApiError) {
    return err.status === 0 || err.status >= 500 ? 2 : 1;
  }
  return 1;
}

export function buildProgram(): Command {
  const program = new Command();
  program.name('skillhub').description('SkillHub CLI').version('0.1.0');
  registerSearch(program);
  registerLogin(program);
  registerWhoami(program);
  registerInstall(program);
  registerPublish(program);
  return program;
}

if (require.main === module) {
  buildProgram()
    .parseAsync(process.argv)
    .catch((err: unknown) => {
      if (err instanceof ApiError) {
        console.error(`${err.message} [${err.code}${err.status ? ` HTTP ${err.status}` : ''}]`);
      } else if (err instanceof ManifestError) {
        console.error(err.message);
      } else {
        console.error(err instanceof Error ? err.message : String(err));
      }
      process.exit(errorExitCode(err));
    });
}
