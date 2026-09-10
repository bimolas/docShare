import { TestBed, ComponentFixture } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ShareLinkDetailModalComponent } from './share-link-detail-modal.component';
import { ShareApiService } from '../../permissions/services/share-api.service';
import { PermissionApiService } from '../../permissions/services/permission-api.service';
import { ShareLinkResponseDto, ShareLinkRevealResponseDto } from '../../permissions/interfaces/permission.interfaces';

describe('ShareLinkDetailModalComponent — Zero-Trust Token Management', () => {
  let component: ShareLinkDetailModalComponent;
  let fixture: ComponentFixture<ShareLinkDetailModalComponent>;
  let mockShareApi: jasmine.SpyObj<ShareApiService>;
  let mockPermissionApi: jasmine.SpyObj<PermissionApiService>;

  const mockShareLink: ShareLinkResponseDto = {
    id: 55,
    token: null,
    permissionType: 'ALLOW',
    permissionLevel: 'VIEW',
    active: true,
    createdByUserId: 'creator-user-1',
    createdByUserEmail: 'creator@example.com',
    canReveal: true,
    expiresAt: '2026-12-31T23:59:59Z',
    createdAt: '2026-01-01T00:00:00Z',
    passwordProtected: false,
  };

  beforeEach(() => {
    mockShareApi = jasmine.createSpyObj('ShareApiService', ['revealShareLink']);
    mockPermissionApi = jasmine.createSpyObj('PermissionApiService', ['revokePermission']);

    mockShareApi.revealShareLink.and.returnValue(
      of({
        permissionId: 55,
        shareLinkToken: 'token-xyz-revealed',
        shareUrl: 'http://localhost/share/token-xyz-revealed',
        expiresAt: '2026-12-31T23:59:59Z',
      } as ShareLinkRevealResponseDto)
    );
    mockPermissionApi.revokePermission.and.returnValue(of(undefined as any));

    TestBed.configureTestingModule({
      imports: [ShareLinkDetailModalComponent],
      providers: [
        { provide: ShareApiService, useValue: mockShareApi },
        { provide: PermissionApiService, useValue: mockPermissionApi },
      ],
    });

    fixture = TestBed.createComponent(ShareLinkDetailModalComponent);
    component = fixture.componentInstance;
  });

  it('1. should show Reveal & Copy URL when token is null and canReveal is true', () => {
    component.isOpen = true;
    component.shareLink = { ...mockShareLink, token: null, canReveal: true };
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const revealBtn = compiled.querySelector('.btn-reveal-action');
    expect(revealBtn).toBeTruthy();
    expect(compiled.querySelector('.security-audit-notice')).toBeFalsy();
  });

  it('2. should show security audit notice and hide reveal button when canReveal is false', () => {
    component.isOpen = true;
    component.shareLink = { ...mockShareLink, token: null, canReveal: false };
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const auditNotice = compiled.querySelector('.security-audit-notice');
    expect(auditNotice).toBeTruthy();
    expect(compiled.querySelector('.btn-reveal-action')).toBeFalsy();
  });

  it('3. clicking revealAndCopy should call shareApiService.revealShareLink with permission id', () => {
    component.isOpen = true;
    component.shareLink = { ...mockShareLink, id: 55, token: null, canReveal: true };
    spyOn(component, 'copyUrl');

    component.revealAndCopy();

    expect(mockShareApi.revealShareLink).toHaveBeenCalledWith(55);
    expect(component.shareLink.token).toBe('token-xyz-revealed');
    expect(component.copyUrl).toHaveBeenCalled();
  });

  it('4. should display share URL and platform share buttons when token is already present', () => {
    component.isOpen = true;
    component.shareLink = { ...mockShareLink, token: 'existing-valid-token', canReveal: true };
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const urlInput = compiled.querySelector('.url-text-field') as HTMLInputElement;
    expect(urlInput).toBeTruthy();
    expect(urlInput.value).toContain('existing-valid-token');
    expect(compiled.querySelector('.social-share-section')).toBeTruthy();
  });

  it('5. confirming revocation should call permissionApiService.revokePermission with shareLink.id', () => {
    component.isOpen = true;
    component.shareLink = { ...mockShareLink, id: 55 };
    const revokedSpy = spyOn(component.revoked, 'emit');

    component.confirmRevokeShareLink();

    expect(mockPermissionApi.revokePermission).toHaveBeenCalledWith(55);
    expect(revokedSpy).toHaveBeenCalled();
  });
});
