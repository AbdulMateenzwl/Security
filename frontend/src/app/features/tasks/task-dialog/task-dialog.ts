import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ChatMember } from '../../../core/models/chat.models';
import {
  TASK_PRIORITIES,
  TASK_STATUSES,
  TASK_STATUS_LABELS,
  Task,
  TaskPriority,
  TaskStatus,
  UpdateTaskRequest,
} from '../../../core/models/task.models';
import { TaskService } from '../../../core/services/task.service';
import { extractErrorMessage } from '../../../core/util/api-error';

/** Create or edit a task. If [task] is provided it's edit mode (with status + delete); else create. */
@Component({
  selector: 'app-task-dialog',
  imports: [ReactiveFormsModule],
  templateUrl: './task-dialog.html',
  styleUrl: './task-dialog.scss',
})
export class TaskDialog implements OnInit {
  private readonly taskService = inject(TaskService);

  readonly chatId = input.required<string>();
  readonly members = input<ChatMember[]>([]);
  readonly task = input<Task | null>(null);

  readonly created = output<Task>();
  readonly updated = output<Task>();
  readonly deleted = output<string>();
  readonly closed = output<void>();

  readonly priorities = TASK_PRIORITIES;
  readonly statuses = TASK_STATUSES;
  readonly statusLabels = TASK_STATUS_LABELS;

  readonly title = new FormControl('', { nonNullable: true });
  readonly description = new FormControl('', { nonNullable: true });
  readonly status = new FormControl<TaskStatus>('TODO', { nonNullable: true });
  readonly priority = new FormControl<TaskPriority>('MEDIUM', { nonNullable: true });
  readonly assignedToId = new FormControl('', { nonNullable: true });
  readonly dueDate = new FormControl('', { nonNullable: true }); // yyyy-mm-dd
  readonly labels = new FormControl('', { nonNullable: true }); // comma-separated

  readonly submitting = signal(false);
  readonly deleting = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const t = this.task();
    if (t) {
      this.title.setValue(t.title);
      this.description.setValue(t.description ?? '');
      this.status.setValue(t.status);
      this.priority.setValue(t.priority);
      this.assignedToId.setValue(t.assignedTo ?? '');
      this.dueDate.setValue(t.dueDate ? t.dueDate.slice(0, 10) : '');
      this.labels.setValue(t.labels.join(', '));
    }
  }

  get isEdit(): boolean {
    return this.task() !== null;
  }

  onSubmit(event: Event): void {
    event.preventDefault();
    this.submit();
  }

  private buildPayload(): UpdateTaskRequest {
    const dd = this.dueDate.value.trim();
    return {
      title: this.title.value.trim(),
      description: this.description.value.trim() || null,
      priority: this.priority.value,
      assignedToId: this.assignedToId.value || null,
      dueDate: dd ? new Date(dd).toISOString() : null,
      labels: this.labels.value
        .split(',')
        .map((l) => l.trim())
        .filter((l) => l.length > 0),
    };
  }

  submit(): void {
    if (this.submitting() || !this.title.value.trim()) return;
    this.submitting.set(true);
    this.error.set(null);
    const payload = this.buildPayload();
    const task = this.task();

    if (task) {
      this.taskService.update(task.id, { ...payload, status: this.status.value }).subscribe({
        next: (t) => this.updated.emit(t),
        error: (err) => {
          this.error.set(extractErrorMessage(err, 'Could not save the task.'));
          this.submitting.set(false);
        },
      });
    } else {
      this.taskService
        .create(this.chatId(), {
          title: payload.title!,
          description: payload.description,
          priority: payload.priority,
          assignedToId: payload.assignedToId,
          dueDate: payload.dueDate,
          labels: payload.labels,
        })
        .subscribe({
          next: (t) => this.created.emit(t),
          error: (err) => {
            this.error.set(extractErrorMessage(err, 'Could not create the task.'));
            this.submitting.set(false);
          },
        });
    }
  }

  remove(): void {
    const task = this.task();
    if (!task || this.deleting()) return;
    this.deleting.set(true);
    this.error.set(null);
    this.taskService.delete(task.id).subscribe({
      next: () => this.deleted.emit(task.id),
      error: (err) => {
        this.error.set(extractErrorMessage(err, 'Could not delete the task.'));
        this.deleting.set(false);
      },
    });
  }

  close(): void {
    this.closed.emit();
  }
}
