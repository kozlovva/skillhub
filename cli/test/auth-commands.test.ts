import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterAll, describe, expect, it, vi } from 'vitest';
import { runWhoami } from '../src/commands/whoami';
import { ApiClient } from '../src/api';

const tmp = mkdtempSync(join(tmpdir(), 'skillhub-auth-'));
afterAll(() => rmSync(tmp, { recursive: true, force: true }));

describe('whoami', () => {
  it('returns MeInfo from API', async () => {
    const meMock = vi.fn().mockResolvedValue({ username: 'vladi', admin: false, teams: [] });
    const spy = vi.spyOn(ApiClient.prototype, 'me').mockImplementation(meMock);
    const me = await runWhoami({ url: 'http://x', token: 'skh_t' });
    expect(me.username).toBe('vladi');
    spy.mockRestore();
  });
});
