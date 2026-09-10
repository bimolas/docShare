import { ApplicationConfig, provideZoneChangeDetection, APP_INITIALIZER } from '@angular/core';
import { provideRouter, withComponentInputBinding, withViewTransitions } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideEchartsCore } from 'ngx-echarts';

import { firstValueFrom } from 'rxjs';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { KeycloakService } from './core/auth/keycloak.service';
import { AuthStore } from './state/auth/auth.store';

export function initializeKeycloak(keycloakService: KeycloakService, authStore: AuthStore) {
  return () => {
    console.log('[AUTH] initializeKeycloak APP_INITIALIZER START', Date.now(), typeof window !== 'undefined' ? window.location.href : '');
    return keycloakService.init()
      .then(async (authenticated) => {
        console.log('[AUTH] initializeKeycloak APP_INITIALIZER DONE, authenticated =', authenticated, Date.now(), typeof window !== 'undefined' ? window.location.href : '');
        if (authenticated) {
          try {
            await firstValueFrom(authStore.loadUserProfile());
          } catch (err) {
            console.error('[AUTH] Failed to initialize user profile during APP_INITIALIZER:', err);
          }
        }
      })
      .catch((err) => {
        console.error('[AUTH] initializeKeycloak APP_INITIALIZER ERROR:', err);
      });
  };
}

/**
 * appConfig: Root application configuration.
 * - Zone-based change detection (granular scheduling).
 * - Router with input binding and view transitions.
 * - HttpClient with functional interceptors.
 * - Keycloak OIDC APP_INITIALIZER bootstrap.
 * - Angular Animations provider.
 * - Lazy-loaded Apache ECharts Core provider.
 * No NgModules. All providers are tree-shakeable.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withViewTransitions()
    ),
    provideHttpClient(
      withFetch(),
      withInterceptors([authInterceptor, errorInterceptor])
    ),
    {
      provide: APP_INITIALIZER,
      useFactory: initializeKeycloak,
      multi: true,
      deps: [KeycloakService, AuthStore],
    },
    provideAnimations(),
    provideEchartsCore({ echarts: () => import('echarts') }),
  ],
};