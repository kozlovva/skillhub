import { api } from './client';

export interface MeTeamRole {
  slug: string;
  name: string;
  role: string;
}

export interface MeResponse {
  username: string;
  admin: boolean;
  teams: MeTeamRole[];
}

export const me = {
  async get(): Promise<MeResponse> {
    return (await api.get<MeResponse>('/api/me')).data;
  },
};
