import { Link } from 'react-router-dom';
import { PageHeader } from '../components/PageParts';

export function NotFoundPage() {
  return (
    <>
      <PageHeader title="Page not found" description="That admin route does not exist." />
      <Link to="/" className="button primary">
        Return to dashboard
      </Link>
    </>
  );
}
