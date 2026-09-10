import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { DashboardActivityLedgerComponent } from './dashboard-activity-ledger.component';
import { DailyActivitySummary } from '../models/dashboard.model';

describe('DashboardActivityLedgerComponent', () => {
  let component: DashboardActivityLedgerComponent;
  let fixture: ComponentFixture<DashboardActivityLedgerComponent>;

  const sampleLedger: DailyActivitySummary[] = [
    { date: '2026-09-10', count: 18, intensity: 4 },
    { date: '2026-09-09', count: 7, intensity: 2 },
    { date: '2026-09-08', count: 0, intensity: 0 },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardActivityLedgerComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardActivityLedgerComponent);
    component = fixture.componentInstance;
  });

  it('should render title and audit trail link', () => {
    fixture.componentRef.setInput('ledger', sampleLedger);
    fixture.componentRef.setInput('scope', 'PERSONAL');
    fixture.detectChanges();

    const titleEl = fixture.debugElement.query(By.css('h3'));
    expect(titleEl.nativeElement.textContent.trim()).toBe('Activity Ledger');

    const linkEl = fixture.debugElement.query(By.css('a'));
    expect(linkEl.nativeElement.textContent).toContain('Audit Trail');
    expect(linkEl.attributes['href']).toBe('/w/personal/audit-trail');
  });

  it('should render chronological entries with monochrome black icon boxes and action counts', () => {
    fixture.componentRef.setInput('ledger', sampleLedger);
    fixture.componentRef.setInput('scope', 'ORGANIZATION');
    fixture.componentRef.setInput('orgCode', 'CORP1');
    fixture.detectChanges();

    const entries = fixture.debugElement.queryAll(By.css('.bg-slate-50\\/70'));
    expect(entries.length).toBe(3);

    // First entry (+18 actions)
    expect(entries[0].nativeElement.textContent).toContain('+18 actions');
    expect(entries[0].nativeElement.textContent).toContain('High Operational Volume');

    // Second entry (+7 actions)
    expect(entries[1].nativeElement.textContent).toContain('+7 actions');
    expect(entries[1].nativeElement.textContent).toContain('Standard File Activity');

    // Third entry (0 actions)
    expect(entries[2].nativeElement.textContent).toContain('0 actions');
    expect(entries[2].nativeElement.textContent).toContain('Routine System Check');

    // Icon containers must be neutral light background with dark icon
    const iconContainers = fixture.debugElement.queryAll(By.css('.bg-slate-100.text-slate-900'));
    expect(iconContainers.length).toBeGreaterThanOrEqual(3);
  });

  it('should render empty notice when ledger is empty', () => {
    fixture.componentRef.setInput('ledger', []);
    fixture.componentRef.setInput('scope', 'PERSONAL');
    fixture.detectChanges();

    const emptyText = fixture.debugElement.query(By.css('[data-testid="empty-ledger-notice"]'));
    expect(emptyText.nativeElement.textContent).toContain('No activity events recorded');
  });
});
