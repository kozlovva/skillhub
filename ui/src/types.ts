export type ElementType = 'SKILL' | 'SCRIPT' | 'AGENT' | 'HOOK' | 'PACK' | 'OTHER';

export interface ElementResponse {
  slug: string;
  type: ElementType;
  name: string;
  description: string;
  team: string;
  category: string | null;
  tags: string[];
  visibility: 'PUBLIC' | 'TEAM';
  latestVersion: string | null;
  downloadsCount: number;
  avgRating?: number | null;
  ratingCount?: number | null;
}

export interface FileDto {
  path: string;
  size: number;
}

export interface VersionResponse {
  version: string;
  status: 'DRAFT' | 'PUBLISHED' | 'DEPRECATED';
  changelog: string;
  sizeBytes: number;
  files: FileDto[];
}

export interface SearchResultResponse {
  items: ElementResponse[];
  total: number;
  facetsByType: Record<string, number>;
}

export interface SocialInfo {
  avgRating: number;
  ratingCount: number;
  favorited: boolean;
}

export interface ReviewResponse {
  author: string;
  rating: number;
  text: string;
  createdAt: string;
}

export interface CategoryResponse {
  slug: string;
  name: string;
  parent: string | null;
  icon: string | null;
}

export interface TeamResponse {
  slug: string;
  name: string;
}

export interface PackContentDto {
  element: string;
  version: string | null;
  versionConstraint: string;
}

export interface PackResponse {
  slug: string;
  contents: PackContentDto[];
}

export interface ApiError {
  status: number;
  code: string;
  message: string;
  details: unknown;
}
