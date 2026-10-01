import { api } from './client';
import type { TeamResponse } from '../types';

export const teams = {
  async list(): Promise<TeamResponse[]> {
    return (await api.get<TeamResponse[]>('/api/teams')).data;
  },
  async create(body: { slug: string; name: string }): Promise<TeamResponse> {
    return (await api.post<TeamResponse>('/api/teams', body)).data;
  },
  async addMember(slug: string, ssoSubject: string, role: string): Promise<void> {
    await api.post(`/api/teams/${slug}/members`, { ssoSubject, role });
  },
};
