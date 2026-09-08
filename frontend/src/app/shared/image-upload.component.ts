import { Component, inject, input, output, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';

/**
 * Reusable image picker: shows a preview, uploads on selection and emits the
 * public URL returned by `POST /api/uploads/image`.
 */
@Component({
  selector: 'app-image-upload',
  standalone: true,
  template: `
    <div class="flex items-start gap-4">
      <div
        class="flex h-24 w-40 shrink-0 items-center justify-center overflow-hidden rounded-lg border border-slate-200 bg-slate-50"
      >
        @if (value()) {
          <img [src]="value()" alt="" class="h-full w-full object-cover" />
        } @else {
          <span class="text-xs text-slate-400">Aucune image</span>
        }
      </div>
      <div class="space-y-1">
        <label class="btn-ghost cursor-pointer text-brand-700">
          {{ uploading() ? 'Téléversement…' : 'Choisir une image' }}
          <input
            type="file"
            class="hidden"
            accept="image/png,image/jpeg,image/webp"
            [disabled]="uploading() || disabled()"
            (change)="onFile($event)"
          />
        </label>
        @if (value()) {
          <button type="button" class="btn-ghost block text-red-600" (click)="clear()">
            Retirer
          </button>
        }
        @if (err()) { <p class="text-xs text-red-600">{{ err() }}</p> }
        <p class="text-xs text-slate-400">PNG, JPEG ou WEBP — 15 Mo max.</p>
      </div>
    </div>
  `,
})
export class ImageUploadComponent {
  private http = inject(HttpClient);

  value = input<string | null>(null);
  folder = input<string>('images');
  disabled = input<boolean>(false);
  valueChange = output<string | null>();

  uploading = signal(false);
  err = signal<string | null>(null);

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.err.set(null);
    this.uploading.set(true);
    const form = new FormData();
    form.append('file', file);
    form.append('dossier', this.folder());
    this.http
      .post<{ url: string }>(`${environment.apiBaseUrl}/uploads/image`, form)
      .subscribe({
        next: (r) => {
          this.uploading.set(false);
          this.valueChange.emit(r.url);
        },
        error: (e) => {
          this.uploading.set(false);
          this.err.set(e?.error?.message ?? 'Téléversement impossible.');
        },
      });
    input.value = '';
  }

  clear(): void {
    this.valueChange.emit(null);
  }
}
