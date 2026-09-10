import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MultiGrantPopoverComponent } from './multi-grant-popover.component';
import { SharedItem } from '../models/sharing.model';

describe('MultiGrantPopoverComponent', () => {
  let component: MultiGrantPopoverComponent;
  let fixture: ComponentFixture<MultiGrantPopoverComponent>;

  const mockItem: SharedItem = {
    permissionId: 50,
    resourceType: 'DOCUMENT',
    resourceId: 200,
    name: 'Strategic_Plan.pdf',
    permissionType: 'ALLOW',
    permissionLevel: 'EDIT',
    grantedByName: 'Jane Director',
    grantedByUserEmail: 'jane@acme.com',
    grantedAt: '2026-03-01T10:00:00Z',
    sourceLabel: 'TEAM SHARE',
    sourceLabels: ['DIRECT SHARE', 'TEAM SHARE'],
    sourceDescription: 'Direct user share and Team Executive access.',
    matchingGrantCount: 2,
    matchedPermissionIds: [50, 51],
    targetGroupName: 'Executives',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MultiGrantPopoverComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(MultiGrantPopoverComponent);
    component = fixture.componentInstance;
  });

  it('should not render anything when open is false', () => {
    fixture.componentRef.setInput('item', mockItem);
    fixture.componentRef.setInput('open', false);
    fixture.detectChanges();

    const panel = fixture.nativeElement.querySelector('.popover-panel');
    expect(panel).toBeNull();
  });

  it('should render popover dialog when open is true', () => {
    fixture.componentRef.setInput('item', mockItem);
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    const panel = fixture.nativeElement.querySelector('.popover-panel');
    expect(panel).toBeTruthy();
    expect(panel.textContent).toContain('Access Provenance');
    expect(panel.textContent).toContain('Strategic_Plan.pdf');
    expect(panel.textContent).toContain('EDIT');
    expect(panel.textContent).toContain('2 contributing grants');
  });

  it('should emit closed when close button or backdrop is clicked', () => {
    fixture.componentRef.setInput('item', mockItem);
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    spyOn(component.closed, 'emit');

    const closeBtn = fixture.nativeElement.querySelector('.close-btn');
    closeBtn.click();
    expect(component.closed.emit).toHaveBeenCalled();

    const backdrop = fixture.nativeElement.querySelector('.popover-backdrop');
    backdrop.click();
    expect(component.closed.emit).toHaveBeenCalledTimes(2);
  });

  it('should emit manageAccess when Inspect Security button is clicked', () => {
    fixture.componentRef.setInput('item', mockItem);
    fixture.componentRef.setInput('open', true);
    fixture.detectChanges();

    spyOn(component.manageAccess, 'emit');

    const securityBtn = fixture.nativeElement.querySelector('.btn-primary');
    securityBtn.click();
    expect(component.manageAccess.emit).toHaveBeenCalledWith(mockItem);
  });
});
