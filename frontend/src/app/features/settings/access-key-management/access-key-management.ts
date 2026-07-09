import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  AccessKeyCreated,
  AccessKeyOverview,
  AccessKeyService,
} from '../../../core/access-key.service';
import { AuthRole, AuthService } from '../../../core/auth.service';
import { SnackService } from '../../../shared/services/snack.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';

@Component({
  selector: 'app-access-key-management',
  imports: [
    DatePipe,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatMenuModule,
    MatSelectModule,
    MatTooltipModule,
  ],
  templateUrl: './access-key-management.html',
  styleUrl: './access-key-management.scss',
})
export class AccessKeyManagement implements OnInit {
  private readonly accessKeyService = inject(AccessKeyService);
  private readonly snack = inject(SnackService);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly auth = inject(AuthService);

  readonly keys = signal<AccessKeyOverview[]>([]);
  readonly loading = signal(false);
  readonly error = signal(false);

  readonly newRole = signal<AuthRole>('USER');
  readonly newQuota = signal(10);
  readonly generating = signal(false);
  readonly createdKey = signal<AccessKeyCreated | null>(null);

  readonly editingId = signal<number | null>(null);
  readonly editRole = signal<AuthRole>('USER');
  readonly editQuota = signal(10);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.accessKeyService.listKeys().subscribe({
      next: (keys) => {
        this.keys.set(keys);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  generate(): void {
    this.generating.set(true);
    const quota = this.newRole() === 'SUPERUSER' ? 0 : this.newQuota();
    this.accessKeyService.generateKey(this.newRole(), quota).subscribe({
      next: (created) => {
        this.createdKey.set(created);
        this.generating.set(false);
        this.load();
      },
      error: () => {
        this.generating.set(false);
        this.snack.error('Zugriffscode konnte nicht erstellt werden.');
      },
    });
  }

  dismissCreatedKey(): void {
    this.createdKey.set(null);
  }

  copyKey(key: string): void {
    navigator.clipboard.writeText(key);
    this.snack.success('Zugriffscode kopiert.');
  }

  startEdit(key: AccessKeyOverview): void {
    this.editingId.set(key.id);
    this.editRole.set(key.role);
    this.editQuota.set(key.quotaLimit);
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  onEditRoleChange(role: AuthRole): void {
    this.editRole.set(role);
    // Wechsel von SUPERUSER (quotaLimit=0) zu USER — 0/Tag macht keinen Sinn, sinnvollen
    // Default vorschlagen statt den alten Superuser-Wert stehen zu lassen.
    if (role === 'USER' && this.editQuota() < 5) {
      this.editQuota.set(10);
    }
  }

  saveEdit(id: number): void {
    const quota = this.editRole() === 'SUPERUSER' ? null : this.editQuota();
    this.accessKeyService.updateKey(id, this.editRole(), quota).subscribe({
      next: (updated) => {
        this.keys.update((list) => list.map((k) => (k.id === id ? updated : k)));
        this.editingId.set(null);
      },
      error: () => this.snack.error('Änderung fehlgeschlagen.'),
    });
  }

  isSelf(key: AccessKeyOverview): boolean {
    return this.auth.accessKeyId() === key.id;
  }

  toggleRevoke(key: AccessKeyOverview): void {
    if (this.isSelf(key)) {
      this.snack.error('Der eigene aktive Zugriffscode kann nicht gesperrt werden.');
      return;
    }
    const action$ = key.revoked
      ? this.accessKeyService.unrevoke(key.id)
      : this.accessKeyService.revoke(key.id);
    action$.subscribe({
      next: (updated) =>
        this.keys.update((list) => list.map((k) => (k.id === updated.id ? updated : k))),
      error: () => this.snack.error('Aktion fehlgeschlagen.'),
    });
  }

  deleteKey(key: AccessKeyOverview): void {
    if (this.isSelf(key)) {
      this.snack.error('Der eigene aktive Zugriffscode kann nicht gelöscht werden.');
      return;
    }
    this.confirmDialog
      .confirm({
        title: 'Zugriffscode löschen',
        message: 'Zugriffscode endgültig löschen? Das kann nicht rückgängig gemacht werden.',
        confirmLabel: 'Löschen',
        destructive: true,
      })
      .subscribe((confirmed) => {
        if (!confirmed) return;
        this.accessKeyService.deleteKey(key.id).subscribe({
          next: () => this.keys.update((list) => list.filter((k) => k.id !== key.id)),
          error: () => this.snack.error('Löschen fehlgeschlagen.'),
        });
      });
  }

  roleLabel(role: AuthRole): string {
    return role === 'SUPERUSER' ? 'Superuser' : 'User';
  }
}
