import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { hasAnyRole, useAuth } from '../context/AuthContext';
import { Loading } from './Loading';
import type { Role } from '../types/api';

interface ProtectedRouteProps {
  children: ReactNode;
  roles?: Role[];
}

export function ProtectedRoute({ children, roles }: ProtectedRouteProps) {
  const { user, initializing } = useAuth();
  const location = useLocation();

  if (initializing) {
    return <Loading label="Restoring your session…" />;
  }

  if (!user) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }

  if (roles && !hasAnyRole(user, roles)) {
    return (
      <div className="page">
        <h1>Not authorized</h1>
        <p className="empty-state">
          Your role ({user.role}) does not grant access to this section.
        </p>
      </div>
    );
  }

  return <>{children}</>;
}
