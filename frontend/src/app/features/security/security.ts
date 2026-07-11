import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { PREKEY_LOW_WATERMARK, SignalService } from '../../core/services/signal.service';
import { extractErrorMessage } from '../../core/util/api-error';

@Component({
  selector: 'app-security',
  imports: [RouterLink],
  templateUrl: './security.html',
  styleUrl: './security.scss',
})
export class Security {
  private readonly signalService = inject(SignalService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly user = this.auth.user;
  readonly state = this.signalService.state;

  readonly provisioned = signal<boolean>(false);
  readonly fingerprint = signal<string | null>(null);
  readonly registrationId = signal<number | null>(null);
  readonly remainingPreKeys = signal<number | null>(null);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);

  readonly safetyNumber = computed(() => {
    const fp = this.fingerprint();
    return fp ? SignalService.formatFingerprint(fp) : null;
  });
  readonly lowOnKeys = computed(() => {
    const n = this.remainingPreKeys();
    return n !== null && n <= PREKEY_LOW_WATERMARK;
  });

  constructor() {
    this.refresh();
  }

  async refresh(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      await this.signalService.ensureProvisioned();
      this.provisioned.set(await this.signalService.isProvisioned());
      this.fingerprint.set(await this.signalService.getFingerprint());
      this.registrationId.set(await this.signalService.getRegistrationId());
      this.remainingPreKeys.set(await this.signalService.remainingPreKeys());
    } catch (err) {
      this.error.set(extractErrorMessage(err, 'Could not load encryption status.'));
    } finally {
      this.loading.set(false);
    }
  }

  async generateMore(): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    try {
      await this.signalService.replenishPreKeys();
      this.remainingPreKeys.set(await this.signalService.remainingPreKeys());
      this.notice.set('Published a fresh batch of one-time pre-keys.');
    } catch (err) {
      this.error.set(extractErrorMessage(err, 'Could not publish more pre-keys.'));
    } finally {
      this.busy.set(false);
    }
  }

  async resetDevice(): Promise<void> {
    const ok = confirm(
      'Reset device keys? This creates a NEW identity (and a new safety number). Existing conversations on this device will need to re-establish sessions. Continue?',
    );
    if (!ok) return;
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    try {
      await this.signalService.resetDevice();
      await this.refresh();
      this.notice.set('Device keys reset and re-published.');
    } catch (err) {
      this.error.set(extractErrorMessage(err, 'Could not reset device keys.'));
    } finally {
      this.busy.set(false);
    }
  }

  back(): void {
    this.router.navigate(['/chats']);
  }
}
