export interface CsvTable {
  headers: string[];
  rows: Array<Record<string, string>>;
}

/**
 * RFC 4180-style parser. It accepts UTF-8 BOM, LF/CRLF, quoted commas, escaped
 * quotes, and embedded newlines. It rejects duplicate or empty headers because
 * silent column loss is dangerous for bulk catalog imports.
 */
export function parseCsv(input: string): CsvTable {
  const text = input.replace(/^\uFEFF/, '');
  const records: string[][] = [];
  let record: string[] = [];
  let field = '';
  let quoted = false;

  for (let index = 0; index < text.length; index += 1) {
    const character = text[index]!;
    if (quoted) {
      if (character === '"') {
        if (text[index + 1] === '"') {
          field += '"';
          index += 1;
        } else {
          quoted = false;
        }
      } else {
        field += character;
      }
      continue;
    }

    if (character === '"') {
      if (field.length > 0) {
        throw new Error('Unexpected quote in an unquoted field');
      }
      quoted = true;
    } else if (character === ',') {
      record.push(field);
      field = '';
    } else if (character === '\n' || character === '\r') {
      if (character === '\r' && text[index + 1] === '\n') index += 1;
      record.push(field);
      field = '';
      if (record.some((cell) => cell.trim() !== '')) records.push(record);
      record = [];
    } else {
      field += character;
    }
  }

  if (quoted) throw new Error('Unclosed quoted field');
  record.push(field);
  if (record.some((cell) => cell.trim() !== '')) records.push(record);

  const first = records.shift();
  if (!first) return { headers: [], rows: [] };

  const headers = first.map((header) => header.trim().toLowerCase());
  if (headers.some((header) => header === '')) {
    throw new Error('CSV headers cannot be empty');
  }
  const duplicates = headers.filter((header, index) => headers.indexOf(header) !== index);
  if (duplicates.length > 0) {
    throw new Error(`Duplicate CSV headers: ${[...new Set(duplicates)].join(', ')}`);
  }

  const rows = records.map((values, index) => {
    if (values.length !== headers.length) {
      throw new Error(
        `Row ${index + 2} has ${values.length} cells; expected ${headers.length}`,
      );
    }
    const row: Record<string, string> = {};
    headers.forEach((header, column) => {
      row[header] = values[column]!.trim();
    });
    return row;
  });

  return { headers, rows };
}

export function csvCell(value: unknown): string {
  if (value === null || value === undefined) return '';
  const text = String(value);
  // Spreadsheet applications may evaluate a cell that begins with one of these
  // characters even when the field is quoted. Prefix untrusted text with an
  // apostrophe so exported data is treated as text.
  const safe = /^[=+\-@\t\r]/.test(text) ? `'${text}` : text;
  return /[",\r\n]/.test(safe) ? `"${safe.replaceAll('"', '""')}"` : safe;
}

export function serializeCsv(
  headers: readonly string[],
  rows: ReadonlyArray<Record<string, unknown>>,
): string {
  const lines = [headers.map(csvCell).join(',')];
  for (const row of rows) {
    lines.push(headers.map((header) => csvCell(row[header])).join(','));
  }
  return `\uFEFF${lines.join('\r\n')}\r\n`;
}

export function downloadText(filename: string, text: string, mime = 'text/csv;charset=utf-8') {
  const blob = new Blob([text], { type: mime });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

export const PRODUCT_IMPORT_HEADERS = [
  'name',
  'slug',
  'brand_slug',
  'category_slug',
  'model_number',
  'release_year',
  'summary',
  'technical_notes',
  'connector_type',
  'form_factor',
  'aliases',
] as const;

const MUTABLE_COLUMNS = new Set<string>(PRODUCT_IMPORT_HEADERS);

export function prepareProductImport(table: CsvTable): Array<Record<string, unknown>> {
  if (!table.headers.includes('name') || !table.headers.includes('slug')) {
    throw new Error('CSV must include name and slug columns');
  }
  if (!table.headers.includes('brand_slug') && !table.headers.includes('brand_id')) {
    throw new Error('CSV must include brand_slug or brand_id');
  }
  if (!table.headers.includes('category_slug') && !table.headers.includes('category_id')) {
    throw new Error('CSV must include category_slug or category_id');
  }
  const unknown = table.headers.filter((header) => !MUTABLE_COLUMNS.has(header));
  if (unknown.length > 0) {
    throw new Error(`Unsupported CSV columns: ${unknown.join(', ')}`);
  }

  return table.rows.map((row, index) => {
    if (!row.name || !row.slug) {
      throw new Error(`Row ${index + 2}: name and slug are required`);
    }
    const output: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(row)) {
      if (value === '') continue;
      if (key === 'aliases') {
        output.aliases = value
          .split('|')
          .map((alias) => alias.trim())
          .filter(Boolean);
      } else if (key === 'release_year') {
        const year = Number(value);
        if (!Number.isInteger(year) || year < 1900 || year > 2200) {
          throw new Error(`Row ${index + 2}: release_year must be a four-digit year`);
        }
        output.release_year = year;
      } else {
        output[key] = value;
      }
    }
    return output;
  });
}
