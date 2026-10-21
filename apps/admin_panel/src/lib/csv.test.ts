import { describe, expect, it } from 'vitest';
import { parseCsv, prepareProductImport, serializeCsv } from './csv';

describe('parseCsv', () => {
  it('parses quoted commas, escaped quotes, and CRLF', () => {
    const table = parseCsv('\uFEFFname,summary\r\n"A, B","He said ""hi"""\r\n');
    expect(table.headers).toEqual(['name', 'summary']);
    expect(table.rows).toEqual([{ name: 'A, B', summary: 'He said "hi"' }]);
  });

  it('preserves embedded newlines', () => {
    const table = parseCsv('name,summary\nA,"line one\nline two"\n');
    expect(table.rows[0]?.summary).toBe('line one\nline two');
  });

  it('rejects duplicate and empty headers', () => {
    expect(() => parseCsv('name,name\nA,B')).toThrow(/Duplicate/);
    expect(() => parseCsv('name,\nA,B')).toThrow(/empty/);
  });

  it('rejects ragged rows and unclosed quotes', () => {
    expect(() => parseCsv('name,slug\nA')).toThrow(/expected 2/);
    expect(() => parseCsv('name\n"open')).toThrow(/Unclosed/);
  });
});

describe('prepareProductImport', () => {
  const base = {
    headers: ['name', 'slug', 'brand_slug', 'category_slug', 'aliases', 'release_year'],
    rows: [
      {
        name: 'Aurora X1',
        slug: 'aurora-x1',
        brand_slug: 'sample-electronics',
        category_slug: 'smartphones',
        aliases: 'AX1 | Aurora One',
        release_year: '2025',
      },
    ],
  };

  it('normalizes aliases and numeric year', () => {
    expect(prepareProductImport(base)).toEqual([
      {
        name: 'Aurora X1',
        slug: 'aurora-x1',
        brand_slug: 'sample-electronics',
        category_slug: 'smartphones',
        aliases: ['AX1', 'Aurora One'],
        release_year: 2025,
      },
    ]);
  });

  it('requires brand and category references', () => {
    expect(() =>
      prepareProductImport({
        headers: ['name', 'slug'],
        rows: [{ name: 'A', slug: 'a' }],
      }),
    ).toThrow(/brand_slug/);
  });

  it('rejects unknown columns', () => {
    expect(() =>
      prepareProductImport({
        headers: ['name', 'slug', 'brand_slug', 'category_slug', 'status'],
        rows: [
          {
            name: 'A',
            slug: 'a',
            brand_slug: 'b',
            category_slug: 'c',
            status: 'published',
          },
        ],
      }),
    ).toThrow(/Unsupported/);
  });
});

describe('serializeCsv', () => {
  it('escapes cells and emits a BOM', () => {
    const result = serializeCsv(['name', 'note'], [{ name: 'A, B', note: 'x"y' }]);
    expect(result.startsWith('\uFEFF')).toBe(true);
    expect(result).toContain('"A, B","x""y"');
  });

  it('neutralizes spreadsheet formulas', () => {
    const result = serializeCsv(
      ['value'],
      [
        { value: '=HYPERLINK("https://example.local")' },
        { value: '+1+1' },
        { value: '-1+1' },
        { value: '@SUM(A1)' },
        { value: '\t=1+1' },
      ],
    );

    expect(result).toContain("'=HYPERLINK");
    expect(result).toContain("'+1+1");
    expect(result).toContain("'-1+1");
    expect(result).toContain("'@SUM(A1)");
    expect(result).toContain("'\t=1+1");
  });
});
