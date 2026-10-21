import { describe, expect, it } from 'vitest';
import { canAdmin, canModerate, roleLabel } from './permissions';

describe('permissions', () => {
  it('only grants admin capabilities to active admins', () => {
    expect(canAdmin('admin', 'active')).toBe(true);
    expect(canAdmin('admin', 'suspended')).toBe(false);
    expect(canAdmin('trusted', 'active')).toBe(false);
  });

  it('grants moderation to active trusted contributors and admins', () => {
    expect(canModerate('trusted', 'active')).toBe(true);
    expect(canModerate('admin', 'active')).toBe(true);
    expect(canModerate('contributor', 'active')).toBe(false);
    expect(canModerate('trusted', 'banned')).toBe(false);
  });

  it('formats role labels', () => {
    expect(roleLabel('admin')).toBe('Administrator');
    expect(roleLabel('contributor')).toBe('Contributor');
  });
});
