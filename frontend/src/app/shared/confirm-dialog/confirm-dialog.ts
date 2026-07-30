import { Component, HostListener, booleanAttribute, input, output } from '@angular/core';

/**
 * Reusable confirmation modal. Replaces the browser's native `confirm()` so prompts match the app's
 * look and stay non-blocking. The host controls visibility (render it with `@if`), supplies the copy,
 * and reacts to the `confirmed` / `cancelled` outputs. Set `busy` while the confirmed action runs to
 * disable the buttons and show a pending label. Backdrop click and Escape both cancel.
 */
@Component({
  selector: 'app-confirm-dialog',
  templateUrl: './confirm-dialog.html',
  styleUrl: './confirm-dialog.scss',
})
export class ConfirmDialog {
  readonly title = input('Are you sure?');
  readonly message = input('');
  readonly confirmLabel = input('Confirm');
  readonly cancelLabel = input('Cancel');
  /** Style the confirm button as destructive (red). */
  readonly danger = input(false, { transform: booleanAttribute });
  /** While true the action is running: buttons disable and the confirm button shows `busyLabel`. */
  readonly busy = input(false, { transform: booleanAttribute });
  readonly busyLabel = input('Working…');

  readonly confirmed = output<void>();
  readonly cancelled = output<void>();

  confirm(): void {
    if (!this.busy()) this.confirmed.emit();
  }

  cancel(): void {
    if (!this.busy()) this.cancelled.emit();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.cancel();
  }
}
