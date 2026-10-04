import { describe, expect, it } from 'vitest';
import { errorExitCode } from '../src/index';
import { ApiError } from '../src/api';
import { ManifestError } from '../src/manifest';

describe('errorExitCode', () => {
  it('network/5xx → 2', () => {
    expect(errorExitCode(new ApiError(0, 'NETWORK_ERROR', 'x'))).toBe(2);
    expect(errorExitCode(new ApiError(500, 'INTERNAL', 'x'))).toBe(2);
  });

  it('client errors → 1', () => {
    expect(errorExitCode(new ApiError(404, 'NOT_FOUND', 'x'))).toBe(1);
    expect(errorExitCode(new ManifestError(['bad']))).toBe(1);
    expect(errorExitCode(new Error('bad'))).toBe(1);
  });
});
