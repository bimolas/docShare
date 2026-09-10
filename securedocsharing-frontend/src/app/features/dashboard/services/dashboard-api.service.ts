import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  PersonalDashboardSummary,
  OrganizationDashboardSummary,
} from '../models/dashboard.model';

/**
 * DashboardApiService
 * Handles HTTP requests for the context-aware Dashboard domain.
 * Consumes the Personal and Organization summary endpoints provided by AnalyticsController.
 */
@Injectable({ providedIn: 'root' })
export class DashboardApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/v1/analytics/dashboard`;


  /**
   * Fetches the current user's personal dashboard summary.
   * Scoped to the authenticated user.
   */
  getPersonalSummary(): Observable<PersonalDashboardSummary> {
    return this.http.get<PersonalDashboardSummary>(`${this.base}/personal`);
  }

  /**
   * Fetches the organization dashboard summary for the specified organization.
   * Requires active membership in the organization or administrative privileges.
   *
   * @param orgCode Organization identifier code (e.g., "ACME")
   */
  getOrganizationSummary(orgCode: string): Observable<OrganizationDashboardSummary> {
    const params = new HttpParams().set('orgCode', orgCode.trim());
    return this.http.get<OrganizationDashboardSummary>(`${this.base}/organization`, { params });
  }
}
