import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { AuditStore } from './audit.store';
import { UnifiedAuditApiService } from '../../../core/services/unified-audit-api.service';
import {
  SpringPageResponse,
  UnifiedAuditEventResponseDto,
} from '../../../core/models/unified-audit.model';

describe('AuditStore', () => {
  let store: AuditStore;
  let mockAuditApi: jasmine.SpyObj<UnifiedAuditApiService>;

  const mockEvents: UnifiedAuditEventResponseDto[] = [
    {
      id: 'evt-1',
      rawId: 1001,
      source: 'GOVERNANCE',
      action: 'FOLDER_CREATE',
      actionDisplay: 'Created Folder',
      category: 'CONTENT',
      timestamp: '2026-03-31T10:00:00Z',
      actor: {
        userId: 'user-1',
        displayName: 'Alice Admin',
        email: 'alice@example.com',
      },
      target: {
        id: 101,
        name: 'Project Roadmap',
        type: 'FOLDER',
      },
      location: {
        orgCode: 'CORP_A',
        workspaceId: 5,
        workspaceName: 'Operations',
      },
      changes: {
        details: 'Created new folder for roadmap',
      },
      result: {
        status: 'SUCCESS',
      },
    },
  ];

  const mockPageResponse: SpringPageResponse<UnifiedAuditEventResponseDto> = {
    content: mockEvents,
    totalElements: 1,
    totalPages: 1,
    size: 20,
    number: 0,
    first: true,
    last: true,
    empty: false,
  };

  beforeEach(() => {
    mockAuditApi = jasmine.createSpyObj('UnifiedAuditApiService', ['getEvents']);
    mockAuditApi.getEvents.and.returnValue(of(mockPageResponse));

    TestBed.configureTestingModule({
      providers: [
        AuditStore,
        { provide: UnifiedAuditApiService, useValue: mockAuditApi },
      ],
    });

    store = TestBed.inject(AuditStore);
  });

  it('should initialize with default state', () => {
    expect(store.criteria().scopeType).toBe('PERSONAL');
    expect(store.criteria().view).toBe('ALL');
    expect(store.page()).toBe(0);
    expect(store.size()).toBe(20);
    expect(store.events().length).toBe(0);
    expect(store.isAccessDenied()).toBeFalse();
    expect(store.hasActiveFilters()).toBeFalse();
  });

  it('should load events and populate signals', () => {
    store.loadEvents();

    expect(mockAuditApi.getEvents).toHaveBeenCalledWith(
      jasmine.objectContaining({ scopeType: 'PERSONAL', view: 'ALL' }),
      0,
      20,
      'timestamp,desc'
    );
    expect(store.events()).toEqual(mockEvents);
    expect(store.totalElements()).toBe(1);
    expect(store.totalPages()).toBe(1);
    expect(store.isLoading()).toBeFalse();
    expect(store.hasEvents()).toBeTrue();
  });

  it('should handle 403 access denied gracefully without crashing', () => {
    const error403 = new HttpErrorResponse({
      status: 403,
      statusText: 'Forbidden',
      error: { message: 'Forbidden access to organization audit' },
    });
    mockAuditApi.getEvents.and.returnValue(throwError(() => error403));

    store.loadEvents();

    expect(store.isAccessDenied()).toBeTrue();
    expect(store.error()).toContain('Forbidden access to organization audit');
    expect(store.isLoading()).toBeFalse();
    expect(store.events().length).toBe(0);
  });

  it('should update scope and container and reload page 0', () => {
    store.setScopeAndContainer('ORGANIZATION', { orgCode: 'CORP_A' });

    expect(store.criteria().scopeType).toBe('ORGANIZATION');
    expect(store.criteria().orgCode).toBe('CORP_A');
    expect(store.page()).toBe(0);
    expect(mockAuditApi.getEvents).toHaveBeenCalledWith(
      jasmine.objectContaining({ scopeType: 'ORGANIZATION', orgCode: 'CORP_A' }),
      0,
      20,
      'timestamp,desc'
    );
  });

  it('should update filters and reload', () => {
    store.updateFilters({
      search: 'roadmap',
      category: 'CONTENT',
    });

    expect(store.criteria().search).toBe('roadmap');
    expect(store.criteria().category).toBe('CONTENT');
    expect(store.hasActiveFilters()).toBeTrue();
    expect(store.page()).toBe(0);
  });

  it('should clear filters preserving container criteria', () => {
    store.setScopeAndContainer('WORKSPACE', { workspaceId: 10 });
    store.updateFilters({ search: 'test' });
    expect(store.hasActiveFilters()).toBeTrue();

    store.clearFilters();

    expect(store.criteria().scopeType).toBe('WORKSPACE');
    expect(store.criteria().workspaceId).toBe(10);
    expect(store.criteria().search).toBeUndefined();
    expect(store.hasActiveFilters()).toBeFalse();
  });

  it('should handle detail drawer open and close', () => {
    expect(store.isDrawerOpen()).toBeFalse();
    expect(store.selectedEvent()).toBeNull();

    store.openDetail(mockEvents[0]);
    expect(store.isDrawerOpen()).toBeTrue();
    expect(store.selectedEvent()).toEqual(mockEvents[0]);

    store.closeDetail();
    expect(store.isDrawerOpen()).toBeFalse();
  });

  it('should update active view with GOVERNANCE and CONTENT and reload', () => {
    store.setView('GOVERNANCE');
    expect(store.activeView()).toBe('GOVERNANCE');
    expect(store.criteria().view).toBe('GOVERNANCE');
    expect(store.page()).toBe(0);
    expect(mockAuditApi.getEvents).toHaveBeenCalledWith(
      jasmine.objectContaining({ view: 'GOVERNANCE' }),
      0,
      20,
      'timestamp,desc'
    );

    store.setView('CONTENT');
    expect(store.activeView()).toBe('CONTENT');
    expect(store.criteria().view).toBe('CONTENT');
    expect(store.page()).toBe(0);
    expect(mockAuditApi.getEvents).toHaveBeenCalledWith(
      jasmine.objectContaining({ view: 'CONTENT' }),
      0,
      20,
      'timestamp,desc'
    );
  });
});
