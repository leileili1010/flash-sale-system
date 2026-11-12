import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router';
import { ApiError } from '../api/client';
import { Button } from '../components/Button';
import { useAuth } from '../features/auth/AuthProvider';
import { safeNext } from '../features/auth/RequireAuth';

type Mode = 'signin' | 'register';

const DEMO = { email: 'shopper@fluffle.dev', password: 'shopper-password' };

export function LoginPage() {
  const { login, register, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const next = safeNext(params.get('next'));

  const [mode, setMode] = useState<Mode>('signin');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState<ApiError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (isAuthenticated && !submitting) {
    return <Navigate to={next} replace />;
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      if (mode === 'signin') await login(email, password);
      else await register(email, password, displayName);
      void navigate(next, { replace: true });
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError('INTERNAL_ERROR', 0, 'Something went wrong. Please try again.'));
      setSubmitting(false);
    }
  }

  const fieldError = (field: string) => error?.fieldErrors[field];
  const input = 'mt-1.5 w-full rounded-2xl border border-line bg-white px-4 py-3 text-base outline-none focus:border-pink-400 focus:ring-3 focus:ring-pink-50';

  return (
    <section className="mx-auto mt-6 max-w-md rounded-[30px] bg-white p-8 shadow-card">
      <div role="tablist" aria-label="Account" className="mb-6 grid grid-cols-2 rounded-full bg-track p-1">
        {(['signin', 'register'] as const).map((m) => (
          <button key={m} role="tab" type="button" aria-selected={mode === m}
                  onClick={() => { setMode(m); setError(null); }}
                  className={`rounded-full py-2.5 text-sm font-bold transition ${mode === m ? 'bg-white text-ink shadow-card' : 'text-muted'}`}>
            {m === 'signin' ? 'Sign in' : 'Create account'}
          </button>
        ))}
      </div>

      <h1 className="font-display text-3xl font-bold">{mode === 'signin' ? 'Welcome back' : 'Join the drop'}</h1>
      <p className="mt-1 text-muted">{mode === 'signin' ? 'Sign in to grab your next figure.' : 'One account, one figure per drop.'}</p>

      <form onSubmit={submit} className="mt-6 flex flex-col gap-4" noValidate>
        {mode === 'register' && (
          <label className="text-sm font-semibold">
            Display name
            <input className={input} value={displayName} onChange={(e) => setDisplayName(e.target.value)} autoComplete="nickname" required />
            {fieldError('displayName') && <span className="mt-1 block text-sm text-pink-600">{fieldError('displayName')}</span>}
          </label>
        )}
        <label className="text-sm font-semibold">
          Email
          <input className={input} type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
          {fieldError('email') && <span className="mt-1 block text-sm text-pink-600">{fieldError('email')}</span>}
        </label>
        <label className="text-sm font-semibold">
          Password
          <input className={input} type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                 autoComplete={mode === 'signin' ? 'current-password' : 'new-password'} required />
          {fieldError('password') && <span className="mt-1 block text-sm text-pink-600">{fieldError('password')}</span>}
        </label>

        {error && Object.keys(error.fieldErrors).length === 0 && (
          <p role="alert" className="rounded-2xl bg-pink-50 px-4 py-3 text-sm font-medium text-[#A02656]">{error.message}</p>
        )}

        <Button type="submit" size="lg" disabled={submitting} className="mt-2 w-full">
          {submitting ? 'One moment…' : mode === 'signin' ? 'Sign in' : 'Create account'}
        </Button>
      </form>

      {mode === 'signin' && (
        <p className="mt-6 rounded-2xl bg-chip px-4 py-3 text-sm text-chip-ink">
          Trying it out?{' '}
          <button type="button" className="font-bold underline underline-offset-4"
                  onClick={() => { setEmail(DEMO.email); setPassword(DEMO.password); }}>
            Use the demo shopper account
          </button>
        </p>
      )}
    </section>
  );
}
