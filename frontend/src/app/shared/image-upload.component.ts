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

  /**
   * Resizes to at most 1600 px and re-encodes as WebP (quality 0.8) before
   * upload (TECH-07): a phone photo of several Mo becomes ~200 Ko, which keeps
   * pages light on 3G. Falls back on the original file if the browser cannot.
   */
  private async compress(file: File): Promise<Blob> {
    if (!file.type.startsWith('image/') || file.type === 'image/gif') return file;
    try {
      const bitmap = await createImageBitmap(file);
      const scale = Math.min(1, 1600 / Math.max(bitmap.width, bitmap.height));
      const canvas = document.createElement('canvas');
      canvas.width = Math.round(bitmap.width * scale);
      canvas.height = Math.round(bitmap.height * scale);
      canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
      const blob = await new Promise<Blob | null>((ok) => canvas.toBlob(ok, 'image/webp', 0.8));
      return blob && blob.type === 'image/webp' && blob.size < file.size ? blob : file;
    } catch {
      return file;
    }
  }

  async onFile(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.err.set(null);
    this.uploading.set(true);
    const blob = await this.compress(file);
    const form = new FormData();
    form.append('file', blob, blob === file ? file.name : file.name.replace(/\.[^.]+$/, '') + '.webp');
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
