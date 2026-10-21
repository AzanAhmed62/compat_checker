import type { Role } from './types';

export function canAdmin(role: Role | null | undefined, status: string | null | undefined): boolean {
  return role === 'admin' && status === 'active';
}

export function canModerate(
  role: Role | null | undefined,
  status: string | null | undefined,
): boolean {
  return (role === 'trusted' || role === 'admin') && status === 'active';
}

export function roleLabel(role: Role): string {
  return role === 'admin' ? 'Administrator' : role[0]!.toUpperCase() + role.slice(1);
}
