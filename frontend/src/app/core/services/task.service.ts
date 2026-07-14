import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CreateTaskRequest,
  Task,
  TaskActivity,
  TaskBoard,
  TaskStatus,
  UpdateTaskRequest,
} from '../models/task.models';

/** Talks to the task endpoints. Tasks live under a chat; individual tasks are addressed by id. */
@Injectable({ providedIn: 'root' })
export class TaskService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiUrl;

  /** Kanban board for a chat: tasks grouped by status. */
  board(chatId: string): Observable<TaskBoard> {
    return this.http.get<TaskBoard>(`${this.baseUrl}/chats/${chatId}/tasks/board`);
  }

  /** Flat, optionally-filtered task list for a chat. */
  list(chatId: string, status?: TaskStatus, assignee?: string): Observable<Task[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    if (assignee) params = params.set('assignee', assignee);
    return this.http.get<Task[]>(`${this.baseUrl}/chats/${chatId}/tasks`, { params });
  }

  create(chatId: string, request: CreateTaskRequest): Observable<Task> {
    return this.http.post<Task>(`${this.baseUrl}/chats/${chatId}/tasks`, request);
  }

  get(taskId: string): Observable<Task> {
    return this.http.get<Task>(`${this.baseUrl}/tasks/${taskId}`);
  }

  update(taskId: string, request: UpdateTaskRequest): Observable<Task> {
    return this.http.put<Task>(`${this.baseUrl}/tasks/${taskId}`, request);
  }

  delete(taskId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/tasks/${taskId}`);
  }

  activity(taskId: string): Observable<TaskActivity[]> {
    return this.http.get<TaskActivity[]>(`${this.baseUrl}/tasks/${taskId}/activity`);
  }
}
