import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { SettingsPageComponent } from './settings.page';
import { SettingsApiService } from '../services/settings-api.service';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';
import { signal } from '@angular/core';

describe('SettingsPageComponent', () => {
  let component: SettingsPageComponent;
  let fixture: ComponentFixture<SettingsPageComponent>;

  const mockSettingsApi = jasmine.createSpyObj('SettingsApiService', [
    'getPrivacySettings',
    'getWorkspacePolicy',
    'updateWorkspacePolicy',
    'updatePrivacySettings',
  ]);

  const mockWorkspaceContext = {
    isPersonal: () => true,
    isOrganization: () => false,
    activeOrgCode: () => null,
    activeOrganization: signal(null),
    activeWorkspace: signal(null),
  };

  beforeEach(async () => {
    mockSettingsApi.getPrivacySettings.and.returnValue(
      of({
        allowInternalDms: true,
        allowExternalDms: true,
        readReceiptsEnabled: true,
      })
    );
    mockSettingsApi.getWorkspacePolicy.and.returnValue(of(null));

    await TestBed.configureTestingModule({
      imports: [SettingsPageComponent],
      providers: [
        provideRouter([]),
        { provide: SettingsApiService, useValue: mockSettingsApi },
        { provide: WorkspaceContextStore, useValue: mockWorkspaceContext },
        {
          provide: ActivatedRoute,
          useValue: {
            queryParams: of({ tab: 'notifications' }),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SettingsPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should initialize and set activeTab to notifications from query params', () => {
    expect(component.activeTab()).toBe('notifications');
  });

  it('should allow switching tabs manually', () => {
    component.setTab('privacy');
    expect(component.activeTab()).toBe('privacy');

    component.setTab('account');
    expect(component.activeTab()).toBe('account');

    component.setTab('notifications');
    expect(component.activeTab()).toBe('notifications');
  });
});
