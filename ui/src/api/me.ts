import { api } from './client';
import type { ElementResponse } from '../types';

export interface MeTeamRole {
  slug: string;
  name: string;
  role: string;
}

export interface MeResponse {
  username: string;
  userId: string;
  admin: boolean;
  teams: MeTeamRole[];
}

export const me = {
  async get(): Promise<MeResponse> {
    return (await api.get<MeResponse>('/api/me')).data;
  },

  async favorites(): Promise<ElementResponse[]> {
    return (await api.get<ElementResponse[]>('/api/me/favorites')).data;
  },
};
