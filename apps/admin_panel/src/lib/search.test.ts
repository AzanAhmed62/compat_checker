import { describe, expect, it } from 'vitest';
import { sanitizeSearchTerm } from './api';

describe('sanitizeSearchTerm', () => {
  it('removes PostgREST filter separators from free-text search', () => {
    expect(sanitizeSearchTerm('phone,slug.eq.secret')).toBe('phone slug.eq.secret');
    expect(sanitizeSearchTerm('name)or(id.eq.1')).toBe('name or id.eq.1');
    expect(sanitizeSearchTerm('100% useful')).toBe('100 useful');
  });

  it('neutralizes wildcard and LIKE escape characters', () => {
    expect(sanitizeSearchTerm('a*b\\c')).toBe('a b c');
  });

  it('trims and collapses whitespace without inventing a term', () => {
    expect(sanitizeSearchTerm('  Aurora   X1  ')).toBe('Aurora X1');
    expect(sanitizeSearchTerm('   ')).toBe('');
    expect(sanitizeSearchTerm(',(),')).toBe('');
  });
});
