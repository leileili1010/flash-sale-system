import { Link, NavLink } from 'react-router';
import logo from '../assets/logo.svg';
import { formatClock } from '../lib/format';
import { useServerNow } from '../lib/serverClock';
import { ClockIcon } from './icons';

export interface HeaderUser {
  displayName: string;
  isAdmin: boolean;
}

interface HeaderProps {
  user: HeaderUser | null;
  onSignOut?: () => void;
}

const navLink = ({ isActive }: { isActive: boolean }) =>
  `rounded-full px-1 py-1 text-[15px] font-medium hover:text-pink-600 ${isActive ? 'text-pink-600' : 'text-ink'}`;

export function Header({ user, onSignOut }: HeaderProps) {
  const now = useServerNow();
  const initials = user?.displayName.split(/\s+/).map((part) => part[0]).join('').slice(0, 2).toUpperCase();

  return (
    <header className="mx-auto flex w-full max-w-[1240px] flex-wrap items-center justify-between gap-4 px-6 py-3.5">
      <div className="flex flex-wrap items-center gap-10">
        <Link to="/" className="flex items-center gap-2 text-ink">
          <img src={logo} alt="" className="size-10" />
          <span className="font-display text-[27px] font-bold tracking-tight">fluffle</span>
        </Link>
        <nav aria-label="Main" className="flex gap-6">
          <NavLink to="/drops" className={navLink}>Drops</NavLink>
          <NavLink to="/orders" className={navLink}>My Orders</NavLink>
          {user?.isAdmin && <NavLink to="/admin" className={navLink}>Admin</NavLink>}
        </nav>
      </div>
      <div className="flex flex-wrap items-center gap-3.5">
        <span className="inline-flex items-center gap-2 rounded-full border border-line bg-white px-3.5 py-2 text-sm text-muted tabular">
          <ClockIcon size={15} />
          Server time {formatClock(now)}
        </span>
        {user ? (
          <div className="flex items-center gap-3">
            <span className="inline-flex items-center gap-2.5 text-sm font-semibold">
              <span aria-hidden="true"
                    className="inline-flex size-9 items-center justify-center rounded-full bg-gradient-to-br from-pink-300 to-[#8FB4FF] font-bold text-navy-950">
                {initials}
              </span>
              {user.displayName}
            </span>
            <button type="button" onClick={onSignOut} className="text-sm font-semibold text-muted underline-offset-4 hover:text-pink-600 hover:underline">
              Sign out
            </button>
          </div>
        ) : (
          <Link to="/login" className="rounded-full bg-ink px-5 py-2.5 text-[15px] font-bold text-white hover:bg-navy-900">
            Sign in
          </Link>
        )}
      </div>
    </header>
  );
}
