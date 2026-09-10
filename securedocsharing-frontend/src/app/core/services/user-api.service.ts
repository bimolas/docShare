import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { UserResponseDto, UserRequestDto } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class UserApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/users`;

  /**
   * Retrieves profile of current authenticated user.
   * Endpoint: GET /api/v1/users/me
   */
  getMyProfile(): Observable<UserResponseDto> {
    return this.http.get<UserResponseDto>(`${this.baseUrl}/me`);
  }

  /**
   * Updates profile of current authenticated user.
   * Endpoint: PUT /api/v1/users/me
   */
  updateMyProfile(payload: UserRequestDto): Observable<UserResponseDto> {
    return this.http.put<UserResponseDto>(`${this.baseUrl}/me`, payload);
  }

  /**
   * Searches for existing SecureDoc users by email, first name, or last name.
   * Endpoint: GET /api/v1/users/search?q={query}
   */
  searchUsers(query?: string): Observable<UserResponseDto[]> {
    let params = new HttpParams();
    if (query && query.trim()) {
      params = params.set('q', query.trim());
    }
    return this.http.get<UserResponseDto[]>(`${this.baseUrl}/search`, { params });
  }
}


