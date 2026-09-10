import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { SharedWithMeResponse, SharedByMeResponse } from '../models/sharing.model';

@Injectable({ providedIn: 'root' })
export class SharingApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/v1/permissions`;

  getSharedWithMe(): Observable<SharedWithMeResponse> {
    return this.http.get<SharedWithMeResponse>(`${this.base}/shared-with-me`);
  }

  getSharedByMe(includeInactive = true): Observable<SharedByMeResponse> {
    return this.http.get<SharedByMeResponse>(`${this.base}/shared-by-me`, {
      params: { includeInactive: includeInactive.toString() },
    });
  }

  revokePermission(permissionId: number): Observable<any> {
    return this.http.delete<any>(`${this.base}/${permissionId}`);
  }
}
