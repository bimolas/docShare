import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SourceBadgeComponent } from './source-badge.component';
import { SharedItem } from '../models/sharing.model';

describe('SourceBadgeComponent', () => {
  let component: SourceBadgeComponent;
  let fixture: ComponentFixture<SourceBadgeComponent>;

  const baseItem: SharedItem = {
    permissionId: 1,
    resourceType: 'FOLDER',
    resourceId: 10,
    name: 'Test Resource',
    permissionType: 'ALLOW',
    permissionLevel: 'VIEW',
    sourceLabel: 'DIRECT SHARE',
    matchingGrantCount: 1,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SourceBadgeComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(SourceBadgeComponent);
    component = fixture.componentInstance;
  });

  it('should render Direct Share badge', () => {
    fixture.componentRef.setInput('item', { ...baseItem, sourceLabel: 'DIRECT SHARE' });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Direct Share');
    const badge = el.querySelector('.sd-source--direct');
    expect(badge).toBeTruthy();
  });

  it('should render Team Share badge with team name', () => {
    fixture.componentRef.setInput('item', {
      ...baseItem,
      sourceLabel: 'TEAM SHARE',
      targetGroupName: 'Marketing Group',
    });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Team: Marketing Group');
    const badge = el.querySelector('.sd-source--team');
    expect(badge).toBeTruthy();
  });

  it('should render Organization Share badge with org name or code', () => {
    fixture.componentRef.setInput('item', {
      ...baseItem,
      sourceLabel: 'ORGANIZATION SHARE',
      targetOrgName: 'Global Org',
      targetOrgCode: 'GLO',
    });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Org: Global Org');
    const badge = el.querySelector('.sd-source--organization');
    expect(badge).toBeTruthy();
  });

  it('should render Collaboration Share badge with collaboration name', () => {
    fixture.componentRef.setInput('item', {
      ...baseItem,
      sourceLabel: 'COLLABORATION SHARE',
      collaborationName: 'Synergy Project',
    });
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Collab: Synergy Project');
    const badge = el.querySelector('.sd-source--collaboration');
    expect(badge).toBeTruthy();
  });

  it('should render multi-grant pill when matchingGrantCount > 1', () => {
    fixture.componentRef.setInput('item', {
      ...baseItem,
      matchingGrantCount: 3,
    });
    fixture.detectChanges();

    const pill = fixture.nativeElement.querySelector('.multi-grant-pill');
    expect(pill).toBeTruthy();
    expect(pill.textContent).toContain('+2');
  });

  it('should emit multiGrantClicked when multi-grant pill is clicked', () => {
    const multiItem = {
      ...baseItem,
      matchingGrantCount: 3,
    };
    fixture.componentRef.setInput('item', multiItem);
    fixture.detectChanges();

    spyOn(component.multiGrantClicked, 'emit');
    const pill = fixture.nativeElement.querySelector('.multi-grant-pill');
    pill.click();

    expect(component.multiGrantClicked.emit).toHaveBeenCalledWith(multiItem);
  });
});
