import { Component, ChangeDetectionStrategy } from '@angular/core';
import { CreateWorkspaceModalComponent } from '../create-workspace-modal/create-workspace-modal.component';

/**
 * @deprecated Use CreateWorkspaceModalComponent instead.
 * Retained temporarily for backward compatibility with unmigrated callers.
 */
@Component({
  selector: 'app-create-subspace-modal',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CreateWorkspaceModalComponent],
  template: `<app-create-workspace-modal />`,
})
export class CreateSubspaceModalComponent {}
