export type Role = 'member' | 'contributor' | 'trusted' | 'admin';
export type AccountStatus = 'active' | 'suspended' | 'banned' | 'deletion_pending' | 'deleted';
export type ProductStatus = 'draft' | 'published' | 'archived';
export type RuleStatus = 'draft' | 'pending_review' | 'published' | 'rejected' | 'retired';
export type Relationship = 'fits' | 'works_with' | 'same_as' | 'variant_of' | 'not_compatible_with';
export type Verification = 'official' | 'community_verified' | 'needs_verification' | 'experimental';
export type Risk = 'none' | 'low' | 'moderate' | 'high' | 'critical';
export type EvidenceType =
  | 'oem_document'
  | 'service_bulletin'
  | 'teardown'
  | 'lab_test'
  | 'community_report'
  | 'other';
export type ReportStatus = 'open' | 'investigating' | 'resolved' | 'dismissed';
export type RequestStatus = 'open' | 'planned' | 'fulfilled' | 'declined';
export type ContributionKind = 'new_product' | 'product_edit' | 'new_rule' | 'evidence' | 'guide';
export type ContributionStatus = 'pending' | 'needs_changes' | 'approved' | 'rejected' | 'withdrawn';
export type CommentStatus = 'visible' | 'hidden' | 'deleted';
export type GuideStatus = 'draft' | 'pending_review' | 'published' | 'archived';
export type MediaStatus = 'pending' | 'approved' | 'rejected';

export interface Me {
  id: string;
  username: string;
  display_name: string;
  bio: string | null;
  avatar_url: string | null;
  role: Role;
  account_status: AccountStatus;
  locale: string;
  created_at: string;
  deletion_requested_at: string | null;
  reputation_points: number;
  reputation_tier: string;
  unread_notifications: number;
  favorite_count: number;
  capabilities: {
    can_moderate: boolean;
    can_admin: boolean;
    can_contribute: boolean;
  };
}

export interface Paged<T> {
  items: T[];
  total: number;
  limit: number;
  offset: number;
}

export interface Brand {
  id: string;
  slug: string;
  name: string;
}

export interface Category {
  id: string;
  slug: string;
  name: string;
  is_public: boolean;
}

export interface Product {
  id: string;
  slug: string;
  name: string;
  brand_id: string;
  category_id: string;
  model_number: string | null;
  release_year: number | null;
  summary: string | null;
  technical_notes: string | null;
  connector_type: string | null;
  form_factor: string | null;
  status: ProductStatus;
  created_by: string | null;
  created_at: string;
  updated_at: string;
  published_at: string | null;
}

export interface ProductListItem extends Product {
  brand_name: string;
  category_name: string;
}

export interface ProductSpec {
  id: string;
  product_id: string;
  label: string;
  value: string;
  unit: string | null;
  is_critical: boolean;
  sort_order: number;
}

export interface ProductAlias {
  id: string;
  product_id: string;
  alias: string;
}

export interface ProductDetail extends Product {
  aliases: ProductAlias[];
  specs: ProductSpec[];
}

export interface Rule {
  id: string;
  subject_product_id: string;
  object_product_id: string;
  relationship: Relationship;
  verification: Verification;
  confidence: number;
  risk: Risk;
  summary: string;
  technical_details: string | null;
  required_modifications: string | null;
  status: RuleStatus;
  created_by: string | null;
  reviewed_by: string | null;
  published_at: string | null;
  created_at: string;
  updated_at: string;
}

export interface RuleEvidence {
  id: string;
  rule_id: string;
  evidence_type: EvidenceType;
  title: string;
  source_url: string;
  publisher: string | null;
  excerpt: string | null;
  captured_at: string | null;
  archived_path: string | null;
  added_by: string | null;
  created_at: string;
}

export interface RuleWithMeta extends Rule {
  subject_name: string;
  object_name: string;
  evidence_count: number;
}

export interface RuleVersion {
  id: string;
  version: number;
  snapshot: Record<string, unknown>;
  change_reason: string | null;
  created_at: string;
  changed_by: string | null;
}

export interface DashboardCounts {
  users_active: number;
  users_banned: number;
  users_suspended: number;
  products_published: number;
  products_draft: number;
  rules_published: number;
  rules_draft: number;
  contributions_pending: number;
  reports_open: number;
  comments_hidden: number;
  requests_open: number;
  notifications_unread: number;
}

export interface Dashboard {
  counts: DashboardCounts;
  recent_audit: Array<{
    id: string;
    action: string;
    entity_type: string;
    entity_id: string | null;
    created_at: string;
    actor: string | null;
  }>;
  pending_contributions: Array<{
    id: string;
    kind: ContributionKind;
    status: string;
    created_at: string;
    payload: Record<string, unknown>;
    contributor_name: string | null;
  }>;
  open_reports: Array<{
    id: string;
    target_type: string;
    target_id: string;
    reason: string;
    status: string;
    created_at: string;
    reporter_name: string | null;
  }>;
}

export interface Report {
  id: string;
  target_type: string;
  target_id: string;
  reason: string;
  details: string | null;
  status: ReportStatus;
  resolution_note: string | null;
  created_at: string;
  updated_at: string;
  reporter_name: string | null;
  handler_name: string | null;
}

export interface Contribution {
  id: string;
  contributor_id: string;
  kind: ContributionKind;
  payload: Record<string, unknown>;
  status: ContributionStatus;
  review_note: string | null;
  applied_entity_id: string | null;
  created_at: string;
  reviewed_at: string | null;
  contributor_name: string | null;
  reviewer_name: string | null;
}

export interface UserRow {
  id: string;
  username: string;
  display_name: string;
  email: string;
  role: Role;
  account_status: AccountStatus;
  created_at: string;
  reputation_points: number;
  approved_contributions: number;
}

export interface RequestRow {
  id: string;
  title: string;
  description: string;
  category_id: string | null;
  status: RequestStatus;
  created_by: string;
  is_anonymous: boolean;
  fulfilled_product_id: string | null;
  created_at: string;
  updated_at: string;
  vote_count: number;
  category_name: string | null;
}

export interface CommentRow {
  id: string;
  target_type: string;
  target_id: string;
  parent_id: string | null;
  user_id: string;
  body: string;
  status: CommentStatus;
  created_at: string;
  updated_at: string;
  author_name: string | null;
}

export interface Guide {
  id: string;
  slug: string;
  title: string;
  summary: string;
  body_md: string;
  difficulty: string;
  status: GuideStatus;
  product_id: string | null;
  created_by: string | null;
  published_at: string | null;
  created_at: string;
  updated_at: string;
}

export interface GuideStep {
  id: string;
  guide_id: string;
  position: number;
  title: string;
  body: string;
  image_path: string | null;
}

export interface GuideDetail extends Guide {
  steps: GuideStep[];
  product_name: string | null;
}

export interface ProductMedia {
  id: string;
  product_id: string;
  storage_path: string;
  alt_text: string | null;
  status: MediaStatus;
  sort_order: number;
  created_by: string | null;
  created_at: string;
  product_name: string | null;
}

export interface MediaAsset {
  id: string;
  bucket: string;
  storage_path: string;
  owner_id: string | null;
  alt_text: string | null;
  status: MediaStatus;
  target_type: string | null;
  target_id: string | null;
  created_at: string;
}

export interface AuditRow {
  id: string;
  actor_id: string | null;
  action: string;
  entity_type: string;
  entity_id: string | null;
  before_data: unknown;
  after_data: unknown;
  context: unknown;
  created_at: string;
  actor_name: string | null;
}

export interface Analytics {
  window_days: number;
  totals: {
    events: number;
    product_views: number;
    searches: number;
    checker_runs: number;
    unique_sessions: number;
  };
  daily: Array<{ date: string; views: number; searches: number; checker_runs: number }>;
  top_products: Array<{ entity_id: string; name: string | null; views: number }>;
  top_searches: Array<{ term: string; count: number }>;
}
