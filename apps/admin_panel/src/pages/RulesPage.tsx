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
  getRuleEvidence,
  listProductOptions,
  listRuleVersions,
  listRules,
  rollbackRule,
  saveRule,
  type EvidenceInput,
  type RuleInput,
} from '../lib/api';
import { formatDate, prettyJson, titleCase } from '../lib/format';
import type {
  EvidenceType,
  Relationship,
  Risk,
  RuleStatus,
  RuleVersion,
  RuleWithMeta,
  Verification,
} from '../lib/types';

const relationships: Relationship[] = [
  'fits',
  'works_with',
  'same_as',
  'variant_of',
  'not_compatible_with',
];
const verifications: Verification[] = [
  'official',
  'community_verified',
  'needs_verification',
  'experimental',
];
const risks: Risk[] = ['none', 'low', 'moderate', 'high', 'critical'];
const evidenceTypes: EvidenceType[] = [
  'oem_document',
  'service_bulletin',
  'teardown',
  'lab_test',
  'community_report',
  'other',
];
const ruleStatuses: RuleStatus[] = [
  'draft',
  'pending_review',
  'published',
  'rejected',
  'retired',
];

interface EvidenceRow extends EvidenceInput {
  key: string;
}

const emptyRule: RuleInput = {
  subject_product_id: '',
  object_product_id: '',
  relationship: 'fits',
  verification: 'needs_verification',
  confidence: 50,
  risk: 'none',
  summary: '',
  technical_details: '',
  required_modifications: '',
  status: 'draft',
};

export function RulesPage() {
  const { me } = useAuth();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [appliedSearch, setAppliedSearch] = useState('');
  const [status, setStatus] = useState<RuleStatus | ''>('');
  const [relationship, setRelationship] = useState<Relationship | ''>('');
  const [editor, setEditor] = useState<{ rule?: RuleWithMeta } | null>(null);
  const [history, setHistory] = useState<RuleWithMeta | null>(null);

  const rules = useQuery({
    queryKey: ['rules', page, appliedSearch, status, relationship],
    queryFn: () =>
      listRules({ page, query: appliedSearch, status, relationship }),
  });
  const products = useQuery({ queryKey: ['product-options'], queryFn: listProductOptions });

  async function saved() {
    setEditor(null);
    await queryClient.invalidateQueries({ queryKey: ['rules'] });
    await queryClient.invalidateQueries({ queryKey: ['dashboard'] });
  }

  function applyFilters(event: FormEvent) {
    event.preventDefault();
    setPage(0);
    setAppliedSearch(search.trim());
  }

  return (
    <>
      <PageHeader
        title="Compatibility rules"
        description="Every claim requires citations. Publishing checks the evidence count and that both products are published. Symmetric relationships are stored in canonical order."
        actions={
          <button type="button" className="button primary" onClick={() => setEditor({})}>
            New rule
          </button>
        }
      />

      <form className="filter-bar" onSubmit={applyFilters} role="search">
        <label>
          Summary contains
          <input value={search} onChange={(event) => setSearch(event.target.value)} />
        </label>
        <label>
          Status
          <select
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as RuleStatus | '');
              setPage(0);
            }}
          >
            <option value="">All</option>
            {ruleStatuses.map((value) => (
              <option key={value} value={value}>
                {titleCase(value)}
              </option>
            ))}
          </select>
        </label>
        <label>
          Relationship
          <select
            value={relationship}
            onChange={(event) => {
              setRelationship(event.target.value as Relationship | '');
              setPage(0);
            }}
          >
            <option value="">All</option>
            {relationships.map((value) => (
              <option key={value} value={value}>
                {titleCase(value)}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="button secondary">
          Apply
        </button>
      </form>

      {rules.isLoading ? <LoadingPanel label="Loading rules…" /> : null}
      {rules.error ? <ErrorPanel error={rules.error} retry={() => void rules.refetch()} /> : null}
      {rules.data && rules.data.items.length === 0 ? (
        <EmptyPanel>No rules match these filters.</EmptyPanel>
      ) : null}
      {rules.data && rules.data.items.length > 0 ? (
        <div className="card table-card">
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">Claim</th>
                  <th scope="col">Relationship</th>
                  <th scope="col">Verification</th>
                  <th scope="col">Confidence</th>
                  <th scope="col">Evidence</th>
                  <th scope="col">Status</th>
                  <th scope="col">Actions</th>
                </tr>
              </thead>
              <tbody>
                {rules.data.items.map((rule) => (
                  <tr key={rule.id}>
                    <td>
                      <strong>{rule.subject_name}</strong>
                      <div className="muted small">
                        → {rule.object_name}
                        <br />
                        {rule.summary}
                      </div>
                    </td>
                    <td>{titleCase(rule.relationship)}</td>
                    <td>
                      <Badge value={rule.verification} />
                      <div className="muted small">{titleCase(rule.risk)} risk</div>
                    </td>
                    <td>{rule.confidence}%</td>
                    <td>{rule.evidence_count}</td>
                    <td>
                      <Badge value={rule.status} />
                    </td>
                    <td>
                      <div className="row-actions">
                        <button
                          type="button"
                          className="button small secondary"
                          onClick={() => setEditor({ rule })}
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          className="button small secondary"
                          onClick={() => setHistory(rule)}
                        >
                          History
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
            pageSize={rules.data.limit}
            total={rules.data.total}
            onPageChange={setPage}
          />
        </div>
      ) : null}

      {editor ? (
        <RuleEditor
          rule={editor.rule}
          products={products.data ?? []}
          actorId={me?.id ?? ''}
          onClose={() => setEditor(null)}
          onSaved={() => void saved()}
        />
      ) : null}
      {history ? (
        <RuleHistory
          rule={history}
          onClose={() => setHistory(null)}
          onRolledBack={() => {
            setHistory(null);
            void queryClient.invalidateQueries({ queryKey: ['rules'] });
          }}
        />
      ) : null}
    </>
  );
}

function makeEvidenceRow(partial?: Partial<EvidenceInput>): EvidenceRow {
  return {
    key: crypto.randomUUID(),
    evidence_type: 'oem_document',
    title: '',
    source_url: '',
    publisher: '',
    excerpt: '',
    captured_at: '',
    ...partial,
  };
}

function RuleEditor({
  rule,
  products,
  actorId,
  onClose,
  onSaved,
}: {
  rule?: RuleWithMeta;
  products: Array<{ id: string; name: string; slug: string; status: string }>;
  actorId: string;
  onClose(): void;
  onSaved(): void;
}) {
  const evidenceQuery = useQuery({
    queryKey: ['rule-evidence', rule?.id],
    queryFn: () => getRuleEvidence(rule!.id),
    enabled: Boolean(rule?.id),
  });
  const [form, setForm] = useState<RuleInput>(() =>
    rule
      ? {
          subject_product_id: rule.subject_product_id,
          object_product_id: rule.object_product_id,
          relationship: rule.relationship,
          verification: rule.verification,
          confidence: rule.confidence,
          risk: rule.risk,
          summary: rule.summary,
          technical_details: rule.technical_details ?? '',
          required_modifications: rule.required_modifications ?? '',
          status: rule.status,
        }
      : { ...emptyRule },
  );
  const [evidence, setEvidence] = useState<EvidenceRow[]>([makeEvidenceRow()]);
  const [error, setError] = useState<string | null>(null);
  const save = useMutation({
    mutationFn: async () => {
      if (!form.subject_product_id || !form.object_product_id) {
        throw new Error('Choose both products');
      }
      if (form.subject_product_id === form.object_product_id) {
        throw new Error('A product cannot be compared with itself');
      }
      if (form.summary.trim().length < 10 || form.summary.trim().length > 300) {
        throw new Error('Summary must contain between 10 and 300 characters');
      }
      if (!Number.isInteger(form.confidence) || form.confidence < 0 || form.confidence > 100) {
        throw new Error('Confidence must be an integer from 0 to 100');
      }
      for (const [index, item] of evidence.entries()) {
        if (item.title.trim().length < 3) {
          throw new Error(`Evidence ${index + 1} needs a title`);
        }
        if (!/^https?:\/\//i.test(item.source_url.trim())) {
          throw new Error(`Evidence ${index + 1} needs an http(s) source URL`);
        }
      }
      if (form.status === 'published' && evidence.length === 0) {
        throw new Error('At least one evidence item is required to publish');
      }
      return saveRule(
        {
          ...form,
          summary: form.summary.trim(),
          technical_details: form.technical_details?.trim() || null,
          required_modifications: form.required_modifications?.trim() || null,
        },
        evidence.map(({ key: _key, ...item }) => ({
          ...item,
          title: item.title.trim(),
          source_url: item.source_url.trim(),
          publisher: item.publisher?.trim() || null,
          excerpt: item.excerpt?.trim() || null,
          captured_at: item.captured_at || null,
        })),
        actorId,
        rule?.id,
      );
    },
    onSuccess: onSaved,
    onError: (cause) => setError(cause instanceof Error ? cause.message : String(cause)),
  });

  useEffect(() => {
    if (!evidenceQuery.data) return;
    setEvidence(
      evidenceQuery.data.map((item) => ({
        id: item.id,
        key: item.id,
        evidence_type: item.evidence_type,
        title: item.title,
        source_url: item.source_url,
        publisher: item.publisher ?? '',
        excerpt: item.excerpt ?? '',
        captured_at: item.captured_at ?? '',
      })),
    );
  }, [evidenceQuery.data]);

  function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    save.mutate();
  }

  return (
    <Modal
      title={rule ? 'Edit compatibility rule' : 'New compatibility rule'}
      onClose={onClose}
      wide
      footer={
        <>
          <button type="button" className="button secondary" onClick={onClose}>
            Cancel
          </button>
          <button
            type="submit"
            form="rule-form"
            className="button primary"
            disabled={save.isPending || evidenceQuery.isLoading}
          >
            {save.isPending ? 'Saving…' : 'Save rule'}
          </button>
        </>
      }
    >
      {evidenceQuery.isLoading ? <LoadingPanel label="Loading evidence…" /> : null}
      {evidenceQuery.error ? <ErrorPanel error={evidenceQuery.error} /> : null}
      <form id="rule-form" className="form-grid" onSubmit={submit}>
        {error ? (
          <div className="notice error form-span" role="alert">
            {error}
          </div>
        ) : null}
        <label>
          Subject product
          <select
            required
            value={form.subject_product_id}
            onChange={(event) =>
              setForm({ ...form, subject_product_id: event.target.value })
            }
          >
            <option value="">Choose a product…</option>
            {products.map((product) => (
              <option key={product.id} value={product.id}>
                {product.name} ({product.status})
              </option>
            ))}
          </select>
        </label>
        <label>
          Object product
          <select
            required
            value={form.object_product_id}
            onChange={(event) =>
              setForm({ ...form, object_product_id: event.target.value })
            }
          >
            <option value="">Choose a product…</option>
            {products.map((product) => (
              <option key={product.id} value={product.id}>
                {product.name} ({product.status})
              </option>
            ))}
          </select>
        </label>
        <label>
          Relationship
          <select
            value={form.relationship}
            onChange={(event) =>
              setForm({ ...form, relationship: event.target.value as Relationship })
            }
          >
            {relationships.map((value) => (
              <option key={value} value={value}>
                {titleCase(value)}
              </option>
            ))}
          </select>
        </label>
        <label>
          Verification
          <select
            value={form.verification}
            onChange={(event) =>
              setForm({ ...form, verification: event.target.value as Verification })
            }
          >
            {verifications.map((value) => (
              <option key={value} value={value}>
                {titleCase(value)}
              </option>
            ))}
          </select>
        </label>
        <label>
          Confidence (0–100)
          <input
            type="number"
            min={0}
            max={100}
            required
            value={form.confidence}
            onChange={(event) =>
              setForm({ ...form, confidence: Number(event.target.value) })
            }
          />
        </label>
        <label>
          Risk
          <select
            value={form.risk}
            onChange={(event) => setForm({ ...form, risk: event.target.value as Risk })}
          >
            {risks.map((value) => (
              <option key={value} value={value}>
                {titleCase(value)}
              </option>
            ))}
          </select>
        </label>
        <label>
          Status
          <select
            value={form.status}
            onChange={(event) =>
              setForm({ ...form, status: event.target.value as RuleStatus })
            }
          >
            {ruleStatuses.map((value) => (
              <option key={value} value={value}>
                {titleCase(value)}
              </option>
            ))}
          </select>
        </label>
        <p className="muted small form-span">
          Publishing requires citations and published products. Only explicit evidence should be
          recorded; do not infer compatibility from similar specifications.
        </p>
        <label className="form-span">
          Summary
          <textarea
            required
            minLength={10}
            maxLength={300}
            rows={2}
            value={form.summary}
            onChange={(event) => setForm({ ...form, summary: event.target.value })}
          />
        </label>
        <label className="form-span">
          Technical details
          <textarea
            rows={3}
            maxLength={8000}
            value={form.technical_details ?? ''}
            onChange={(event) => setForm({ ...form, technical_details: event.target.value })}
          />
        </label>
        <label className="form-span">
          Required modifications
          <textarea
            rows={2}
            maxLength={2000}
            value={form.required_modifications ?? ''}
            onChange={(event) =>
              setForm({ ...form, required_modifications: event.target.value })
            }
          />
        </label>

        <fieldset className="form-span fieldset">
          <legend>Evidence ({evidence.length})</legend>
          {evidence.length === 0 ? (
            <p className="notice warning">A published rule cannot have zero evidence.</p>
          ) : null}
          {evidence.map((item, index) => (
            <div className="evidence-editor" key={item.key}>
              <div className="evidence-heading">
                <strong>Evidence {index + 1}</strong>
                <button
                  type="button"
                  className="button small danger"
                  onClick={() => setEvidence(evidence.filter((row) => row.key !== item.key))}
                >
                  Remove
                </button>
              </div>
              <div className="form-grid">
                <label>
                  Type
                  <select
                    value={item.evidence_type}
                    onChange={(event) => {
                      const next = [...evidence];
                      next[index] = {
                        ...item,
                        evidence_type: event.target.value as EvidenceType,
                      };
                      setEvidence(next);
                    }}
                  >
                    {evidenceTypes.map((value) => (
                      <option key={value} value={value}>
                        {titleCase(value)}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Captured date
                  <input
                    type="date"
                    value={item.captured_at ?? ''}
                    onChange={(event) => {
                      const next = [...evidence];
                      next[index] = { ...item, captured_at: event.target.value };
                      setEvidence(next);
                    }}
                  />
                </label>
                <label className="form-span">
                  Title
                  <input
                    required
                    maxLength={250}
                    value={item.title}
                    onChange={(event) => {
                      const next = [...evidence];
                      next[index] = { ...item, title: event.target.value };
                      setEvidence(next);
                    }}
                  />
                </label>
                <label className="form-span">
                  Source URL
                  <input
                    required
                    type="url"
                    value={item.source_url}
                    onChange={(event) => {
                      const next = [...evidence];
                      next[index] = { ...item, source_url: event.target.value };
                      setEvidence(next);
                    }}
                  />
                </label>
                <label>
                  Publisher
                  <input
                    maxLength={150}
                    value={item.publisher ?? ''}
                    onChange={(event) => {
                      const next = [...evidence];
                      next[index] = { ...item, publisher: event.target.value };
                      setEvidence(next);
                    }}
                  />
                </label>
                <label className="form-span">
                  Excerpt
                  <textarea
                    rows={2}
                    maxLength={4000}
                    value={item.excerpt ?? ''}
                    onChange={(event) => {
                      const next = [...evidence];
                      next[index] = { ...item, excerpt: event.target.value };
                      setEvidence(next);
                    }}
                  />
                </label>
              </div>
            </div>
          ))}
          <button
            type="button"
            className="button secondary small"
            onClick={() => setEvidence([...evidence, makeEvidenceRow()])}
          >
            Add evidence
          </button>
        </fieldset>
      </form>
    </Modal>
  );
}

function RuleHistory({
  rule,
  onClose,
  onRolledBack,
}: {
  rule: RuleWithMeta;
  onClose(): void;
  onRolledBack(): void;
}) {
  const versions = useQuery({
    queryKey: ['rule-versions', rule.id],
    queryFn: () => listRuleVersions(rule.id),
  });
  const [target, setTarget] = useState<RuleVersion | null>(null);
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const rollback = useMutation({
    mutationFn: () => rollbackRule(rule.id, target!.version, reason),
    onSuccess: onRolledBack,
    onError: (cause) => setError(cause instanceof Error ? cause.message : String(cause)),
  });

  return (
    <Modal title={`Version history — ${rule.summary}`} onClose={onClose} wide>
      {versions.isLoading ? <LoadingPanel label="Loading versions…" /> : null}
      {versions.error ? <ErrorPanel error={versions.error} /> : null}
      {error ? (
        <div className="notice error" role="alert">
          {error}
        </div>
      ) : null}
      {versions.data ? (
        <ol className="version-list">
          {versions.data.map((version) => (
            <li key={version.id}>
              <div className="card-header">
                <strong>Version {version.version}</strong>
                <span className="muted">{formatDate(version.created_at)}</span>
              </div>
              <p className="muted small">
                {version.change_reason ?? 'No reason recorded'} ·{' '}
                {version.changed_by ?? 'System'}
              </p>
              <details>
                <summary>Snapshot</summary>
                <pre>{prettyJson(version.snapshot)}</pre>
              </details>
              <button
                type="button"
                className="button small secondary"
                onClick={() => {
                  setTarget(version);
                  setError(null);
                }}
              >
                Restore this version
              </button>
            </li>
          ))}
        </ol>
      ) : null}

      {target ? (
        <div className="notice warning">
          <h3>Roll back to version {target.version}</h3>
          <p>
            A new snapshot recording the rollback will be appended. Existing evidence is preserved
            and snapshot evidence is re-added where needed.
          </p>
          <label>
            Reason (required)
            <textarea
              rows={2}
              minLength={5}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
            />
          </label>
          <div className="row-actions">
            <button type="button" className="button secondary" onClick={() => setTarget(null)}>
              Cancel
            </button>
            <button
              type="button"
              className="button danger"
              disabled={reason.trim().length < 5 || rollback.isPending}
              onClick={() => rollback.mutate()}
            >
              {rollback.isPending ? 'Restoring…' : 'Confirm rollback'}
            </button>
          </div>
        </div>
      ) : null}
    </Modal>
  );
}
