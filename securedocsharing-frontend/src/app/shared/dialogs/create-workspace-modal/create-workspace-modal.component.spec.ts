import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { CreateWorkspaceModalComponent } from './create-workspace-modal.component';
import { LayoutStore, ModalType } from '../../../state/layout/layout.store';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';
import { WorkspaceApiService } from '../../../core/services/workspace-api.service';
import { NotificationService } from '../../../core/services/notification.service';
import { OrganizationModel } from '../../../core/models/organization.model';
import { ProjectWorkspaceModel } from '../../../core/models/project-workspace.model';

describe('CreateWorkspaceModalComponent', () => {
  let component: CreateWorkspaceModalComponent;
  let fixture: ComponentFixture<CreateWorkspaceModalComponent>;
  let router: Router;

  let mockActiveModal: ReturnType<typeof signal<ModalType | null>>;
  let closeModalSpy: jasmine.Spy;

  let mockActiveOrg: ReturnType<typeof signal<OrganizationModel | null>>;
  let mockAvailableWorkspaces: ReturnType<typeof signal<ProjectWorkspaceModel[]>>;
  let setActiveWorkspaceSpy: jasmine.Spy;

  let createPersonalWorkspaceSpy: jasmine.Spy;
  let createWorkspaceSpy: jasmine.Spy;
  let notificationSuccessSpy: jasmine.Spy;

  const mockCreatedPersonalWs: ProjectWorkspaceModel = {
    id: 101,
    organizationCode: null,
    name: 'My Personal Workspace',
    description: null,
    rootFolderId: 501,
    status: 'ACTIVE',
    isDefault: false,
    createdAt: '2026-09-05T00:00:00Z',
  };

  const mockCreatedOrgWs: ProjectWorkspaceModel = {
    id: 202,
    organizationCode: 'ACME',
    name: 'Engineering',
    description: null,
    rootFolderId: 601,
    status: 'ACTIVE',
    isDefault: false,
    createdAt: '2026-09-05T00:00:00Z',
  };

  beforeEach(async () => {
    mockActiveModal = signal<ModalType | null>('CREATE_WORKSPACE');
    closeModalSpy = jasmine.createSpy('closeModal');

    mockActiveOrg = signal<OrganizationModel | null>(null);
    mockAvailableWorkspaces = signal<ProjectWorkspaceModel[]>([]);
    setActiveWorkspaceSpy = jasmine.createSpy('setActiveWorkspace');

    createPersonalWorkspaceSpy = jasmine
      .createSpy('createPersonalWorkspace')
      .and.returnValue(of(mockCreatedPersonalWs));
    createWorkspaceSpy = jasmine
      .createSpy('createWorkspace')
      .and.returnValue(of(mockCreatedOrgWs));
    notificationSuccessSpy = jasmine.createSpy('success');

    const mockLayoutStore = {
      activeModal: mockActiveModal,
      closeModal: closeModalSpy,
    };

    const mockWorkspaceContextStore = {
      activeOrganization: mockActiveOrg,
      availableWorkspaces: mockAvailableWorkspaces,
      setActiveWorkspace: setActiveWorkspaceSpy,
    };

    const mockWorkspaceApiService = {
      createPersonalWorkspace: createPersonalWorkspaceSpy,
      createWorkspace: createWorkspaceSpy,
    };

    const mockNotificationService = {
      success: notificationSuccessSpy,
      error: jasmine.createSpy('error'),
    };

    await TestBed.configureTestingModule({
      imports: [CreateWorkspaceModalComponent],
      providers: [
        { provide: LayoutStore, useValue: mockLayoutStore },
        { provide: WorkspaceContextStore, useValue: mockWorkspaceContextStore },
        { provide: WorkspaceApiService, useValue: mockWorkspaceApiService },
        { provide: NotificationService, useValue: mockNotificationService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CreateWorkspaceModalComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    spyOn(router, 'navigate');
    fixture.detectChanges();
  });

  it('should create CreateWorkspaceModalComponent', () => {
    expect(component).toBeTruthy();
  });

  describe('Modal Visibility and Triggers', () => {
    it('should be open when activeModal is CREATE_WORKSPACE', () => {
      mockActiveModal.set('CREATE_WORKSPACE');
      fixture.detectChanges();
      expect(component.isOpen()).toBeTrue();

      const modal = fixture.nativeElement.querySelector('.modal-card');
      expect(modal).toBeTruthy();
    });

    it('should be open when activeModal is legacy CREATE_SUBSPACE for backward compatibility', () => {
      mockActiveModal.set('CREATE_SUBSPACE');
      fixture.detectChanges();
      expect(component.isOpen()).toBeTrue();

      const modal = fixture.nativeElement.querySelector('.modal-card');
      expect(modal).toBeTruthy();
    });

    it('should be closed when activeModal is null or unrelated', () => {
      mockActiveModal.set(null);
      fixture.detectChanges();
      expect(component.isOpen()).toBeFalse();

      const modal = fixture.nativeElement.querySelector('.modal-card');
      expect(modal).toBeNull();
    });
  });

  describe('Personal Workspace Creation (POST /api/v1/personal/workspaces)', () => {
    beforeEach(() => {
      mockActiveOrg.set(null); // Personal Space context
      mockAvailableWorkspaces.set([]); // Zero-workspace state
      fixture.detectChanges();
    });

    it('should call WorkspaceApiService.createPersonalWorkspace with correct payload when in Personal Space', () => {
      component.name.set('My Personal Workspace');
      fixture.detectChanges();

      component.onSubmit();

      expect(createPersonalWorkspaceSpy).toHaveBeenCalledWith({
        name: 'My Personal Workspace',
      });
      expect(createWorkspaceSpy).not.toHaveBeenCalled();
    });

    it('should activate created workspace in WorkspaceContextStore without duplicate GET', () => {
      component.name.set('My Personal Workspace');
      component.onSubmit();

      expect(setActiveWorkspaceSpy).toHaveBeenCalledWith(mockCreatedPersonalWs);
    });

    it('should navigate to canonical personal workspace dashboard /w/personal/w/:numericId/dashboard', () => {
      component.name.set('My Personal Workspace');
      component.onSubmit();

      expect(router.navigate).toHaveBeenCalledWith([
        '/w',
        'personal',
        'w',
        '101',
        'dashboard',
      ]);
    });

    it('should close the modal and display success toast on creation', () => {
      component.name.set('My Personal Workspace');
      component.onSubmit();

      expect(closeModalSpy).toHaveBeenCalled();
      expect(notificationSuccessSpy).toHaveBeenCalledWith(
        'Workspace "My Personal Workspace" created successfully.'
      );
      expect(component.name()).toBe('');
    });
  });

  describe('Organization Workspace Creation (POST /api/v1/organizations/{orgCode}/workspaces)', () => {
    beforeEach(() => {
      mockActiveOrg.set({ orgCode: 'ACME', name: 'Acme Corporation' });
      fixture.detectChanges();
    });

    it('should call WorkspaceApiService.createWorkspace with orgCode and correct payload', () => {
      component.name.set('Engineering');
      fixture.detectChanges();

      component.onSubmit();

      expect(createWorkspaceSpy).toHaveBeenCalledWith('ACME', {
        name: 'Engineering',
      });
      expect(createPersonalWorkspaceSpy).not.toHaveBeenCalled();
    });

    it('should activate created workspace in store and navigate to canonical org workspace dashboard', () => {
      component.name.set('Engineering');
      component.onSubmit();

      expect(setActiveWorkspaceSpy).toHaveBeenCalledWith(mockCreatedOrgWs);
      expect(router.navigate).toHaveBeenCalledWith([
        '/org',
        'ACME',
        'w',
        '202',
        'dashboard',
      ]);
      expect(closeModalSpy).toHaveBeenCalled();
    });
  });

  describe('Validation & Error Handling', () => {
    it('should disable submit button when workspace name is shorter than 2 characters', () => {
      component.name.set('A');
      expect(component.isValid()).toBeFalse();

      component.name.set('  ');
      expect(component.isValid()).toBeFalse();

      component.name.set('Dev');
      expect(component.isValid()).toBeTrue();
    });

    it('should display duplicate-name or backend error message when creation fails', () => {
      createPersonalWorkspaceSpy.and.returnValue(
        throwError(() => ({
          error: { message: 'A workspace with name "Duplicate" already exists' },
        }))
      );

      component.name.set('Duplicate');
      component.onSubmit();

      expect(component.isLoading()).toBeFalse();
      expect(component.errorMessage()).toBe(
        'A workspace with name "Duplicate" already exists'
      );
      expect(closeModalSpy).not.toHaveBeenCalled();
      expect(setActiveWorkspaceSpy).not.toHaveBeenCalled();
    });
  });

  describe('Decoupling & No FolderApiService', () => {
    it('should not contain or call any FolderApiService or /subspaces endpoints', () => {
      expect((component as any).folderApiService).toBeUndefined();
      expect((component as any).createPersonalSubspace).toBeUndefined();
      expect((component as any).createOrganizationSubspace).toBeUndefined();
    });
  });
});
