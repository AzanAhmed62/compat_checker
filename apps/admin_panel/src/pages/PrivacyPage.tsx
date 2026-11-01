import { useEffect } from 'react';
import { Link } from 'react-router-dom';

const OPERATOR = import.meta.env.VITE_DATA_CONTROLLER?.trim();
const SUPPORT_EMAIL = import.meta.env.VITE_SUPPORT_EMAIL?.trim();

export function PrivacyPage() {
  useEffect(() => {
    document.title = 'Privacy policy - Compat Checker';
  }, []);

  return (
    <main className="public-page">
      <article className="public-document">
        <p className="eyebrow">Compat Checker</p>
        <h1>Privacy policy</h1>
        <p className="muted">Last updated: 24 October 2026</p>

        <p>
          This policy describes the privacy practices of the Compat Checker application and the
          web surfaces operated with it. The deployment operator identified at the end of this page
          is the data controller for this deployment.
        </p>

        <h2>Information we process</h2>
        <ul>
          <li>
            <strong>Account information.</strong> Email address, authentication metadata, username,
            display name, optional biography and avatar, locale, and account timestamps.
          </li>
          <li>
            <strong>Activity and content.</strong> Favorites, votes, notifications, comments,
            requests, reports, contributions, guide edits, and the content you submit. Votes are
            stored privately; the public API exposes only aggregate totals.
          </li>
          <li>
            <strong>Technical and security data.</strong> The hosting and authentication providers
            may process IP address, user agent, timestamps, request identifiers, and error or
            security logs to operate and protect the service.
          </li>
        </ul>

        <h2>Why we process it</h2>
        <ul>
          <li>To create and secure accounts and keep you signed in.</li>
          <li>To provide catalog browsing, compatibility checks, guides, favorites, and requests.</li>
          <li>
            To process contributions, moderation, reports, and reputation, and to keep public data
            accurate and safe.
          </li>
          <li>To send account and moderation notifications and to respond to support requests.</li>
          <li>To detect abuse, enforce rate limits, and maintain service reliability.</li>
        </ul>

        <h2>Public information</h2>
        <p>
          Published catalog data, compatibility rules, evidence references, guides, and public
          aggregate counts are available without an account. Public projections use explicit
          columns and do not expose individual vote rows, account email addresses, or moderation
          notes. Content you publish as a contributor, such as a public display name or guide
          attribution, may be visible to others.
        </p>

        <h2>Service providers and disclosure</h2>
        <p>
          The service is built on Supabase for authentication, database, storage, and server
          functions, and uses hosting infrastructure for the public web surfaces. These providers
          process data on the operator&apos;s behalf. We do not sell personal information, and this
          deployment does not include third-party advertising or analytics SDKs. We may disclose
          information when required by law or when necessary to protect users, the service, or
          others.
        </p>

        <h2>Retention and account deletion</h2>
        <p>
          Account data is retained while your account is active. When you request deletion, your
          profile is hidden and the account is scheduled for purging after 30 days. You can cancel
          a pending deletion by signing in to the deletion page and selecting cancel. After the
          purge, the authentication record, profile, and associated user-owned records are removed
          according to the database&apos;s cascading rules. Some published catalog or guide records
          may remain with creator references removed. Limited security, moderation, or audit records
          may be retained where necessary for safety, abuse prevention, or legal obligations.
        </p>
        <p>
          <Link to="/delete-account">Request or cancel account deletion</Link>
        </p>

        <h2>Security</h2>
        <p>
          Access is enforced at the database boundary with row-level security and server-side
          authorization. Privileged service credentials are kept out of client applications. No
          method of transmission or storage is completely secure, so we cannot promise absolute
          security.
        </p>

        <h2>Your choices and rights</h2>
        <p>
          You can review and update profile information in the app, manage favorites and
          notifications, and request account deletion from the app or this site. Depending on your
          location, you may also have rights to access, correct, export, restrict, or object to
          processing. Contact the operator to make a request. You may also complain to your local
          data-protection authority.
        </p>

        <h2>Children</h2>
        <p>
          Compat Checker is not directed to children under 13, and we do not knowingly collect
          personal information from them. If you believe a child created an account, contact the
          operator so it can be removed.
        </p>

        <h2>Changes</h2>
        <p>
          We may update this policy when the service or legal requirements change. The date at the
          top of this page identifies the current version. Material changes will be communicated
          through the service where appropriate.
        </p>

        <h2>Contact</h2>
        <p>
          {OPERATOR
            ? `Data controller: ${OPERATOR}.`
            : 'Data controller: not configured for this deployment.'}
        </p>
        {SUPPORT_EMAIL ? (
          <p>
            Privacy and deletion questions: <a href={`mailto:${SUPPORT_EMAIL}`}>{SUPPORT_EMAIL}</a>
          </p>
        ) : (
          <p className="notice warning" role="note">
            A support contact has not been configured for this deployment. The deployment operator
            must set <code>VITE_SUPPORT_EMAIL</code> before publishing this policy.
          </p>
        )}
        <p>
          <Link to="/delete-account">Account deletion</Link>
          {' · '}
          <Link to="/login">Administration sign in</Link>
        </p>
      </article>
    </main>
  );
}
