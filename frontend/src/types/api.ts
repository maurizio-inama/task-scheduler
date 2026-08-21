export type Role = 'ADMIN' | 'REVIEWER' | 'OPERATOR';

export type TaskStatus =
  | 'PENDING'
  | 'SCHEDULED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED';

export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type ScheduleStatus = 'DRAFT' | 'PUBLISHED' | 'COMPLETED' | 'CANCELLED';

export type AssignmentStatus =
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED';

export interface ApiErrorBody {
  status: number;
  error: string;
  message: string;
  path: string;
  timestamp: string;
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresIn: number;
  username: string;
  role: Role;
}

export interface MeResponse {
  id: number;
  username: string;
  role: Role;
}

export interface User {
  id: number;
  username: string;
  firstName: string;
  lastName: string;
  email: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UserInput {
  username: string;
  password: string;
  firstName: string;
  lastName: string;
  email: string;
  role: Role;
  enabled: boolean;
}

export interface Task {
  id: number;
  title: string;
  description: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  estimatedDurationMinutes: number;
  deadline: string | null;
  createdAt: string;
}

export interface TaskInput {
  title: string;
  description: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  estimatedDurationMinutes: number;
  deadline: string | null;
}

export interface Availability {
  id: number;
  userId: number;
  startDateTime: string;
  endDateTime: string;
}

export interface AvailabilityInput {
  userId: number;
  startDateTime: string;
  endDateTime: string;
}

export interface Unavailability {
  id: number;
  userId: number;
  startDateTime: string;
  endDateTime: string;
  reason: string | null;
}

export interface UnavailabilityInput {
  userId: number;
  startDateTime: string;
  endDateTime: string;
  reason: string | null;
}

export interface Schedule {
  id: number;
  startDateTime: string;
  endDateTime: string;
  createdAt: string;
}

export interface ScheduleInput {
  startDateTime: string;
  endDateTime: string;
  status: ScheduleStatus;
}

export interface Assignment {
  id: number;
  userId: number;
  taskId: number;
  scheduleId: number;
  startDateTime: string;
  endDateTime: string;
  status: AssignmentStatus;
}

export interface AssignmentInput {
  userId: number;
  taskId: number;
  scheduleId: number;
  startDateTime: string;
  endDateTime: string;
  status: AssignmentStatus;
}

export interface UnscheduledTaskInfo {
  taskId: number;
  reason: string;
  detail: string | null;
}

export interface GenerateResponse {
  scheduleId: number;
  scheduledTaskCount: number;
  createdAssignmentCount: number;
  assignments: Assignment[];
  unscheduledTasks: UnscheduledTaskInfo[];
}
