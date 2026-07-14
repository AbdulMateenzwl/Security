import { DatePipe } from '@angular/common';
import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { Component, OnDestroy, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { ChatService } from '../../../core/services/chat.service';
import { RealtimeService } from '../../../core/services/realtime.service';
import { TaskService } from '../../../core/services/task.service';
import { Chat, ChatMember } from '../../../core/models/chat.models';
import {
  TASK_STATUSES,
  TASK_STATUS_LABELS,
  Task,
  TaskBoard as TaskBoardModel,
  TaskStatus,
} from '../../../core/models/task.models';
import { extractErrorMessage } from '../../../core/util/api-error';
import { chatDisplayName } from '../../chats/chat-display';
import { TaskDialog } from '../task-dialog/task-dialog';

const emptyBoard = (): TaskBoardModel => ({ TODO: [], IN_PROGRESS: [], IN_REVIEW: [], DONE: [] });

@Component({
  selector: 'app-task-board',
  imports: [DragDropModule, RouterLink, DatePipe, TaskDialog],
  templateUrl: './task-board.html',
  styleUrl: './task-board.scss',
})
export class TaskBoard implements OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly taskService = inject(TaskService);
  private readonly chatService = inject(ChatService);
  private readonly realtime = inject(RealtimeService);
  private readonly auth = inject(AuthService);

  readonly statuses = TASK_STATUSES;
  readonly statusLabels = TASK_STATUS_LABELS;

  readonly chat = signal<Chat | null>(null);
  readonly board = signal<TaskBoardModel>(emptyBoard());
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly showCreate = signal(false);
  readonly editing = signal<Task | null>(null);

  private chatId = '';
  private memberNames = new Map<string, string>();
  private liveUnsub?: () => void;

  constructor() {
    this.route.paramMap.subscribe((p) => {
      const id = p.get('id');
      if (id) {
        this.chatId = id;
        this.load(id);
      }
    });
  }

  ngOnDestroy(): void {
    this.liveUnsub?.();
  }

  title(): string {
    const chat = this.chat();
    return chat ? chatDisplayName(chat, this.auth.user()?.id) : 'Tasks';
  }

  get members(): ChatMember[] {
    return this.chat()?.members ?? [];
  }

  tasksFor(status: TaskStatus): Task[] {
    return this.board()[status];
  }

  memberName(userId: string | null): string {
    if (!userId) return 'Unassigned';
    return this.memberNames.get(userId) ?? 'Unknown';
  }

  isOverdue(task: Task): boolean {
    return task.status !== 'DONE' && task.dueDate != null && new Date(task.dueDate) < new Date();
  }

  private async load(chatId: string): Promise<void> {
    this.teardownLive();
    this.loading.set(true);
    this.error.set(null);
    try {
      const chat = await firstValueFrom(this.chatService.get(chatId));
      this.chat.set(chat);
      this.memberNames = new Map(chat.members.map((m) => [m.userId, m.username]));
      const board = await firstValueFrom(this.taskService.board(chatId));
      this.board.set(this.normalize(board));
      this.loading.set(false);
      // Live-refresh when any member changes a task.
      this.realtime.connect();
      this.liveUnsub = this.realtime.subscribeToTasks(chatId, () => void this.refresh());
    } catch (err) {
      this.error.set(extractErrorMessage(err, 'Could not load the task board.'));
      this.loading.set(false);
    }
  }

  /** Backend may omit empty status keys — ensure every column exists. */
  private normalize(board: TaskBoardModel): TaskBoardModel {
    const out = emptyBoard();
    for (const s of TASK_STATUSES) out[s] = board[s] ?? [];
    return out;
  }

  async refresh(): Promise<void> {
    try {
      const board = await firstValueFrom(this.taskService.board(this.chatId));
      this.board.set(this.normalize(board));
    } catch {
      /* transient — keep the current board */
    }
  }

  private cloneBoard(): TaskBoardModel {
    const current = this.board();
    const out = emptyBoard();
    for (const s of TASK_STATUSES) out[s] = [...current[s]];
    return out;
  }

  /** A card was dropped into a column: persist the status change (reordering within a column is
   *  cosmetic — the backend has no order field — so we ignore same-column drops). */
  async drop(event: CdkDragDrop<Task[]>, target: TaskStatus): Promise<void> {
    if (event.previousContainer === event.container) return;
    const task = event.item.data as Task;
    if (task.status === target) return;

    const next = this.cloneBoard();
    next[task.status] = next[task.status].filter((t) => t.id !== task.id);
    next[target] = [{ ...task, status: target }, ...next[target]];
    this.board.set(next);

    try {
      await firstValueFrom(this.taskService.update(task.id, { status: target }));
      this.realtime.sendTaskUpdate(this.chatId, task.id, 'STATUS_CHANGED');
    } catch (err) {
      this.error.set(extractErrorMessage(err, 'Could not move the task.'));
      await this.refresh();
    }
  }

  onCreated(task: Task): void {
    this.showCreate.set(false);
    const next = this.cloneBoard();
    next[task.status] = [task, ...next[task.status]];
    this.board.set(next);
    this.realtime.sendTaskUpdate(this.chatId, task.id, 'CREATED');
  }

  onUpdated(task: Task): void {
    this.editing.set(null);
    const next = this.cloneBoard();
    // Remove the old copy from wherever it was, then insert the updated one under its status.
    for (const s of TASK_STATUSES) next[s] = next[s].filter((t) => t.id !== task.id);
    next[task.status] = [task, ...next[task.status]];
    this.board.set(next);
    this.realtime.sendTaskUpdate(this.chatId, task.id, 'STATUS_CHANGED');
  }

  onDeleted(taskId: string): void {
    this.editing.set(null);
    const next = this.cloneBoard();
    for (const s of TASK_STATUSES) next[s] = next[s].filter((t) => t.id !== taskId);
    this.board.set(next);
    this.realtime.sendTaskUpdate(this.chatId, taskId, 'DELETED');
  }

  private teardownLive(): void {
    this.liveUnsub?.();
    this.liveUnsub = undefined;
  }
}
