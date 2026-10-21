import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../auth/AuthProvider';
import { Modal } from '../components/Modal';
import {
  Badge,
  EmptyPanel,
  ErrorPanel,
  LoadingPanel,
  PageHeader,
} from '../components/PageParts';
import {
  deleteMedia,
  listMediaAssets,
  listProductMedia,
  listProductOptions,
  publicMediaUrl,
  setMediaStatus,
  uploadProductMedia,
} from '../lib/api';
import { formatDate } from '../lib/format';
import type { ProductMedia } from '../lib/types';

export function MediaPage() {
  const { me } = useAuth();
  const queryClient = useQueryClient();
  const [uploadOpen, setUploadOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<ProductMedia | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const media = useQuery({ queryKey: ['product-media'], queryFn: listProductMedia });
  const assets = useQuery({ queryKey: ['media-assets'], queryFn: listMediaAssets });
  const products = useQuery({ queryKey: ['product-options'], queryFn: listProductOptions });

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: 'pending' | 'approved' | 'rejected' }) =>
      setMediaStatus(id, status),
    onSuccess: async () => {
      setActionError(null);
      await queryClient.invalidateQueries({ queryKey: ['product-media'] });
      await queryClient.invalidateQueries({ queryKey: ['media-assets'] });
    },
    onError: (error) => setActionError(error instanceof Error ? error.message : String(error)),
  });

  const remove = useMutation({
    mutationFn: (item: ProductMedia) => deleteMedia(item),
    onSuccess: async () => {
      setDeleteTarget(null);
      setActionError(null);
      await queryClient.invalidateQueries({ queryKey: ['product-media'] });
      await queryClient.invalidateQueries({ queryKey: ['media-assets'] });
    },
    onError: (error) => setActionError(error instanceof Error ? error.message : String(error)),
  });

  async function uploaded() {
    setUploadOpen(false);
    await queryClient.invalidateQueries({ queryKey: ['product-media'] });
    await queryClient.invalidateQueries({ queryKey: ['media-assets'] });
    await queryClient.invalidateQueries({ queryKey: ['products'] });
  }

  return (
    <>
      <PageHeader
        title="Media"
        description="Public product images live in the public media bucket. Evidence files belong in the private evidence bucket and are never public URLs."
        actions={
          <button type="button" className="button primary" onClick={() => setUploadOpen(true)}>
            Upload product image
          </button>
        }
      />
      {actionError ? (
        <div className="notice error" role="alert">
          {actionError}
        </div>
      ) : null}

      <section className="card" aria-labelledby="product-media-heading">
        <div className="card-header">
          <h2 id="product-media-heading">Product media</h2>
          <button
            type="button"
            className="button secondary small"
            onClick={() => void media.refetch()}
          >
            Refresh
          </button>
        </div>
        {media.isLoading ? <LoadingPanel label="Loading media…" /> : null}
        {media.error ? <ErrorPanel error={media.error} /> : null}
        {media.data && media.data.length === 0 ? (
          <EmptyPanel>No product images have been registered.</EmptyPanel>
        ) : null}
        {media.data && media.data.length > 0 ? (
          <div className="media-grid">
            {media.data.map((item) => (
              <article className="media-card" key={item.id}>
                <img src={publicMediaUrl(item.storage_path)} alt={item.alt_text ?? ''} />
                <div>
                  <strong>{item.product_name ?? 'Unknown product'}</strong>
                  <p className="muted small">
                    {item.alt_text ?? 'No alternative text'} · {formatDate(item.created_at)}
                  </p>
                  <Badge value={item.status} />
                  <div className="row-actions">
                    {item.status !== 'approved' ? (
                      <button
                        type="button"
                        className="button small secondary"
                        onClick={() => statusMutation.mutate({ id: item.id, status: 'approved' })}
                      >
                        Approve
                      </button>
                    ) : null}
                    {item.status !== 'rejected' ? (
                      <button
                        type="button"
                        className="button small secondary"
                        onClick={() => statusMutation.mutate({ id: item.id, status: 'rejected' })}
                      >
                        Reject
                      </button>
                    ) : null}
                    <button
                      type="button"
                      className="button small danger"
                      onClick={() => {
                        setActionError(null);
                        setDeleteTarget(item);
                      }}
                    >
                      Delete
                    </button>
                  </div>
                </div>
              </article>
            ))}
          </div>
        ) : null}
      </section>

      <section className="card" aria-labelledby="assets-heading">
        <div className="card-header">
          <h2 id="assets-heading">Media registry</h2>
        </div>
        {assets.isLoading ? <LoadingPanel label="Loading registry…" /> : null}
        {assets.error ? <ErrorPanel error={assets.error} /> : null}
        {assets.data && assets.data.length === 0 ? (
          <EmptyPanel>No registry entries.</EmptyPanel>
        ) : null}
        {assets.data && assets.data.length > 0 ? (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">Bucket</th>
                  <th scope="col">Path</th>
                  <th scope="col">Target</th>
                  <th scope="col">Status</th>
                  <th scope="col">Created</th>
                </tr>
              </thead>
              <tbody>
                {assets.data.map((asset) => (
                  <tr key={asset.id}>
                    <td>{asset.bucket}</td>
                    <td>
                      <code>{asset.storage_path}</code>
                    </td>
                    <td>{asset.target_type ?? '—'}</td>
                    <td>
                      <Badge value={asset.status} />
                    </td>
                    <td>{formatDate(asset.created_at)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </section>

      {uploadOpen ? (
        <UploadMedia
          products={(products.data ?? []).filter((product) => product.status === 'published')}
          actorId={me?.id ?? ''}
          onClose={() => setUploadOpen(false)}
          onDone={() => void uploaded()}
        />
      ) : null}

      {deleteTarget ? (
        <Modal
          title="Delete media file?"
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
                onClick={() => remove.mutate(deleteTarget)}
              >
                {remove.isPending ? 'Deleting…' : 'Delete file'}
              </button>
            </>
          }
        >
          <p>
            This removes the storage object, product-media row, and registry entry. Public pages
            will lose the image immediately.
          </p>
          {actionError ? (
            <div className="notice error" role="alert">
              {actionError}
            </div>
          ) : null}
        </Modal>
      ) : null}
    </>
  );
}

function UploadMedia({
  products,
  actorId,
  onClose,
  onDone,
}: {
  products: Array<{ id: string; name: string }>;
  actorId: string;
  onClose(): void;
  onDone(): void;
}) {
  const [productId, setProductId] = useState('');
  const [altText, setAltText] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const upload = useMutation({
    mutationFn: async () => {
      if (!productId) throw new Error('Choose a published product');
      if (!file) throw new Error('Choose an image file');
      await uploadProductMedia({ productId, file, altText, actorId });
    },
    onSuccess: onDone,
    onError: (cause) => setError(cause instanceof Error ? cause.message : String(cause)),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    upload.mutate();
  }

  return (
    <Modal
      title="Upload product image"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="button secondary" onClick={onClose}>
            Cancel
          </button>
          <button
            type="submit"
            form="media-form"
            className="button primary"
            disabled={upload.isPending}
          >
            {upload.isPending ? 'Uploading…' : 'Upload'}
          </button>
        </>
      }
    >
      <form id="media-form" className="form-stack" onSubmit={submit}>
        {error ? (
          <div className="notice error" role="alert">
            {error}
          </div>
        ) : null}
        <label>
          Published product
          <select
            required
            value={productId}
            onChange={(event) => setProductId(event.target.value)}
          >
            <option value="">Choose a product…</option>
            {products.map((product) => (
              <option key={product.id} value={product.id}>
                {product.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Image
          <input
            type="file"
            accept="image/jpeg,image/png,image/webp"
            required
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
          />
        </label>
        <label>
          Alternative text
          <textarea
            required
            rows={2}
            maxLength={250}
            value={altText}
            onChange={(event) => setAltText(event.target.value)}
            placeholder="Describe the image for screen readers."
          />
        </label>
        <p className="muted small">
          JPEG, PNG, or WebP up to 10 MB. Images are uploaded directly to Supabase Storage; only
          the object path is stored in Postgres.
        </p>
      </form>
    </Modal>
  );
}
