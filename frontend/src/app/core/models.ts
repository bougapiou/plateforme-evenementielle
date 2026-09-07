export type UserType = 'PARTICULIER' | 'STRUCTURE' | 'ORGANISATEUR' | 'PERSONNEL' | 'ADMIN';
export type UserStatus = 'ACTIF' | 'EN_ATTENTE' | 'DESACTIVE';

export interface UserSummary {
  id: string;
  email: string;
  fullName: string;
  type: UserType;
  status: UserStatus;
  roles: string[];
  permissions: string[];
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserSummary;
}

export interface RegisterPayload {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phone?: string;
  type?: UserType;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  fieldErrors?: { field: string; message: string }[];
}
