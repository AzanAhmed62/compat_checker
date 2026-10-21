export function readableError(error: unknown): string {
  if (!error) return 'Unknown error';
  if (typeof error === 'string') return error;
  if (error instanceof Error) return error.message;
  if (typeof error === 'object') {
    const candidate = error as { message?: unknown; details?: unknown; hint?: unknown; code?: unknown };
    const message = typeof candidate.message === 'string' ? candidate.message : null;
    if (message) {
      return candidate.code ? `${message} (${String(candidate.code)})` : message;
    }
    try {
      return JSON.stringify(error);
    } catch {
      return 'Unexpected error';
    }
  }
  return String(error);
}

export function errorMessage(error: unknown): string {
  const text = readableError(error);
  if (/permission denied|row-level security|42501/i.test(text)) {
    return 'You do not have permission for this action.';
  }
  if (/jwt|session|not authenticated|401/i.test(text)) {
    return 'Your session expired. Sign in again.';
  }
  if (/rate limit/i.test(text)) {
    return 'Too many attempts. Wait a moment and try again.';
  }
  return text;
}
