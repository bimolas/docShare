import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import {
  NotificationSettingsComponent,
  DEFAULT_NOTIFICATION_PREFERENCES,
} from './notification-settings.component';

describe('NotificationSettingsComponent', () => {
  let component: NotificationSettingsComponent;
  let fixture: ComponentFixture<NotificationSettingsComponent>;
  let router: Router;
  let navigateSpy: jasmine.Spy;

  beforeEach(async () => {
    localStorage.clear();

    await TestBed.configureTestingModule({
      imports: [NotificationSettingsComponent],
      providers: [
        {
          provide: Router,
          useValue: {
            navigate: jasmine.createSpy('navigate'),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(NotificationSettingsComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    navigateSpy = router.navigate as jasmine.Spy;
    fixture.detectChanges();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should initialize with default preferences when localStorage is empty', () => {
    expect(component.prefs()).toEqual(DEFAULT_NOTIFICATION_PREFERENCES);
    expect(component.prefs().showNavBadge).toBeTrue();
    expect(component.prefs().actionRequiredAlerts).toBeTrue();
    expect(component.prefs().autoMarkReadOnReview).toBeFalse();
  });

  it('should load saved preferences from localStorage', () => {
    localStorage.setItem(
      'securedoc_in_app_notification_preferences',
      JSON.stringify({ showNavBadge: false, autoMarkReadOnReview: true })
    );

    component.loadPreferences();
    expect(component.prefs().showNavBadge).toBeFalse();
    expect(component.prefs().autoMarkReadOnReview).toBeTrue();
    expect(component.prefs().actionRequiredAlerts).toBeTrue(); // preserved default
  });

  it('should update a preference and persist to localStorage', () => {
    component.updatePref('showNavBadge', false);
    expect(component.prefs().showNavBadge).toBeFalse();

    const stored = JSON.parse(localStorage.getItem('securedoc_in_app_notification_preferences') || '{}');
    expect(stored.showNavBadge).toBeFalse();
  });

  it('should reset preferences to defaults', () => {
    component.updatePref('showNavBadge', false);
    component.updatePref('autoMarkReadOnReview', true);

    component.resetDefaults();
    expect(component.prefs()).toEqual(DEFAULT_NOTIFICATION_PREFERENCES);
    expect(localStorage.getItem('securedoc_in_app_notification_preferences')).toBeNull();
  });

  it('should navigate to Notification Center on openNotificationCenter', () => {
    component.openNotificationCenter();
    expect(navigateSpy).toHaveBeenCalledWith(['/w', 'personal', 'notifications']);
  });
});
