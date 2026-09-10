import { Component, Input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { cn } from '../../../../lib/utils';

@Component({
  selector: 'ui-card',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div [class]="computedClass()">
      <ng-content></ng-content>
    </div>
  `,
})
export class UiCardComponent {
  @Input() class: string = '';
  computedClass = computed(() =>
    cn('rounded-xl border border-border bg-card text-card-foreground shadow-sm', this.class)
  );
}

@Component({
  selector: 'ui-card-header',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div [class]="computedClass()">
      <ng-content></ng-content>
    </div>
  `,
})
export class UiCardHeaderComponent {
  @Input() class: string = '';
  computedClass = computed(() => cn('flex flex-col space-y-1.5 p-6', this.class));
}

@Component({
  selector: 'ui-card-title',
  standalone: true,
  imports: [CommonModule],
  template: `
    <h3 [class]="computedClass()">
      <ng-content></ng-content>
    </h3>
  `,
})
export class UiCardTitleComponent {
  @Input() class: string = '';
  computedClass = computed(() =>
    cn('text-lg font-semibold leading-none tracking-tight', this.class)
  );
}

@Component({
  selector: 'ui-card-description',
  standalone: true,
  imports: [CommonModule],
  template: `
    <p [class]="computedClass()">
      <ng-content></ng-content>
    </p>
  `,
})
export class UiCardDescriptionComponent {
  @Input() class: string = '';
  computedClass = computed(() => cn('text-sm text-muted-foreground', this.class));
}

@Component({
  selector: 'ui-card-content',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div [class]="computedClass()">
      <ng-content></ng-content>
    </div>
  `,
})
export class UiCardContentComponent {
  @Input() class: string = '';
  computedClass = computed(() => cn('p-6 pt-0', this.class));
}

@Component({
  selector: 'ui-card-footer',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div [class]="computedClass()">
      <ng-content></ng-content>
    </div>
  `,
})
export class UiCardFooterComponent {
  @Input() class: string = '';
  computedClass = computed(() => cn('flex items-center p-6 pt-0', this.class));
}
