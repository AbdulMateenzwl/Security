/** Task/collaboration models — mirror the backend task domain. Tasks are plaintext (not encrypted). */

export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'DONE';
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

export type TaskAction =
  | 'CREATED'
  | 'STATUS_CHANGED'
  | 'PRIORITY_CHANGED'
  | 'ASSIGNED'
  | 'UNASSIGNED'
  | 'DUE_DATE_CHANGED'
  | 'TITLE_CHANGED'
  | 'DESCRIPTION_CHANGED'
  | 'DELETED';

/** The status columns, in board order. */
export const TASK_STATUSES: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE'];
export const TASK_PRIORITIES: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

export const TASK_STATUS_LABELS: Record<TaskStatus, string> = {
  TODO: 'To do',
  IN_PROGRESS: 'In progress',
  IN_REVIEW: 'In review',
  DONE: 'Done',
};

/** A task as returned by the backend. */
export interface Task {
  id: string;
  chatId: string;
  title: string;
  description: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  assignedTo: string | null;
  createdBy: string;
  dueDate: string | null;
  labels: string[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateTaskRequest {
  title: string;
  description?: string | null;
  priority?: TaskPriority | null;
  assignedToId?: string | null;
  dueDate?: string | null;
  labels?: string[] | null;
}

/** Partial update — every field optional; omitted/null means "leave unchanged". */
export interface UpdateTaskRequest {
  title?: string | null;
  description?: string | null;
  status?: TaskStatus | null;
  priority?: TaskPriority | null;
  assignedToId?: string | null;
  dueDate?: string | null;
  labels?: string[] | null;
}

/** One entry in a task's activity log. */
export interface TaskActivity {
  id: string;
  action: TaskAction;
  performedBy: string;
  performedByUsername: string;
  oldValue: string | null;
  newValue: string | null;
  createdAt: string;
}

/** The board endpoint returns tasks grouped by status. */
export type TaskBoard = Record<TaskStatus, Task[]>;
