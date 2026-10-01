import axios from 'axios';
import type { ApiError } from '../types';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
});

let authToken: string | null = null;

export function setAuthToken(token: string | null) {
  authToken = token;
}

api.interceptors.request.use((config) => {
  if (authToken) {
    config.headers.Authorization = `Bearer ${authToken}`;
  }
  return config;
});

export function toApiError(error: unknown): ApiError {
  const err = error as { response?: { status: number; data: { code?: string; message?: string; details?: unknown } }; message?: string };
  if (err.response) {
    return {
      status: err.response.status,
      code: err.response.data?.code ?? 'ERROR',
      message: err.response.data?.message ?? err.message ?? 'Unknown error',
      details: err.response.data?.details ?? null,
    };
  }
  return { status: 0, code: 'NETWORK', message: err.message ?? 'Network error', details: null };
}
