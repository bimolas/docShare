import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideEchartsCore } from 'ngx-echarts';
import { By } from '@angular/platform-browser';
import { DashboardActivityCardComponent } from './dashboard-activity-card.component';
import { ActivityTrendViewModel } from '../models/dashboard.model';

describe('DashboardActivityCardComponent', () => {
  let component: DashboardActivityCardComponent;
  let fixture: ComponentFixture<DashboardActivityCardComponent>;

  const sampleVm: ActivityTrendViewModel = {
    dates: ['2026-09-08', '2026-09-09', '2026-09-10'],
    counts: [5, 12, 7],
    totalActions: 24,
    peakCount: 12,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardActivityCardComponent],
      providers: [provideEchartsCore({ echarts: () => import('echarts') })],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardActivityCardComponent);
    component = fixture.componentInstance;
  });

  it('should render title and total actions badge', () => {
    fixture.componentRef.setInput('viewModel', sampleVm);
    fixture.componentRef.setInput('options', {});
    fixture.detectChanges();

    const titleEl = fixture.debugElement.query(By.css('h3'));
    expect(titleEl.nativeElement.textContent.trim()).toBe('14-Day Activity Velocity');

    const totalBadge = fixture.debugElement.query(By.css('.bg-amber-50'));
    expect(totalBadge.nativeElement.textContent).toContain('24 Total Actions');

    const peakBadge = fixture.debugElement.query(By.css('.bg-slate-100'));
    expect(peakBadge.nativeElement.textContent).toContain('Peak: 12/day');
  });

  it('should calculate daily average accurately in footer', () => {
    fixture.componentRef.setInput('viewModel', sampleVm);
    fixture.componentRef.setInput('options', {});
    fixture.detectChanges();

    expect(component.dailyAverage()).toBe('8.0');
    const footerEl = fixture.debugElement.query(By.css('.border-t'));
    expect(footerEl.nativeElement.textContent).toContain('8.0 actions/day');
  });
});
