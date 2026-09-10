import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideEchartsCore } from 'ngx-echarts';
import { By } from '@angular/platform-browser';
import { DashboardStorageCardComponent } from './dashboard-storage-card.component';
import { StorageGaugeViewModel } from '../models/dashboard.model';

describe('DashboardStorageCardComponent', () => {
  let component: DashboardStorageCardComponent;
  let fixture: ComponentFixture<DashboardStorageCardComponent>;

  const sampleVm: StorageGaugeViewModel = {
    percentage: 12.5,
    usedBytes: 1342177280, // 1.25 GB
    limitBytes: 10737418240, // 10 GB
    formattedUsed: '1.25 GB',
    formattedLimit: '10 GB',
    accentColor: '#f97316',
    trackColor: '#f1f5f9',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardStorageCardComponent],
      providers: [provideEchartsCore({ echarts: () => import('echarts') })],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardStorageCardComponent);
    component = fixture.componentInstance;
  });

  it('should render capacity overview and metrics breakdown', () => {
    fixture.componentRef.setInput('viewModel', sampleVm);
    fixture.componentRef.setInput('options', {});
    fixture.detectChanges();

    const titleEl = fixture.debugElement.query(By.css('h3'));
    expect(titleEl.nativeElement.textContent.trim()).toBe('Capacity Overview');

    const usedEl = fixture.debugElement.query(By.css('.text-slate-800'));
    expect(usedEl.nativeElement.textContent).toContain('1.25 GB');

    const badgeEl = fixture.debugElement.query(By.css('.text-emerald-700'));
    expect(badgeEl.nativeElement.textContent).toContain('12.5% Used');
  });

  it('should indicate critical badge when percentage exceeds 90%', () => {
    fixture.componentRef.setInput('viewModel', {
      ...sampleVm,
      percentage: 92.0,
      accentColor: '#f43f5e',
    });
    fixture.componentRef.setInput('options', {});
    fixture.detectChanges();

    const badgeEl = fixture.debugElement.query(By.css('.bg-rose-50'));
    expect(badgeEl).toBeTruthy();
    expect(badgeEl.nativeElement.textContent).toContain('Critical');
  });
});
