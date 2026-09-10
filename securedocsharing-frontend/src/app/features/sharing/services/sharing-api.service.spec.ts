import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { SharingApiService } from './sharing-api.service';
import { environment } from '../../../../environments/environment';

describe('SharingApiService', () => {
  let service: SharingApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [SharingApiService],
    });

    service = TestBed.inject(SharingApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should call getSharedWithMe and return response', () => {
    const mockData = { sharedFolders: [], sharedDocuments: [] };

    service.getSharedWithMe().subscribe((res) => {
      expect(res).toEqual(mockData);
    });

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/v1/permissions/shared-with-me`);
    expect(req.request.method).toBe('GET');
    req.flush(mockData);
  });

  it('should call getSharedByMe and return response', () => {
    const mockData = { sharedFolders: [], sharedDocuments: [] };

    service.getSharedByMe().subscribe((res) => {
      expect(res).toEqual(mockData);
    });

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/v1/permissions/shared-by-me?includeInactive=true`);
    expect(req.request.method).toBe('GET');
    req.flush(mockData);
  });

  it('should call revokePermission with DELETE method', () => {
    service.revokePermission(42).subscribe();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/v1/permissions/42`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
