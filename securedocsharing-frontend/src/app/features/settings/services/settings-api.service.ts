import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { UserMessagePrivacySettingsDto, WorkspacePolicyDto, WorkspacePolicyTarget } from '../interfaces/settings.interfaces';

@Injectable({
  providedIn: 'root'
})
export class SettingsApiService {
  private readonly http = inject(HttpClient);
  
  // DM Privacy URL
  private readonly dmPrivacyUrl = `${environment.apiBaseUrl}/v1/direct-messages/privacy`;

  getPrivacySettings(): Observable<UserMessagePrivacySettingsDto> {
    return this.http.get<UserMessagePrivacySettingsDto>(this.dmPrivacyUrl);
  }

  updatePrivacySettings(settings: UserMessagePrivacySettingsDto): Observable<UserMessagePrivacySettingsDto> {
    return this.http.put<UserMessagePrivacySettingsDto>(this.dmPrivacyUrl, settings);
  }

  // Workspace Policy URLs
  getWorkspacePolicy(target: WorkspacePolicyTarget): Observable<WorkspacePolicyDto> {
    this.assertValidPolicyTarget(target);
    return this.http.get<WorkspacePolicyDto>(`${environment.apiBaseUrl}/v1/workspaces/${target}/policies`);
  }

  updateWorkspacePolicy(target: WorkspacePolicyTarget, policy: WorkspacePolicyDto): Observable<WorkspacePolicyDto> {
    this.assertValidPolicyTarget(target);
    return this.http.put<WorkspacePolicyDto>(`${environment.apiBaseUrl}/v1/workspaces/${target}/policies`, policy);
  }

  private assertValidPolicyTarget(target: unknown): asserts target is WorkspacePolicyTarget {
    if (typeof target === 'number') {
      throw new Error(`Invalid policy target: ${target}. Numeric ProjectWorkspace IDs must never be passed to the policy API. Expected 'personal' or an orgCode.`);
    }
    if (!target || typeof target !== 'string' || !target.trim()) {
      throw new Error(`Policy target must be a non-empty string ('personal' or an orgCode), received: ${target}`);
    }
  }
}
