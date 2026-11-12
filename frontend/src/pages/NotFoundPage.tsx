import { ButtonLink } from '../components/Button';

export function NotFoundPage() {
  return (
    <section className="rounded-3xl bg-white p-10 text-center shadow-card">
      <h1 className="font-display text-3xl font-bold">This page wandered off</h1>
      <p className="mt-2 text-muted">We couldn't find what you were looking for.</p>
      <ButtonLink to="/" className="mt-6">Back to drops</ButtonLink>
    </section>
  );
}
