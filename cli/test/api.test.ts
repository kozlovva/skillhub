import { describe, expect, it, vi } from 'vitest';
import { ApiClient, ApiError } from '../src/api';

function jsonRes(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

describe('ApiClient', () => {
  it('sends Bearer token and parses search result', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonRes({ items: [{ slug: 'pdf-export', type: 'SKILL', name: 'PDF', downloadsCount: 3 }], total: 1 })
    );
    const client = new ApiClient('http://x', 'skh_tok', fetchMock as unknown as typeof fetch);
    const result = await client.search('pdf', { limit: 5 });
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('http://x/api/search?q=pdf&limit=5');
    expect((init.headers as Record<string, string>)['Authorization']).toBe('Bearer skh_tok');
    expect(result.items[0].slug).toBe('pdf-export');
  });

  it('throws ApiError with backend code/message', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonRes({ code: 'VERSION_CONFLICT', message: 'exists' }, 409));
    const client = new ApiClient('http://x', 'skh_tok', fetchMock as unknown as typeof fetch);
    await expect(client.getElement('dup')).rejects.toMatchObject({
      status: 409, code: 'VERSION_CONFLICT', message: 'exists',
    });
  });

  it('wraps network failure as ApiError status 0', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new Error('ECONNREFUSED'));
    const client = new ApiClient('http://x', undefined, fetchMock as unknown as typeof fetch);
    await expect(client.me()).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' });
  });

  it('publishVersion posts multipart with file field', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonRes({ version: '1.0.0' }, 201));
    const client = new ApiClient('http://x', 'skh_tok', fetchMock as unknown as typeof fetch);
    const v = await client.publishVersion('my-skill', Buffer.from('zip'), 'first');
    expect(v.version).toBe('1.0.0');
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(init.method).toBe('POST');
    expect(init.body).toBeInstanceOf(FormData);
    expect((init.body as FormData).get('file')).not.toBeNull();
    expect((init.body as FormData).get('changelog')).toBe('first');
  });

  it('downloadElementArchive returns bytes (fetch follows 302)', async () => {
    const zip = new Uint8Array([1, 2, 3]);
    const fetchMock = vi.fn().mockResolvedValue(new Response(zip, { status: 200 }));
    const client = new ApiClient('http://x', 'skh_tok', fetchMock as unknown as typeof fetch);
    const buf = await client.downloadElementArchive('s', '1.0.0');
    expect(Buffer.from(buf).equals(Buffer.from([1, 2, 3]))).toBe(true);
  });
});
