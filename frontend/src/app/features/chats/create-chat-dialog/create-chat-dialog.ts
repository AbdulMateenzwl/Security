import { Component, inject, output, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { User } from '../../../core/models/auth.models';
import { Chat, ChatType } from '../../../core/models/chat.models';
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

  readonly type = signal<ChatType>('DIRECT');
  readonly groupName = new FormControl('', { nonNullable: true });
  readonly searchControl = new FormControl('', { nonNullable: true });

  readonly results = signal<User[]>([]);
  readonly selected = signal<User[]>([]);
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
            return [[] as User[]];
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

  setType(type: ChatType): void {
    this.type.set(type);
    // Direct chats have exactly one member; drop extras when switching back.
    if (type === 'DIRECT' && this.selected().length > 1) {
      this.selected.set([this.selected()[0]]);
    }
    this.error.set(null);
  }

  select(user: User): void {
    if (this.type() === 'DIRECT') {
      this.selected.set([user]);
    } else if (!this.selected().some((u) => u.id === user.id)) {
      this.selected.update((list) => [...list, user]);
    }
    this.results.update((list) => list.filter((u) => u.id !== user.id));
    this.searchControl.setValue('');
  }

  remove(user: User): void {
    this.selected.update((list) => list.filter((u) => u.id !== user.id));
  }

  get canSubmit(): boolean {
    if (this.submitting()) return false;
    if (this.type() === 'DIRECT') return this.selected().length === 1;
    return this.selected().length >= 1 && this.groupName.value.trim().length > 0;
  }

  submit(): void {
    if (!this.canSubmit) return;
    this.submitting.set(true);
    this.error.set(null);

    this.chatService
      .create({
        type: this.type(),
        name: this.type() === 'GROUP' ? this.groupName.value.trim() : null,
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
