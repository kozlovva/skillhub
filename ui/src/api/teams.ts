import { api } from './client';
import type { TeamResponse, MemberCandidate, TeamMemberResponse } from '../types';

export const teams = {
  async list(): Promise<TeamResponse[]> {
    return (await api.get<TeamResponse[]>('/api/teams')).data;
  },
  async create(body: { slug: string; name: string }): Promise<TeamResponse> {
    return (await api.post<TeamResponse>('/api/teams', body)).data;
  },
  async searchCandidates(slug: string, q: string): Promise<MemberCandidate[]> {
    return (await api.get<MemberCandidate[]>(`/api/teams/${slug}/member-candidates`, { params: { q } })).data;
  },
  async addMember(slug: string, userId: string, role: string): Promise<void> {
    await api.post(`/api/teams/${slug}/members`, { userId, role });
  },
  async members(slug: string): Promise<TeamMemberResponse[]> {
    return (await api.get<TeamMemberResponse[]>(`/api/teams/${slug}/members`)).data;
  },
  async changeRole(slug: string, userId: string, role: string): Promise<void> {
    await api.patch(`/api/teams/${slug}/members/${userId}`, { role });
  },
  async removeMember(slug: string, userId: string): Promise<void> {
    await api.delete(`/api/teams/${slug}/members/${userId}`);
  },
};
