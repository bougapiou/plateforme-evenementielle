import { Component, Input, forwardRef } from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';
import { COUNTRY_CALLING_CODES } from './countries';

/**
 * Phone number field split in two: a country-code select and a local-number
 * input (typed without the dial code). Works with `formControlName` and
 * `[(ngModel)]` alike (`ControlValueAccessor`) — the combined value it emits
 * (e.g. `"+226 70000000"`) matches the backend's phone pattern directly.
 */
@Component({
  selector: 'app-phone-input',
  standalone: true,
  imports: [FormsModule],
  // A custom element is inline by default and an <input> never shrinks below its intrinsic width on its own:
  // without `block` / `min-w-0` the number field overflowed its column on small screens.
  host: { class: 'block min-w-0' },
  template: `
    <div class="flex min-w-0 gap-2">
      <select class="form-input w-[6.5rem] shrink-0 sm:w-[7.5rem]" [(ngModel)]="dialCode" (ngModelChange)="emit()"
              aria-label="Indicatif du pays">
        @for (c of countries; track c.code) {
          <option [value]="c.phoneCode">{{ c.code }} {{ c.phoneCode }}</option>
        }
      </select>
      <input class="form-input min-w-0 flex-1" type="tel" inputmode="tel" [placeholder]="placeholder"
             aria-label="Numéro de téléphone" [(ngModel)]="localNumber" (ngModelChange)="emit()" />
    </div>
  `,
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => PhoneInputComponent), multi: true },
  ],
})
export class PhoneInputComponent implements ControlValueAccessor {
  @Input() placeholder = '70 00 00 00';

  countries = COUNTRY_CALLING_CODES;
  dialCode = '+226';
  localNumber = '';

  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(value: string | null): void {
    const trimmed = (value ?? '').trim();
    if (!trimmed) {
      this.localNumber = '';
      return;
    }
    // Longest dial code first so "+1" doesn't swallow "+1 868" (Trinidad).
    const match = [...this.countries]
      .sort((a, b) => b.phoneCode.length - a.phoneCode.length)
      .find((c) => trimmed.startsWith(c.phoneCode));
    if (match) {
      this.dialCode = match.phoneCode;
      this.localNumber = trimmed.slice(match.phoneCode.length).trim();
    } else {
      this.localNumber = trimmed;
    }
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  emit(): void {
    const digits = this.localNumber.replace(/[^0-9]/g, '');
    this.onChange(digits ? `${this.dialCode} ${digits}` : '');
    this.onTouched();
  }
}
