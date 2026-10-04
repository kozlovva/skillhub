import axios, { type AxiosResponse } from 'axios';
import type { ApiError } from '../types';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? '',
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

export async function downloadFile(url: string, filename?: string): Promise<void> {
  let response: AxiosResponse<Blob>;
  try {
    response = await api.get<Blob>(url, { responseType: 'blob' });
  } catch (error) {
    throw await unwrapBlobError(error);
  }
  const objectUrl = URL.createObjectURL(response.data);
  const a = document.createElement('a');
  a.href = objectUrl;
  a.download = filename ?? '';
  a.click();
  URL.revokeObjectURL(objectUrl);
}

async function unwrapBlobError(error: unknown): Promise<unknown> {
  const err = error as { response?: { data?: unknown } } | undefined;
  const blob = err?.response?.data;
  if (blob instanceof Blob) {
    try {
      const text = await blob.text();
      err!.response!.data = JSON.parse(text);
    } catch {
      // not JSON — keep original blob data
    }
  }
  return error;
}

export function toApiError(error: unknown): ApiError {
  const err = error as { response?: { status: number; data?: { code?: string; message?: string; details?: unknown } }; message?: string };
  if (err.response) {
    const data = err.response.data ?? {};
    const details = data.details ?? null;
    const detailText = formatDetails(details);
    const message = detailText
      ? `${data.message ?? err.message ?? 'Unknown error'}: ${detailText}`
      : data.message ?? err.message ?? 'Unknown error';
    return {
      status: err.response.status,
      code: data.code ?? 'ERROR',
      message,
      details,
    };
  }
  return { status: 0, code: 'NETWORK', message: err.message ?? 'Network error', details: null };
}

function formatDetails(details: unknown): string | null {
  if (details == null) return null;
  if (typeof details === 'string') return details;
  if (Array.isArray(details)) {
    return details.map((d) => String(d)).join('; ') || null;
  }
  if (typeof details === 'object') {
    const parts = Object.entries(details as Record<string, unknown>)
      .map(([field, msg]) => (msg != null && msg !== '' ? `${field} — ${String(msg)}` : null))
      .filter((p): p is string => p != null);
    return parts.length ? parts.join('; ') : null;
  }
  return String(details);
}
