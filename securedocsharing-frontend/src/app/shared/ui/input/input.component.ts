import { Component, ChangeDetectionStrategy, input, output, signal } from '@angular/core';

import { FormsModule } from '@angular/forms';

export type InputSize = 'sm' | 'md' | 'lg';
export type InputState = 'default' | 'error' | 'success';

@Component({
  selector: 'sd-input',
  standalone: true,
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="sd-input-wrapper" [class.sd-input-wrapper--disabled]="disabled()">
      @if (label()) {
        <label [for]="id()" class="sd-input-label">
          {{ label() }}
        </label>
      }

      <div class="sd-input-container" [class]="containerClasses()">
        <ng-content select="[prefix]"></ng-content>

        @if (type() === 'textarea') {
          <textarea
            [id]="id()"
            [placeholder]="placeholder()"
            [disabled]="disabled()"
            [readonly]="readonly()"
            [value]="value()"
            (input)="onInput($event)"
            (blur)="onBlur($event)"
            (focus)="onFocus($event)"
            class="sd-input-field sd-input-field--textarea"
            [rows]="rows()"
          ></textarea>
        } @else {
          <input
            [id]="id()"
            [type]="type()"
            [placeholder]="placeholder()"
            [disabled]="disabled()"
            [readonly]="readonly()"
            [value]="value()"
            (input)="onInput($event)"
            (blur)="onBlur($event)"
            (focus)="onFocus($event)"
            class="sd-input-field"
          />
        }

        <ng-content select="[suffix]"></ng-content>
      </div>

      @if (errorText() && state() === 'error') {
        <span class="sd-input-message sd-input-message--error">{{ errorText() }}</span>
      } @else if (helperText()) {
        <span class="sd-input-message sd-input-message--helper">{{ helperText() }}</span>
      }
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
    }

    .sd-input-wrapper {
      display: flex;
      flex-direction: column;
      gap: var(--sd-space-2xs);
      width: 100%;
    }

    .sd-input-label {
      font-family: var(--sd-font-sans);
      font-size: var(--sd-font-size-xs);
      font-weight: var(--sd-weight-medium);
      color: var(--sd-color-text-secondary);
    }

    .sd-input-container {
      display: flex;
      align-items: center;
      gap: var(--sd-space-sm);
      background-color: var(--sd-color-surface);
      border: 1px solid var(--sd-color-border);
      transition: border-color var(--sd-duration-fast) ease, box-shadow var(--sd-duration-fast) ease;
      width: 100%;
    }

    .sd-input-container:hover:not(.sd-input-container--disabled) {
      border-color: var(--sd-color-border-hover);
    }

    .sd-input-container--focused {
      border-color: var(--sd-color-border-focus) !important;
      box-shadow: 0 0 0 2px var(--sd-color-primary-subtle);
    }

    .sd-input-container--error {
      border-color: var(--sd-color-danger) !important;
    }

    .sd-input-container--success {
      border-color: var(--sd-color-success) !important;
    }

    .sd-input-container--disabled {
      background-color: var(--sd-color-surface-disabled);
      cursor: not-allowed;
      opacity: 0.7;
    }

    /* ── Sizes ────────────────────────────────────────────────────── */
    .sd-input-container--sm {
      height: var(--sd-size-control-sm);
      padding: 0 var(--sd-space-sm);
      border-radius: var(--sd-radius-md);
      font-size: var(--sd-font-size-xs);
    }

    .sd-input-container--md {
      height: var(--sd-size-control-md);
      padding: 0 var(--sd-space-md);
      border-radius: var(--sd-radius-md);
      font-size: var(--sd-font-size-base);
    }

    .sd-input-container--lg {
      height: var(--sd-size-control-lg);
      padding: 0 var(--sd-space-lg);
      border-radius: var(--sd-radius-lg);
      font-size: var(--sd-font-size-base);
    }

    .sd-input-container--textarea {
      height: auto;
      padding: var(--sd-space-sm) var(--sd-space-md);
    }

    .sd-input-field {
      flex: 1;
      width: 100%;
      height: 100%;
      border: none;
      background: transparent;
      outline: none;
      color: var(--sd-color-text-primary);
      font-family: var(--sd-font-sans);
      font-size: inherit;
    }

    .sd-input-field::placeholder {
      color: var(--sd-color-text-muted);
    }

    .sd-input-field--textarea {
      resize: vertical;
      min-height: 70px;
    }

    .sd-input-message {
      font-family: var(--sd-font-sans);
      font-size: var(--sd-font-size-xs);
    }

    .sd-input-message--helper {
      color: var(--sd-color-text-muted);
    }

    .sd-input-message--error {
      color: var(--sd-color-danger);
    }
  `],
})
export class InputComponent {
  id = input<string>(`sd-input-${Math.random().toString(36).substring(2, 9)}`);
  label = input<string | undefined>();
  type = input<'text' | 'password' | 'email' | 'number' | 'search' | 'textarea'>('text');
  placeholder = input<string>('');
  value = input<string>('');
  size = input<InputSize>('md');
  state = input<InputState>('default');
  helperText = input<string | undefined>();
  errorText = input<string | undefined>();
  disabled = input<boolean>(false);
  readonly = input<boolean>(false);
  rows = input<number>(3);

  valueChange = output<string>();
  blurred = output<FocusEvent>();
  focused = output<FocusEvent>();

  isFocused = signal<boolean>(false);

  containerClasses(): string {
    const classes = [
      'sd-input-container',
      `sd-input-container--${this.size()}`,
    ];
    if (this.type() === 'textarea') {
      classes.push('sd-input-container--textarea');
    }
    if (this.isFocused()) {
      classes.push('sd-input-container--focused');
    }
    if (this.state() !== 'default') {
      classes.push(`sd-input-container--${this.state()}`);
    }
    if (this.disabled()) {
      classes.push('sd-input-container--disabled');
    }
    return classes.join(' ');
  }

  onInput(event: Event): void {
    const val = (event.target as HTMLInputElement | HTMLTextAreaElement).value;
    this.valueChange.emit(val);
  }

  onFocus(event: FocusEvent): void {
    this.isFocused.set(true);
    this.focused.emit(event);
  }

  onBlur(event: FocusEvent): void {
    this.isFocused.set(false);
    this.blurred.emit(event);
  }
}
