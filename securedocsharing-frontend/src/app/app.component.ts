import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * AppComponent: Root bootstrap component.
 * Contains only the router outlet — layout is handled by AppShellComponent.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `<router-outlet />`,
})
export class AppComponent {}
