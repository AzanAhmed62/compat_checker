import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
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
  getGuide,
  listGuides,
  listProductOptions,
  saveGuide,
  type GuideInput,
} from '../lib/api';
import { formatDate, truncate } from '../lib/format';
import type { GuideStatus } from '../lib/types';

const statuses: GuideStatus[] = ['draft', 'pending_review', 'published', 'archived'];
const difficulties = ['easy', 'moderate', 'difficult', 'expert'];

interface StepDraft {
  key: string;
  title: string;
  body: string;
  image_path: string;
}

export function GuidesPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<GuideStatus | ''>('');
  const [search, setSearch] = useState('');
  const [appliedSearch, setAppliedSearch] = useState('');
  const [editor, setEditor] = useState<{ id?: string } | null>(null);

  const guides = useQuery({
    queryKey: ['guides', page, status, appliedSearch],
    queryFn: () => listGuides({ page, status, query: appliedSearch }),
  });
  const products = useQuery({ queryKey: ['product-options'], queryFn: listProductOptions });

  async function saved() {
    setEditor(null);
    await queryClient.invalidateQueries({ queryKey: ['guides'] });
  }

  function searchSubmit(event: FormEvent) {
    event.preventDefault();
    setPage(0);
    setAppliedSearch(search.trim());
  }

  return (
    <>
      <PageHeader
        title="Repair and usage guides"
        description="Manage ordered steps and Markdown content. Published guides are public; drafts and archived guides are administrator-only."
        actions={
          <button type="button" className="button primary" onClick={() => setEditor({})}>
            New guide
          </button>
        }
      />
      <form className="filter-bar" onSubmit={searchSubmit} role="search">
        <label>
          Title contains
          <input value={search} onChange={(event) => setSearch(event.target.value)} />
        </label>
        <label>
          Status
          <select
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as GuideStatus | '');
              setPage(0);
            }}
          >
            <option value="">All statuses</option>
            {statuses.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="button secondary">
          Apply
        </button>
      </form>

      {guides.isLoading ? <LoadingPanel label="Loading guides…" /> : null}
      {guides.error ? (
        <ErrorPanel error={guides.error} retry={() => void guides.refetch()} />
      ) : null}
      {guides.data && guides.data.items.length === 0 ? (
        <EmptyPanel>No guides match these filters.</EmptyPanel>
      ) : null}
      {guides.data && guides.data.items.length > 0 ? (
        <div className="card table-card">
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">Guide</th>
                  <th scope="col">Difficulty</th>
                  <th scope="col">Status</th>
                  <th scope="col">Updated</th>
                  <th scope="col">Action</th>
                </tr>
              </thead>
              <tbody>
                {guides.data.items.map((guide) => (
                  <tr key={guide.id}>
                    <td>
                      <strong>{guide.title}</strong>
                      <div className="muted small">{truncate(guide.summary, 120)}</div>
                      <code className="small">{guide.slug}</code>
                    </td>
                    <td>{guide.difficulty}</td>
                    <td>
                      <Badge value={guide.status} />
                    </td>
                    <td>{formatDate(guide.updated_at)}</td>
                    <td>
                      <button
                        type="button"
                        className="button small secondary"
                        onClick={() => setEditor({ id: guide.id })}
                      >
                        Edit
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={page}
            pageSize={guides.data.limit}
            total={guides.data.total}
            onPageChange={setPage}
          />
        </div>
      ) : null}

      {editor ? (
        <GuideEditor
          id={editor.id}
          products={(products.data ?? []).filter((product) => product.status === 'published')}
          onClose={() => setEditor(null)}
          onSaved={() => void saved()}
        />
      ) : null}
    </>
  );
}

function GuideEditor({
  id,
  products,
  onClose,
  onSaved,
}: {
  id?: string;
  products: Array<{ id: string; name: string }>;
  onClose(): void;
  onSaved(): void;
}) {
  const detail = useQuery({
    queryKey: ['guide', id],
    queryFn: () => getGuide(id!),
    enabled: Boolean(id),
  });
  const [form, setForm] = useState<GuideInput>({
    slug: '',
    title: '',
    summary: '',
    body_md: '',
    difficulty: 'moderate',
    status: 'draft',
    product_id: null,
  });
  const [steps, setSteps] = useState<StepDraft[]>([]);
  const [error, setError] = useState<string | null>(null);
  const save = useMutation({
    mutationFn: async () => {
      if (!/^[a-z0-9]+(-[a-z0-9]+)*$/.test(form.slug)) {
        throw new Error('Slug must be lowercase words separated by single hyphens');
      }
      if (form.title.trim().length < 3) throw new Error('Title is too short');
      if (form.summary.trim().length < 10) throw new Error('Summary must be at least 10 characters');
      if (form.body_md.trim().length < 20) throw new Error('Body must be at least 20 characters');
      for (const [index, step] of steps.entries()) {
        if (!step.title.trim() || !step.body.trim()) {
          throw new Error(`Step ${index + 1} requires a title and body`);
        }
      }
      return saveGuide(
        {
          ...form,
          title: form.title.trim(),
          summary: form.summary.trim(),
          body_md: form.body_md.trim(),
        },
        steps.map((step) => ({
          title: step.title.trim(),
          body: step.body.trim(),
          image_path: step.image_path.trim() || null,
        })),
        id,
      );
    },
    onSuccess: onSaved,
    onError: (cause) => setError(cause instanceof Error ? cause.message : String(cause)),
  });

  useEffect(() => {
    const guide = detail.data;
    if (!guide) return;
    setForm({
      slug: guide.slug,
      title: guide.title,
      summary: guide.summary,
      body_md: guide.body_md,
      difficulty: guide.difficulty,
      status: guide.status,
      product_id: guide.product_id,
    });
    setSteps(
      guide.steps.map((step) => ({
        key: step.id,
        title: step.title,
        body: step.body,
        image_path: step.image_path ?? '',
      })),
    );
  }, [detail.data]);

  function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    save.mutate();
  }

  return (
    <Modal
      title={id ? 'Edit guide' : 'New guide'}
      onClose={onClose}
      wide
      footer={
        <>
          <button type="button" className="button secondary" onClick={onClose}>
            Cancel
          </button>
          <button
            type="submit"
            form="guide-form"
            className="button primary"
            disabled={save.isPending || detail.isLoading}
          >
            {save.isPending ? 'Saving…' : 'Save guide'}
          </button>
        </>
      }
    >
      {detail.isLoading ? <LoadingPanel label="Loading guide…" /> : null}
      {detail.error ? <ErrorPanel error={detail.error} /> : null}
      <form id="guide-form" className="form-grid" onSubmit={submit}>
        {error ? (
          <div className="notice error form-span" role="alert">
            {error}
          </div>
        ) : null}
        <label>
          Title
          <input
            required
            maxLength={200}
            value={form.title}
            onChange={(event) => setForm({ ...form, title: event.target.value })}
          />
        </label>
        <label>
          Slug
          <input
            required
            pattern="[a-z0-9]+(-[a-z0-9]+)*"
            value={form.slug}
            onChange={(event) => setForm({ ...form, slug: event.target.value })}
          />
        </label>
        <label>
          Difficulty
          <select
            value={form.difficulty}
            onChange={(event) => setForm({ ...form, difficulty: event.target.value })}
          >
            {difficulties.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </label>
        <label>
          Status
          <select
            value={form.status}
            onChange={(event) =>
              setForm({ ...form, status: event.target.value as GuideStatus })
            }
          >
            {statuses.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </label>
        <label className="form-span">
          Related product
          <select
            value={form.product_id ?? ''}
            onChange={(event) =>
              setForm({ ...form, product_id: event.target.value || null })
            }
          >
            <option value="">No specific product</option>
            {products.map((product) => (
              <option key={product.id} value={product.id}>
                {product.name}
              </option>
            ))}
          </select>
        </label>
        <label className="form-span">
          Summary
          <textarea
            required
            minLength={10}
            maxLength={1000}
            rows={2}
            value={form.summary}
            onChange={(event) => setForm({ ...form, summary: event.target.value })}
          />
        </label>
        <label className="form-span">
          Markdown body
          <textarea
            required
            minLength={20}
            maxLength={50000}
            rows={8}
            value={form.body_md}
            onChange={(event) => setForm({ ...form, body_md: event.target.value })}
          />
        </label>

        <fieldset className="form-span fieldset">
          <legend>Steps ({steps.length})</legend>
          {steps.length === 0 ? <p className="muted">No ordered steps yet.</p> : null}
          {steps.map((step, index) => (
            <div className="evidence-editor" key={step.key}>
              <div className="evidence-heading">
                <strong>Step {index + 1}</strong>
                <div className="row-actions">
                  <button
                    type="button"
                    className="button small secondary"
                    disabled={index === 0}
                    onClick={() => {
                      const next = [...steps];
                      [next[index - 1], next[index]] = [next[index]!, next[index - 1]!];
                      setSteps(next);
                    }}
                  >
                    Move up
                  </button>
                  <button
                    type="button"
                    className="button small secondary"
                    disabled={index === steps.length - 1}
                    onClick={() => {
                      const next = [...steps];
                      [next[index], next[index + 1]] = [next[index + 1]!, next[index]!];
                      setSteps(next);
                    }}
                  >
                    Move down
                  </button>
                  <button
                    type="button"
                    className="button small danger"
                    onClick={() => setSteps(steps.filter((row) => row.key !== step.key))}
                  >
                    Remove
                  </button>
                </div>
              </div>
              <div className="form-grid">
                <label>
                  Step title
                  <input
                    maxLength={160}
                    value={step.title}
                    onChange={(event) => {
                      const next = [...steps];
                      next[index] = { ...step, title: event.target.value };
                      setSteps(next);
                    }}
                  />
                </label>
                <label>
                  Image path (optional)
                  <input
                    value={step.image_path}
                    onChange={(event) => {
                      const next = [...steps];
                      next[index] = { ...step, image_path: event.target.value };
                      setSteps(next);
                    }}
                  />
                </label>
                <label className="form-span">
                  Instructions
                  <textarea
                    rows={3}
                    maxLength={5000}
                    value={step.body}
                    onChange={(event) => {
                      const next = [...steps];
                      next[index] = { ...step, body: event.target.value };
                      setSteps(next);
                    }}
                  />
                </label>
              </div>
            </div>
          ))}
          <button
            type="button"
            className="button secondary small"
            onClick={() =>
              setSteps([
                ...steps,
                { key: crypto.randomUUID(), title: '', body: '', image_path: '' },
              ])
            }
          >
            Add step
          </button>
        </fieldset>
      </form>
    </Modal>
  );
}
