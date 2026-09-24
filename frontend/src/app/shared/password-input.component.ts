import { Component, Input, forwardRef, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { IconComponent } from './icon.component';

/**
 * Password field with a show/hide (eye) toggle. Works with `formControlName`
 * and `[(ngModel)]` alike (`ControlValueAccessor`).
 */
@Component({
  selector: 'app-password-input',
  standalone: true,
  imports: [IconComponent],
  template: `
    <div class="relative">
      <input class="form-input pr-11" [id]="inputId" [type]="visible() ? 'text' : 'password'"
             [attr.autocomplete]="autocomplete" [value]="value" [disabled]="disabled"
             (input)="onInput($any($event.target).value)" (blur)="onTouched()" />
      <button type="button"
              class="absolute inset-y-0 right-0 flex w-11 items-center justify-center text-slate-400 hover:text-slate-700"
              [attr.aria-label]="visible() ? 'Masquer le mot de passe' : 'Afficher le mot de passe'"
              [attr.aria-pressed]="visible()" (click)="visible.set(!visible())">
        <app-icon [name]="visible() ? 'eye-off' : 'eye'" class="h-5 w-5" />
      </button>
    </div>
  `,
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => PasswordInputComponent), multi: true },
  ],
})
export class PasswordInputComponent implements ControlValueAccessor {
  @Input() inputId = '';
  @Input() autocomplete = 'current-password';

  visible = signal(false);
  value = '';
  disabled = false;

  private onChange: (value: string) => void = () => {};
  onTouched: () => void = () => {};

  onInput(v: string): void {
    this.value = v;
    this.onChange(v);
  }

  writeValue(value: string | null): void {
    this.value = value ?? '';
  }
  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }
  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }
}
