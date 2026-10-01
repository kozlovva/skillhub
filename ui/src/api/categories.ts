import { api } from './client';
import type { CategoryResponse } from '../types';

export const categories = {
  async list(): Promise<CategoryResponse[]> {
    return (await api.get<CategoryResponse[]>('/api/categories')).data;
  },
  async create(body: { slug: string; name: string; parentSlug?: string; icon?: string }): Promise<CategoryResponse> {
    return (await api.post<CategoryResponse>('/api/categories', body)).data;
  },
};
