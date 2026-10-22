export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Country { id: number; isoCode: string; name: string; active: boolean }
export interface City { id: number; countryId: number; countryName: string; name: string; active: boolean }
export interface AppItem { id: number; code: string; name: string; description: string | null; active: boolean; cityIds: number[] }

export type Role = 'ADMIN' | 'AGENT';
export interface AssignmentItem { applicationId: number; applicationName?: string; cityId: number; cityName?: string }
export interface UserItem {
  id: number; username: string; fullName: string; phone: string | null; role: Role; active: boolean;
  deviceId: string | null; deviceModel: string | null; deviceBoundAt: string | null;
  lastLoginAt: string | null; lockedUntil: string | null; assignments: AssignmentItem[] | null;
}

export type CaptureMode = 'SCAN' | 'MANUAL';
export interface PersonSummary {
  id: number; lastName: string; firstName: string; birthDate: string | null;
  documentType: string | null; documentNumberMasked: string | null;
  applicationId: number; applicationName: string; countryId: number; countryName: string;
  cityId: number; cityName: string; agentId: number; agentName: string;
  captureMode: CaptureMode; hasDocumentImage: boolean; createdAt: string;
}
export interface PersonDetail {
  id: number; clientUuid: string; applicationName: string; countryName: string; cityName: string;
  agentName: string; deviceId: string; captureMode: CaptureMode; lastName: string; firstName: string;
  gender: string | null; birthDate: string | null; birthPlace: string | null; nationality: string | null;
  phone: string | null; email: string | null; address: string | null;
  documentType: string | null; documentNumber: string | null;
  documentIssueDate: string | null; documentExpiryDate: string | null; hasDocumentImage: boolean; hasSignature: boolean;
  ocrConfidence: number | null; ocrRawText: string | null; extraData: unknown;
  capturedAt: string; createdAt: string;
}

export interface Overview {
  total: number; today: number; last7Days: number; last30Days: number; activeAgents: number;
  byCaptureMode: Record<CaptureMode, number>;
}
export interface CountryCount { countryId: number; country: string; count: number }
export interface CityCount { countryId: number; country: string; cityId: number; city: string; count: number }
export interface ApplicationCount { applicationId: number; application: string; count: number }
export interface Breakdown {
  countryId: number; country: string; cityId: number; city: string;
  applicationId: number; application: string; count: number;
}
export interface DayCount { day: string; count: number }
