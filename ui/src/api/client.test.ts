import { describe, it, expect, vi, beforeEach } from 'vitest';
import axios from 'axios';

vi.mock('axios', () => {
  const instance = {
    get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  };
  const axiosMock = { create: vi.fn(() => instance), get: vi.fn() };
  return { default: axiosMock };
});

describe('api client', () => {
  beforeEach(() => vi.resetModules());

  it('creates axios instance with base URL from env', async () => {
    vi.stubEnv('VITE_API_URL', 'http://test:8080');
    const { api } = await import('./client');
    expect(axios.create).toHaveBeenCalledWith(
      expect.objectContaining({ baseURL: 'http://test:8080' })
    );
    expect(api).toBeDefined();
  });

  it('normalizes backend error to ApiError', async () => {
    const { toApiError } = await import('./client');
    const error = {
      response: {
        status: 409,
        data: { code: 'CONFLICT', message: 'Version exists', details: null },
      },
    };
    const apiError = toApiError(error);
    expect(apiError).toEqual({
      status: 409, code: 'CONFLICT', message: 'Version exists', details: null,
    });
  });
});
