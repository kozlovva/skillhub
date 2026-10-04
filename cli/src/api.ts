export interface ElementInfo {
  slug: string;
  type: string;
  name: string;
  description?: string | null;
  team?: string | null;
  category?: string | null;
  tags?: string[] | null;
  visibility: string;
  latestVersion?: string | null;
  downloadsCount: number;
  avgRating?: number | null;
  ratingCount?: number | null;
}

export interface MeInfo {
  username: string;
  admin: boolean;
  teams: { slug: string; name: string; role: string }[];
}

export interface SearchResult {
  items: ElementInfo[];
  total: number;
}

export interface VersionInfo {
  version: string;
  status?: string;
  changelog?: string | null;
}

export interface CreateElementInput {
  slug: string;
  type: string;
  name: string;
  description?: string;
  team?: string;
  category?: string;
  tags?: string[];
  visibility?: string;
}

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
  ) {
    super(message);
  }
}

export class ApiClient {
  constructor(
    private readonly baseUrl: string,
    private readonly token?: string,
    private readonly fetchImpl: typeof fetch = fetch,
  ) {}

  private async requestRaw(path: string, init: RequestInit = {}): Promise<Response> {
    const headers: Record<string, string> = {
      ...((init.headers as Record<string, string>) ?? {}),
    };
    if (this.token) headers['Authorization'] = `Bearer ${this.token}`;
    let res: Response;
    try {
      res = await this.fetchImpl(this.baseUrl + path, { ...init, headers });
    } catch {
      throw new ApiError(0, 'NETWORK_ERROR', `Cannot reach SkillHub at ${this.baseUrl}`);
    }
    if (!res.ok) {
      let code = 'UNKNOWN';
      let message = res.statusText || `HTTP ${res.status}`;
      try {
        const body = (await res.json()) as { code?: string; message?: string };
        if (body.code) code = body.code;
        if (body.message) message = body.message;
      } catch {
        // body is not JSON (plain text / empty) — keep defaults
      }
      throw new ApiError(res.status, code, message);
    }
    return res;
  }

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const res = await this.requestRaw(path, init);
    if (res.status === 204) return undefined as T;
    return (await res.json()) as T;
  }

  me(): Promise<MeInfo> {
    return this.request('/api/me');
  }

  search(q: string, opts: { type?: string; limit?: number; offset?: number } = {}): Promise<SearchResult> {
    const p = new URLSearchParams({ q });
    if (opts.type) p.set('type', opts.type);
    if (opts.limit !== undefined) p.set('limit', String(opts.limit));
    if (opts.offset !== undefined) p.set('offset', String(opts.offset));
    return this.request(`/api/search?${p.toString()}`);
  }

  getElement(slug: string): Promise<ElementInfo> {
    return this.request(`/api/elements/${encodeURIComponent(slug)}`);
  }

  createElement(input: CreateElementInput): Promise<ElementInfo> {
    return this.request('/api/elements', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    });
  }

  publishVersion(slug: string, zip: Buffer, changelog?: string): Promise<VersionInfo> {
    const form = new FormData();
    form.append('file', new Blob([new Uint8Array(zip)]), `${slug}.zip`);
    if (changelog) form.append('changelog', changelog);
    return this.request(`/api/elements/${encodeURIComponent(slug)}/versions`, {
      method: 'POST',
      body: form,
    });
  }

  async downloadElementArchive(slug: string, version: string): Promise<Buffer> {
    const res = await this.requestRaw(
      `/api/elements/${encodeURIComponent(slug)}/versions/${encodeURIComponent(version)}/download`,
    );
    return Buffer.from(await res.arrayBuffer());
  }

  async downloadPackArchive(slug: string, version: string): Promise<Buffer> {
    const res = await this.requestRaw(
      `/api/packs/${encodeURIComponent(slug)}/versions/${encodeURIComponent(version)}/download`,
    );
    return Buffer.from(await res.arrayBuffer());
  }
}
