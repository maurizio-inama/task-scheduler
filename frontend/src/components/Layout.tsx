import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import type { Role } from '../types/api';

interface NavItem {
  to: string;
  label: string;
  end?: boolean;
  roles?: Role[];
}

const NAV_ITEMS: NavItem[] = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/tasks', label: 'Tasks' },
  { to: '/availability', label: 'Availability' },
  { to: '/unavailability', label: 'Unavailability' },
  { to: '/schedules', label: 'Schedules' },
  { to: '/assignments', label: 'Assignments' },
  { to: '/users', label: 'Users', roles: ['ADMIN'] },
];

export function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  if (!user) {
    return null;
  }

  const items = NAV_ITEMS.filter(
    (item) => !item.roles || item.roles.includes(user.role),
  );

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="app-brand">Task Scheduler</div>
        <nav aria-label="Main navigation" className="app-nav">
          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                isActive ? 'nav-link nav-link-active' : 'nav-link'
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="app-user">
          <span className="app-user-name">{user.username}</span>
          <span className={`badge role-${user.role.toLowerCase()}`}>
            {user.role}
          </span>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={handleLogout}
          >
            Logout
          </button>
        </div>
      </header>
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  );
}
