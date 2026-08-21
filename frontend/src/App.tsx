import { Navigate, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { ProtectedRoute } from './components/ProtectedRoute';
import { AssignmentsPage } from './pages/AssignmentsPage';
import { AvailabilityPage } from './pages/AvailabilityPage';
import { DashboardPage } from './pages/DashboardPage';
import { LoginPage } from './pages/LoginPage';
import { SchedulesPage } from './pages/SchedulesPage';
import { TasksPage } from './pages/TasksPage';
import { UnavailabilityPage } from './pages/UnavailabilityPage';
import { UsersPage } from './pages/UsersPage';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <ProtectedRoute>
            <Layout />
          </ProtectedRoute>
        }
      >
        <Route index element={<DashboardPage />} />
        <Route path="tasks" element={<TasksPage />} />
        <Route path="availability" element={<AvailabilityPage />} />
        <Route path="unavailability" element={<UnavailabilityPage />} />
        <Route path="schedules" element={<SchedulesPage />} />
        <Route path="assignments" element={<AssignmentsPage />} />
        <Route
          path="users"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <UsersPage />
            </ProtectedRoute>
          }
        />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
