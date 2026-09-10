import { HttpErrorResponse } from '@angular/common/http';

export type ErrorCategory =
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'CONFLICT'
  | 'VALIDATION'
  | 'RATE_LIMITED'
  | 'SERVER_ERROR'
  | 'SERVICE_UNAVAILABLE'
  | 'NETWORK_ERROR'
  | 'TIMEOUT'
  | 'UNKNOWN';

export interface FieldValidationError {
  field: string;
  message: string;
}

export interface AppError {
  category: ErrorCategory;
  status: number;
  message: string;
  userFriendlyMessage: string;
  path?: string;
  timestamp: string;
  fieldErrors?: FieldValidationError[];
  originalError?: unknown;
}

/**
 * Normalizes any HTTP error response or generic error into a standardized AppError object.
 */
export function normalizeHttpError(error: unknown, requestUrl?: string): AppError {
  const timestamp = new Date().toISOString();

  if (!(error instanceof HttpErrorResponse)) {
    const message = error instanceof Error ? error.message : 'An unexpected error occurred.';
    return {
      category: 'UNKNOWN',
      status: 0,
      message,
      userFriendlyMessage: 'An unexpected error occurred. Please try again.',
      path: requestUrl,
      timestamp,
      originalError: error,
    };
  }

  const status = error.status;
  const rawBody = error.error;
  const path = rawBody?.path || requestUrl || error.url || '';
  const message = rawBody?.message || error.message || 'HTTP request failed';

  let category: ErrorCategory = 'UNKNOWN';
  let userFriendlyMessage = 'An unexpected error occurred. Please try again.';

  switch (status) {
    case 0:
      category = 'NETWORK_ERROR';
      userFriendlyMessage = 'Unable to connect to the server. Please check your internet connection.';
      break;
    case 401:
      category = 'UNAUTHORIZED';
      userFriendlyMessage = 'Your session has expired. Please sign in again.';
      break;
    case 403:
      category = 'FORBIDDEN';
      userFriendlyMessage = 'You do not have permission to access this resource.';
      break;
    case 404:
      category = 'NOT_FOUND';
      userFriendlyMessage = 'The requested resource was not found.';
      break;
    case 409:
      category = 'CONFLICT';
      userFriendlyMessage = rawBody?.message || 'A resource conflict occurred. Please review and try again.';
      break;
    case 422:
      category = 'VALIDATION';
      userFriendlyMessage = 'Some fields contain invalid data. Please correct them and try again.';
      break;
    case 429:
      category = 'RATE_LIMITED';
      userFriendlyMessage = 'Too many requests. Please wait a moment before trying again.';
      break;
    case 500:
      category = 'SERVER_ERROR';
      userFriendlyMessage = 'A server error occurred. Our team has been notified.';
      break;
    case 502:
    case 503:
    case 504:
      category = 'SERVICE_UNAVAILABLE';
      userFriendlyMessage = 'The service is temporarily unavailable. Please try again shortly.';
      break;
    default:
      category = 'UNKNOWN';
      userFriendlyMessage = rawBody?.message || 'An unexpected error occurred.';
  }

  // Extract field validation errors if structured in payload
  let fieldErrors: FieldValidationError[] | undefined = undefined;
  const rawErrors = Array.isArray(rawBody?.validationErrors)
    ? rawBody.validationErrors
    : Array.isArray(rawBody?.errors)
      ? rawBody.errors
      : undefined;

  if (rawErrors) {
    fieldErrors = rawErrors.map((e: any) => ({
      field: e.field || '',
      message: e.message || 'Invalid value',
    }));
  }

  return {
    category,
    status,
    message,
    userFriendlyMessage,
    path,
    timestamp,
    fieldErrors,
    originalError: error,
  };
}

/**
 * Robustly extracts the most informative error message from an unknown error object,
 * whether it is an AppError, HttpErrorResponse, validation error list, or standard Error.
 */
export function extractErrorMessage(err: unknown, fallback: string): string {
  if (!err) return fallback;
  const anyErr = err as any;

  // 1. Structured validation errors
  const validationList =
    anyErr?.fieldErrors ||
    anyErr?.originalError?.error?.validationErrors ||
    anyErr?.error?.validationErrors ||
    anyErr?.originalError?.error?.errors ||
    anyErr?.error?.errors;

  if (Array.isArray(validationList) && validationList.length > 0) {
    const msgs = validationList
      .map((v: any) => v?.message || (v?.field ? `${v.field} is invalid` : null))
      .filter(Boolean);
    if (msgs.length > 0) {
      return msgs.join(', ');
    }
  }

  // 2. AppError message or backend message
  if (
    typeof anyErr?.message === 'string' &&
    anyErr.message &&
    anyErr.message !== 'HTTP request failed' &&
    !anyErr.message.startsWith('Http failure response')
  ) {
    return anyErr.message;
  }
  if (typeof anyErr?.error?.message === 'string' && anyErr.error.message) {
    return anyErr.error.message;
  }
  if (typeof anyErr?.userFriendlyMessage === 'string' && anyErr.userFriendlyMessage) {
    return anyErr.userFriendlyMessage;
  }
  if (typeof anyErr?.error?.error === 'string' && anyErr.error.error) {
    return anyErr.error.error;
  }
  if (typeof anyErr?.error === 'string' && anyErr.error) {
    return anyErr.error;
  }
  if (typeof anyErr?.message === 'string' && anyErr.message) {
    return anyErr.message;
  }

  return fallback;
}
