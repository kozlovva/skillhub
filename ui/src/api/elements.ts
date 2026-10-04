import { api } from './client';
import type { ElementResponse, VersionResponse } from '../types';

export const elements = {
  async list(): Promise<ElementResponse[]> {
    return (await api.get<ElementResponse[]>('/api/elements')).data;
  },
  async get(slug: string): Promise<ElementResponse> {
    return (await api.get<ElementResponse>(`/api/elements/${slug}`)).data;
  },
  async create(body: {
    slug: string; type: string; name: string; description?: string;
    team?: string; category?: string; tags?: string[]; visibility: string;
  }): Promise<ElementResponse> {
    return (await api.post<ElementResponse>('/api/elements', body)).data;
  },
  async versions(slug: string): Promise<VersionResponse[]> {
    return (await api.get<VersionResponse[]>(`/api/elements/${slug}/versions`)).data;
  },
  async publishVersion(slug: string, file: File, changelog?: string): Promise<VersionResponse> {
    const form = new FormData();
    form.append('file', file);
    const params = changelog ? { changelog } : undefined;
    return (await api.post<VersionResponse>(`/api/elements/${slug}/versions`, form, { params })).data;
  },
  downloadVersionUrl(slug: string, version: string): string {
    const base = import.meta.env.VITE_API_URL ?? '';
    return `${base}/api/elements/${slug}/versions/${version}/download`;
  },
  downloadFileUrl(slug: string, version: string, path: string): string {
    const base = import.meta.env.VITE_API_URL ?? '';
    return `${base}/api/elements/${slug}/versions/${version}/files?path=${encodeURIComponent(path)}`;
  },
};
