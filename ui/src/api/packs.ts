import { api } from './client';
import type { PackResponse } from '../types';

export const packs = {
  async get(slug: string): Promise<PackResponse> {
    return (await api.get<PackResponse>(`/api/packs/${slug}`)).data;
  },
  async addContent(slug: string, element: string, versionConstraint: string): Promise<PackResponse> {
    return (await api.post<PackResponse>(`/api/packs/${slug}/contents`, { element, versionConstraint })).data;
  },
  downloadPackUrl(slug: string): string {
    const base = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
    return `${base}/api/packs/${slug}/versions/latest/download`;
  },
};
