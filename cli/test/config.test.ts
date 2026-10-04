import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { loadConfig, resolveConfig, saveConfig } from '../src/config';

const tmp = mkdtempSync(join(tmpdir(), 'skillhub-test-'));
const cfgPath = join(tmp, 'config.json');
afterEach(() => rmSync(cfgPath, { force: true }));

describe('config', () => {
  it('returns null when file missing', () => {
    expect(loadConfig(join(tmp, 'nope.json'))).toBeNull();
  });

  it('saves and loads config', () => {
    saveConfig({ url: 'https://sh.local', token: 'skh_abc' }, cfgPath);
    expect(loadConfig(cfgPath)).toEqual({ url: 'https://sh.local', token: 'skh_abc' });
  });

  it('env overrides file', () => {
    saveConfig({ url: 'https://file.local', token: 'skh_file' }, cfgPath);
    process.env.SKILLHUB_URL = 'https://env.local';
    process.env.SKILLHUB_TOKEN = 'skh_env';
    expect(resolveConfig(cfgPath)).toEqual({ url: 'https://env.local', token: 'skh_env' });
    delete process.env.SKILLHUB_URL;
    delete process.env.SKILLHUB_TOKEN;
    expect(resolveConfig(cfgPath)).toEqual({ url: 'https://file.local', token: 'skh_file' });
  });

  it('falls back to default url', () => {
    expect(resolveConfig(join(tmp, 'nope.json')).url).toBe('http://localhost:8080');
  });
});
