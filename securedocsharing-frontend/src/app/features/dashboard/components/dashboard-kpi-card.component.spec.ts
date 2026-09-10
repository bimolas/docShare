import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { DashboardKpiCardComponent } from './dashboard-kpi-card.component';

describe('DashboardKpiCardComponent', () => {
  let component: DashboardKpiCardComponent;
  let fixture: ComponentFixture<DashboardKpiCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardKpiCardComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardKpiCardComponent);
    component = fixture.componentInstance;
  });

  it('should render title and formatted numerical value', () => {
    fixture.componentRef.setInput('title', 'Total Documents');
    fixture.componentRef.setInput('value', 1250);
    fixture.componentRef.setInput('icon', 'file-text');
    fixture.detectChanges();

    const titleEl = fixture.debugElement.query(By.css('span'));
    expect(titleEl.nativeElement.textContent.trim()).toBe('Total Documents');

    const valueEl = fixture.debugElement.query(By.css('.text-2xl'));
    expect(valueEl.nativeElement.textContent.trim()).toBe('1,250');
  });

  it('should render routerLink when provided', () => {
    fixture.componentRef.setInput('title', 'Workspaces');
    fixture.componentRef.setInput('value', 5);
    fixture.componentRef.setInput('icon', 'layers');
    fixture.componentRef.setInput('routerLink', '/w/personal/explorer');
    fixture.detectChanges();

    const linkEl = fixture.debugElement.query(By.css('a'));
    expect(linkEl).toBeTruthy();
    expect(linkEl.attributes['href']).toBe('/w/personal/explorer');
  });

  it('should render non-clickable container when routerLink is not provided', () => {
    fixture.componentRef.setInput('title', 'Folders');
    fixture.componentRef.setInput('value', 8);
    fixture.componentRef.setInput('icon', 'folder');
    fixture.detectChanges();

    const linkEl = fixture.debugElement.query(By.css('a'));
    expect(linkEl).toBeFalsy();

    const cardEl = fixture.debugElement.query(By.css('.p-5'));
    expect(cardEl).toBeTruthy();
  });
});
