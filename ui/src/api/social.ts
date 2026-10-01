import { api } from './client';
import type { ReviewResponse, SocialInfo } from '../types';

export const social = {
  async rate(slug: string, rating: number): Promise<void> {
    await api.put(`/api/elements/${slug}/rating`, { rating });
  },
  async review(slug: string, rating: number, text: string): Promise<void> {
    await api.put(`/api/elements/${slug}/review`, { rating, text });
  },
  async reviews(slug: string): Promise<ReviewResponse[]> {
    return (await api.get<ReviewResponse[]>(`/api/elements/${slug}/reviews`)).data;
  },
  async setFavorite(slug: string, favorited: boolean): Promise<void> {
    if (favorited) {
      await api.post(`/api/elements/${slug}/favorite`);
    } else {
      await api.delete(`/api/elements/${slug}/favorite`);
    }
  },
  async info(slug: string): Promise<SocialInfo> {
    return (await api.get<SocialInfo>(`/api/elements/${slug}/social`)).data;
  },
};
