import { useState, type ChangeEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  ErrorPanel,
  PageHeader,
} from '../components/PageParts';
import { exportProductsCsv, exportRulesCsv, importProducts } from '../lib/api';
import {
  PRODUCT_IMPORT_HEADERS,
  downloadText,
  parseCsv,
  prepareProductImport,
  serializeCsv,
  type CsvTable,
} from '../lib/csv';

export function ImportsPage() {
  const queryClient = useQueryClient();
  const [table, setTable] = useState<CsvTable | null>(null);
  const [fileName, setFileName] = useState('');
  const [parseError, setParseError] = useState<string | null>(null);
  const [publish, setPublish] = useState(false);
  const [result, setResult] = useState<string | null>(null);

  const importMutation = useMutation({
    mutationFn: async () => {
      if (!table) throw new Error('Choose a CSV file first');
      const rows = prepareProductImport(table);
      if (rows.length > 500) throw new Error('Import at most 500 rows at a time');
      return importProducts(rows, publish);
    },
    onSuccess: async (data) => {
      setResult(
        `Imported ${data.imported} product${data.imported === 1 ? '' : 's'} as ${
          data.published ? 'published' : 'draft'
        }.`,
      );
      await queryClient.invalidateQueries({ queryKey: ['products'] });
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });

  async function chooseFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    setResult(null);
    setParseError(null);
    setTable(null);
    setFileName('');
    if (!file) return;
    setFileName(file.name);
    if (file.size > 5 * 1024 * 1024) {
      setParseError('CSV files must be 5 MB or smaller.');
      return;
    }
    try {
      const parsed = parseCsv(await file.text());
      prepareProductImport(parsed);
      setTable(parsed);
    } catch (cause) {
      setParseError(cause instanceof Error ? cause.message : String(cause));
    }
  }

  async function exportProducts() {
    try {
      const csv = await exportProductsCsv();
      downloadText('products-export.csv', csv);
    } catch (cause) {
      setParseError(cause instanceof Error ? cause.message : String(cause));
    }
  }

  async function exportRules() {
    try {
      const csv = await exportRulesCsv();
      downloadText('rules-export.csv', csv);
    } catch (cause) {
      setParseError(cause instanceof Error ? cause.message : String(cause));
    }
  }

  return (
    <>
      <PageHeader
        title="CSV tools"
        description="Bulk import validates every row and applies the batch atomically. Duplicate slugs or any invalid row roll back the entire batch."
      />

      <div className="two-column">
        <section className="card" aria-labelledby="import-heading">
          <div className="card-header">
            <h2 id="import-heading">Import products</h2>
            <a
              className="button small secondary"
              href="#template"
              onClick={(event) => {
                event.preventDefault();
                downloadText(
                  'products-template.csv',
                  serializeCsv(PRODUCT_IMPORT_HEADERS, []),
                );
              }}
            >
              Download empty template
            </a>
          </div>
          <div className="form-stack">
            <label>
              CSV file
              <input type="file" accept=".csv,text/csv" onChange={(event) => void chooseFile(event)} />
            </label>
            <label className="checkbox-label">
              <input
                type="checkbox"
                checked={publish}
                onChange={(event) => setPublish(event.target.checked)}
              />
              Publish imported products immediately
            </label>
            <p className="notice warning">
              Importing as drafts is the safe default. Publishing makes products eligible for
              public search and compatibility rules, but does not create any compatibility claim.
            </p>
            {parseError ? (
              <div className="notice error" role="alert">
                {parseError}
              </div>
            ) : null}
            {importMutation.error ? <ErrorPanel error={importMutation.error} /> : null}
            {result ? (
              <div className="notice success" role="status">
                {result}
              </div>
            ) : null}
            {table ? (
              <>
                <p>
                  <strong>{fileName}</strong> · {table.rows.length} rows · {table.headers.length}{' '}
                  columns
                </p>
                <div className="table-scroll preview-table">
                  <table>
                    <thead>
                      <tr>
                        {table.headers.map((header) => (
                          <th key={header} scope="col">
                            {header}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {table.rows.slice(0, 20).map((row, index) => (
                        <tr key={index}>
                          {table.headers.map((header) => (
                            <td key={header}>{row[header]}</td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                {table.rows.length > 20 ? (
                  <p className="muted small">Showing the first 20 of {table.rows.length} rows.</p>
                ) : null}
                <button
                  type="button"
                  className="button primary"
                  disabled={importMutation.isPending}
                  onClick={() => {
                    setResult(null);
                    importMutation.mutate();
                  }}
                >
                  {importMutation.isPending
                    ? 'Importing…'
                    : `Import ${table.rows.length} product rows`}
                </button>
              </>
            ) : null}
          </div>
        </section>

        <section className="card" aria-labelledby="export-heading">
          <div className="card-header">
            <h2 id="export-heading">Export</h2>
          </div>
          <p>
            Exports include drafts and archived records and are intended for administrators. Files
            include a UTF-8 BOM for spreadsheet compatibility.
          </p>
          <div className="row-actions">
            <button type="button" className="button secondary" onClick={() => void exportProducts()}>
              Products CSV
            </button>
            <button type="button" className="button secondary" onClick={() => void exportRules()}>
              Rules CSV
            </button>
          </div>
          <h3>Automation endpoint</h3>
          <p className="muted">
            The <code>admin-csv</code> Edge Function provides the same administrator-only export
            over HTTP for scheduled reporting. It verifies the caller’s JWT and profile role; it
            never uses the service role for user-initiated reads.
          </p>
          <h3>Required columns</h3>
          <ul>
            <li>
              <code>name</code>, <code>slug</code>
            </li>
            <li>
              <code>brand_slug</code> (or <code>brand_id</code>)
            </li>
            <li>
              <code>category_slug</code> (or <code>category_id</code>)
            </li>
          </ul>
          <p className="muted small">
            Optional: model_number, release_year, summary, technical_notes, connector_type,
            form_factor, aliases (pipe-separated). Unsupported columns are rejected rather than
            silently ignored.
          </p>
        </section>
      </div>
    </>
  );
}
