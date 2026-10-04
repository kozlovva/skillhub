import { api } from './client';
import type { SearchResultResponse } from '../types';

export const search = {
  async search(params: {
    q?: string; type?: string; category?: string;
    sort?: string; order?: string; limit?: number; offset?: number;
  }): Promise<SearchResultResponse> {
    return (await api.get<SearchResultResponse>('/api/search', { params })).data;
  },
};
