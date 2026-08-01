import { Component, inject, output, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { UserSummary } from '../../../core/models/auth.models';
import { Chat } from '../../../core/models/chat.models';
import { ChatService } from '../../../core/services/chat.service';
import { UserService } from '../../../core/services/user.service';
import { extractErrorMessage } from '../../../core/util/api-error';

@Component({
  selector: 'app-create-chat-dialog',
  imports: [ReactiveFormsModule],
  templateUrl: './create-chat-dialog.html',
  styleUrl: './create-chat-dialog.scss',
})
export class CreateChatDialog {
  private readonly chatService = inject(ChatService);
  private readonly userService = inject(UserService);

  /** Emitted with the new chat on success. */
  readonly created = output<Chat>();
  /** Emitted when the user dismisses the dialog. */
  readonly closed = output<void>();

  readonly searchControl = new FormControl('', { nonNullable: true });

  readonly results = signal<UserSummary[]>([]);
  readonly selected = signal<UserSummary[]>([]);
  readonly searching = signal(false);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    this.searchControl.valueChanges
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((q) => {
          const query = q.trim();
          if (query.length < 1) {
            this.searching.set(false);
            return [[] as UserSummary[]];
          }
          this.searching.set(true);
          return this.userService.search(query);
        }),
      )
      .subscribe({
        next: (users) => {
          // Hide already-selected users from the results.
          const selectedIds = new Set(this.selected().map((u) => u.id));
          this.results.set(users.filter((u) => !selectedIds.has(u.id)));
          this.searching.set(false);
        },
        error: () => this.searching.set(false),
      });
  }

  select(user: UserSummary): void {
    // Direct chats have exactly one other participant.
    this.selected.set([user]);
    this.results.update((list) => list.filter((u) => u.id !== user.id));
    this.searchControl.setValue('');
  }

  remove(user: UserSummary): void {
    this.selected.update((list) => list.filter((u) => u.id !== user.id));
  }

  get canSubmit(): boolean {
    if (this.submitting()) return false;
    return this.selected().length === 1;
  }

  submit(): void {
    if (!this.canSubmit) return;
    this.submitting.set(true);
    this.error.set(null);

    this.chatService
      .create({
        type: 'DIRECT',
        name: null,
        memberIds: this.selected().map((u) => u.id),
      })
      .subscribe({
        next: (chat) => this.created.emit(chat),
        error: (err) => {
          this.error.set(extractErrorMessage(err, 'Could not create chat.'));
          this.submitting.set(false);
        },
      });
  }

  close(): void {
    this.closed.emit();
  }
}
