import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideEchartsCore } from 'ngx-echarts';
import { DashboardDistributionCardComponent } from './dashboard-distribution-card.component';
import { ContentDistributionItem } from '../models/dashboard.model';

describe('DashboardDistributionCardComponent', () => {
  let component: DashboardDistributionCardComponent;
  let fixture: ComponentFixture<DashboardDistributionCardComponent>;

  const sampleItems: ContentDistributionItem[] = [
    { label: 'Documents', count: 40, percentage: 67, color: '#f97316' },
    { label: 'Folders', count: 12, percentage: 20, color: '#0f172a' },
    { label: 'Direct Shares', count: 8, percentage: 13, color: '#10b981' },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardDistributionCardComponent],
      providers: [provideEchartsCore({ echarts: () => import('echarts') })],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardDistributionCardComponent);
    component = fixture.componentInstance;
  });

  it('should render title and total entities badge', () => {
    fixture.componentRef.setInput('items', sampleItems);
    fixture.componentRef.setInput('scope', 'PERSONAL');
    fixture.componentRef.setInput('options', {});
    fixture.componentRef.setInput('formattedValue', '2.4 GB');
    fixture.componentRef.setInput('percentage', 75);
    fixture.detectChanges();

    const titleEl = fixture.debugElement.query(By.css('h3'));
    expect(titleEl.nativeElement.textContent.trim()).toBe('Content & Storage Health');

    const totalBadge = fixture.debugElement.query(By.css('.bg-slate-100'));
    expect(totalBadge.nativeElement.textContent).toContain('60 Total Entities');

    const metricValue = fixture.debugElement.query(By.css('.text-2xl'));
    expect(metricValue.nativeElement.textContent).toContain('2.4 GB');
  });

  it('should render half chart and category cards when options are provided', () => {
    fixture.componentRef.setInput('items', sampleItems);
    fixture.componentRef.setInput('scope', 'PERSONAL');
    fixture.componentRef.setInput('options', {});
    fixture.detectChanges();

    const chartEl = fixture.debugElement.query(By.css('app-chart'));
    expect(chartEl).toBeTruthy();

    const legendCards = fixture.debugElement.queryAll(By.css('.bg-slate-50'));
    expect(legendCards.length).toBe(3);
    expect(legendCards[0].nativeElement.textContent).toContain('Documents');
    expect(legendCards[0].nativeElement.textContent).toContain('40');
    expect(legendCards[0].nativeElement.textContent).toContain('67%');
  });

  it('should render empty notice when options are not provided', () => {
    fixture.componentRef.setInput('items', []);
    fixture.componentRef.setInput('scope', 'PERSONAL');
    fixture.componentRef.setInput('options', null);
    fixture.detectChanges();

    const emptyText = fixture.debugElement.query(By.css('[data-testid="empty-notice"]'));
    expect(emptyText.nativeElement.textContent).toContain('No entities registered');
  });
});
