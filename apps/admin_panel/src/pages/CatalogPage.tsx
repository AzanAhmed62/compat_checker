import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../auth/AuthProvider';
import { Modal } from '../components/Modal';
import { Pagination } from '../components/Pagination';
import {
  Badge,
  EmptyPanel,
  ErrorPanel,
  LoadingPanel,
  PageHeader,
} from '../components/PageParts';
import {
  deleteProduct,
  getProductDetail,
  listBrands,
  listCategories,
  listProducts,
  saveProduct,
  type ProductInput,
} from '../lib/api';
import { formatDate } from '../lib/format';
import type { Brand, Category, ProductDetail, ProductStatus } from '../lib/types';

type SpecRow = {
  label: string;
  value: string;
  unit: string;
  is_critical: boolean;
};

const emptyForm = {
  name: '',
  slug: '',
  brand_id: '',
  category_id: '',
  model_number: '',
  release_year: '',
  summary: '',
  technical_notes: '',
  connector_type: '',
  form_factor: '',
  status: 'draft' as ProductStatus,
};

export function CatalogPage() {
  const { me } = useAuth();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [queryText, setQueryText] = useState('');
  const [appliedQuery, setAppliedQuery] = useState('');
  const [status, setStatus] = useState<ProductStatus | ''>('');
  const [editor, setEditor] = useState<{ id?: string } | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<{ id: string; name: string } | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const brands = useQuery({ queryKey: ['brands'], queryFn: listBrands });
  const categories = useQuery({ queryKey: ['categories'], queryFn: listCategories });
  const products = useQuery({
    queryKey: ['products', page, appliedQuery, status],
    queryFn: () => listProducts({ page, query: appliedQuery, status }),
  });

  const remove = useMutation({
    mutationFn: (id: string) => deleteProduct(id),
    onSuccess: async () => {
      setDeleteTarget(null);
      setDeleteError(null);
      await queryClient.invalidateQueries({ queryKey: ['products'] });
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
    onError: (error) => setDeleteError(error instanceof Error ? error.message : String(error)),
  });

  function applySearch(event: FormEvent) {
    event.preventDefault();
    setPage(0);
    setAppliedQuery(queryText.trim());
  }

  async function handleSaved() {
    setEditor(null);
    await queryClient.invalidateQueries({ queryKey: ['products'] });
    await queryClient.invalidateQueries({ queryKey: ['dashboard'] });
  }

  return (
    <>
      <PageHeader
        title="Catalog"
        description="Products are public only when published. Draft and archived rows remain visible to administrators."
        actions={
          <button type="button" className="button primary" onClick={() => setEditor({})}>
            New product
          </button>
        }
      />

      <form className="filter-bar" onSubmit={applySearch} role="search">
        <label>
          Search
          <input
            type="search"
            value={queryText}
            onChange={(event) => setQueryText(event.target.value)}
            placeholder="Name or slug"
          />
        </label>
        <label>
          Status
          <select
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as ProductStatus | '');
              setPage(0);
            }}
          >
            <option value="">All statuses</option>
            <option value="published">Published</option>
            <option value="draft">Draft</option>
            <option value="archived">Archived</option>
          </select>
        </label>
        <button type="submit" className="button secondary">
          Apply
        </button>
      </form>

      {products.isLoading ? <LoadingPanel label="Loading products…" /> : null}
      {products.error ? (
        <ErrorPanel error={products.error} retry={() => void products.refetch()} />
      ) : null}
      {products.data && products.data.items.length === 0 ? (
        <EmptyPanel>No products match these filters.</EmptyPanel>
      ) : null}
      {products.data && products.data.items.length > 0 ? (
        <div className="card table-card">
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">Product</th>
                  <th scope="col">Brand</th>
                  <th scope="col">Category</th>
                  <th scope="col">Status</th>
                  <th scope="col">Updated</th>
                  <th scope="col">Actions</th>
                </tr>
              </thead>
              <tbody>
                {products.data.items.map((product) => (
                  <tr key={product.id}>
                    <td>
                      <strong>{product.name}</strong>
                      <div className="muted small">
                        <code>{product.slug}</code>
                        {product.model_number ? ` · ${product.model_number}` : ''}
                      </div>
                    </td>
                    <td>{product.brand_name}</td>
                    <td>{product.category_name}</td>
                    <td>
                      <Badge value={product.status} />
                    </td>
                    <td>{formatDate(product.updated_at)}</td>
                    <td>
                      <div className="row-actions">
                        <button
                          type="button"
                          className="button small secondary"
                          onClick={() => setEditor({ id: product.id })}
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          className="button small danger"
                          onClick={() => {
                            setDeleteError(null);
                            setDeleteTarget({ id: product.id, name: product.name });
                          }}
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={page}
            pageSize={products.data.limit}
            total={products.data.total}
            onPageChange={setPage}
          />
        </div>
      ) : null}

      {editor ? (
        <ProductEditor
          id={editor.id}
          actorId={me?.id ?? ''}
          brands={brands.data ?? []}
          categories={categories.data ?? []}
          onClose={() => setEditor(null)}
          onSaved={() => void handleSaved()}
        />
      ) : null}

      {deleteTarget ? (
        <Modal
          title={`Delete “${deleteTarget.name}”?`}
          onClose={() => setDeleteTarget(null)}
          footer={
            <>
              <button
                type="button"
                className="button secondary"
                onClick={() => setDeleteTarget(null)}
              >
                Cancel
              </button>
              <button
                type="button"
                className="button danger"
                disabled={remove.isPending}
                onClick={() => remove.mutate(deleteTarget.id)}
              >
                {remove.isPending ? 'Deleting…' : 'Delete permanently'}
              </button>
            </>
          }
        >
          <p>
            Delete is only appropriate for unreferenced drafts. Products referenced by rules or
            history cannot be deleted. Prefer <strong>Archived</strong> status for public records.
          </p>
          {deleteError ? (
            <div className="notice error" role="alert">
              {deleteError}
            </div>
          ) : null}
        </Modal>
      ) : null}
    </>
  );
}

function ProductEditor({
  id,
  actorId,
  brands,
  categories,
  onClose,
  onSaved,
}: {
  id?: string;
  actorId: string;
  brands: Brand[];
  categories: Category[];
  onClose(): void;
  onSaved(): void;
}) {
  const detail = useQuery({
    queryKey: ['product', id],
    queryFn: () => getProductDetail(id!),
    enabled: Boolean(id),
  });
  const [form, setForm] = useState({ ...emptyForm });
  const [aliases, setAliases] = useState('');
  const [specs, setSpecs] = useState<SpecRow[]>([]);
  const [error, setError] = useState<string | null>(null);
  const save = useMutation({
    mutationFn: async () => {
      if (!form.brand_id || !form.category_id) {
        throw new Error('Choose a brand and category');
      }
      if (!form.name.trim() || !form.slug.trim()) {
        throw new Error('Name and slug are required');
      }
      if (!/^[a-z0-9]+(-[a-z0-9]+)*$/.test(form.slug.trim())) {
        throw new Error('Slug must be lowercase words separated by single hyphens');
      }
      const input: ProductInput = {
        name: form.name.trim(),
        slug: form.slug.trim(),
        brand_id: form.brand_id,
        category_id: form.category_id,
        model_number: form.model_number.trim() || null,
        release_year: form.release_year ? Number(form.release_year) : null,
        summary: form.summary.trim() || null,
        technical_notes: form.technical_notes.trim() || null,
        connector_type: form.connector_type.trim() || null,
        form_factor: form.form_factor.trim() || null,
        status: form.status,
      };
      return saveProduct(
        input,
        {
          aliases: aliases.split('\n'),
          specs: specs.map((spec) => ({
            label: spec.label,
            value: spec.value,
            unit: spec.unit || null,
            is_critical: spec.is_critical,
            sort_order: 0,
          })),
        },
        actorId,
        id,
      );
    },
    onSuccess: onSaved,
    onError: (cause) => setError(cause instanceof Error ? cause.message : String(cause)),
  });

  useEffect(() => {
    const product = detail.data;
    if (!product) return;
    setForm(fromDetail(product));
    setAliases(product.aliases.map((alias) => alias.alias).join('\n'));
    setSpecs(
      product.specs.map((spec) => ({
        label: spec.label,
        value: spec.value,
        unit: spec.unit ?? '',
        is_critical: spec.is_critical,
      })),
    );
  }, [detail.data]);

  function fromDetail(product: ProductDetail) {
    return {
      name: product.name,
      slug: product.slug,
      brand_id: product.brand_id,
      category_id: product.category_id,
      model_number: product.model_number ?? '',
      release_year: product.release_year ? String(product.release_year) : '',
      summary: product.summary ?? '',
      technical_notes: product.technical_notes ?? '',
      connector_type: product.connector_type ?? '',
      form_factor: product.form_factor ?? '',
      status: product.status,
    };
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    save.mutate();
  }

  return (
    <Modal
      title={id ? 'Edit product' : 'New product'}
      onClose={onClose}
      wide
      footer={
        <>
          <button type="button" className="button secondary" onClick={onClose}>
            Cancel
          </button>
          <button
            type="submit"
            form="product-form"
            className="button primary"
            disabled={save.isPending || detail.isLoading}
          >
            {save.isPending ? 'Saving…' : 'Save product'}
          </button>
        </>
      }
    >
      {detail.isLoading ? <LoadingPanel label="Loading product…" /> : null}
      {detail.error ? <ErrorPanel error={detail.error} /> : null}
      {!detail.isLoading ? (
        <form id="product-form" className="form-grid" onSubmit={submit}>
          {error ? (
            <div className="notice error form-span" role="alert">
              {error}
            </div>
          ) : null}
          <label>
            Name
            <input
              required
              maxLength={160}
              value={form.name}
              onChange={(event) => setForm({ ...form, name: event.target.value })}
            />
          </label>
          <label>
            Slug
            <input
              required
              maxLength={80}
              pattern="[a-z0-9]+(-[a-z0-9]+)*"
              value={form.slug}
              onChange={(event) => setForm({ ...form, slug: event.target.value })}
            />
          </label>
          <label>
            Brand
            <select
              required
              value={form.brand_id}
              onChange={(event) => setForm({ ...form, brand_id: event.target.value })}
            >
              <option value="">Choose a brand…</option>
              {brands.map((brand) => (
                <option key={brand.id} value={brand.id}>
                  {brand.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Category
            <select
              required
              value={form.category_id}
              onChange={(event) => setForm({ ...form, category_id: event.target.value })}
            >
              <option value="">Choose a category…</option>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                  {category.is_public ? '' : ' (private)'}
                </option>
              ))}
            </select>
          </label>
          <label>
            Model number
            <input
              maxLength={120}
              value={form.model_number}
              onChange={(event) => setForm({ ...form, model_number: event.target.value })}
            />
          </label>
          <label>
            Release year
            <input
              type="number"
              min={1900}
              max={2200}
              value={form.release_year}
              onChange={(event) => setForm({ ...form, release_year: event.target.value })}
            />
          </label>
          <label>
            Connector type
            <input
              maxLength={120}
              value={form.connector_type}
              onChange={(event) => setForm({ ...form, connector_type: event.target.value })}
            />
          </label>
          <label>
            Form factor
            <input
              maxLength={120}
              value={form.form_factor}
              onChange={(event) => setForm({ ...form, form_factor: event.target.value })}
            />
          </label>
          <label>
            Status
            <select
              value={form.status}
              onChange={(event) =>
                setForm({ ...form, status: event.target.value as ProductStatus })
              }
            >
              <option value="draft">Draft</option>
              <option value="published">Published</option>
              <option value="archived">Archived</option>
            </select>
          </label>
          <label className="form-span">
            Summary
            <textarea
              rows={3}
              maxLength={1000}
              value={form.summary}
              onChange={(event) => setForm({ ...form, summary: event.target.value })}
            />
          </label>
          <label className="form-span">
            Technical notes
            <textarea
              rows={4}
              maxLength={8000}
              value={form.technical_notes}
              onChange={(event) => setForm({ ...form, technical_notes: event.target.value })}
            />
          </label>
          <label className="form-span">
            Aliases (one per line)
            <textarea
              rows={3}
              value={aliases}
              onChange={(event) => setAliases(event.target.value)}
            />
          </label>

          <fieldset className="form-span fieldset">
            <legend>Specifications</legend>
            {specs.length === 0 ? <p className="muted">No specifications recorded.</p> : null}
            {specs.map((spec, index) => (
              <div key={index} className="spec-row">
                <label>
                  Label
                  <input
                    value={spec.label}
                    onChange={(event) => {
                      const next = [...specs];
                      next[index] = { ...spec, label: event.target.value };
                      setSpecs(next);
                    }}
                  />
                </label>
                <label>
                  Value
                  <input
                    value={spec.value}
                    onChange={(event) => {
                      const next = [...specs];
                      next[index] = { ...spec, value: event.target.value };
                      setSpecs(next);
                    }}
                  />
                </label>
                <label>
                  Unit
                  <input
                    value={spec.unit}
                    onChange={(event) => {
                      const next = [...specs];
                      next[index] = { ...spec, unit: event.target.value };
                      setSpecs(next);
                    }}
                  />
                </label>
                <label className="checkbox-label">
                  <input
                    type="checkbox"
                    checked={spec.is_critical}
                    onChange={(event) => {
                      const next = [...specs];
                      next[index] = { ...spec, is_critical: event.target.checked };
                      setSpecs(next);
                    }}
                  />
                  Critical
                </label>
                <button
                  type="button"
                  className="button small danger"
                  onClick={() => setSpecs(specs.filter((_, row) => row !== index))}
                >
                  Remove
                </button>
              </div>
            ))}
            <button
              type="button"
              className="button secondary small"
              onClick={() =>
                setSpecs([...specs, { label: '', value: '', unit: '', is_critical: false }])
              }
            >
              Add specification
            </button>
          </fieldset>
        </form>
      ) : null}
    </Modal>
  );
}
