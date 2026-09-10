import { Injectable } from '@angular/core';
import { ProjectWorkspaceModel } from '../models/project-workspace.model';

/**
 * Service to persist user's last selected workspace per organization.
 * Canonical Key format: securedoc_active_ws_${userId}_${orgCode} (stores numeric workspaceId).
 * Legacy Key format: securedoc_subspace_pref_${userId}_${orgCode} (stored root folderId).
 */
@Injectable({ providedIn: 'root' })
export class SubspacePersistenceService {
  private readonly CANONICAL_PREFIX = 'securedoc_active_ws_';
  private readonly LEGACY_PREFIX = 'securedoc_subspace_pref_';

  private buildCanonicalKey(userId: string | null | undefined, orgCode: string | null | undefined): string {
    const uid = userId || 'anon';
    const scope = orgCode ? orgCode : 'personal';
    return `${this.CANONICAL_PREFIX}${uid}_${scope}`;
  }

  private buildLegacyKey(userId: string | null | undefined, workspaceId: string): string {
    const uid = userId || 'anon';
    return `${this.LEGACY_PREFIX}${uid}_${workspaceId}`;
  }

  private cleanStaleAnonKey(orgCode: string | null | undefined): void {
    try {
      const scope = orgCode ? orgCode : 'personal';
      localStorage.removeItem(`${this.CANONICAL_PREFIX}anon_${scope}`);
      localStorage.removeItem(`${this.LEGACY_PREFIX}anon_${scope}`);
    } catch {
      // Ignore storage errors
    }
  }

  /**
   * Resolves the persisted numeric workspace ID for a user and organization/personal space.
   * Handles safe translation from legacy root-folder IDs to canonical workspace IDs.
   * Stale or invalid preferences are safely ignored/purged.
   */
  getWorkspacePreference(
    userId: string | null | undefined,
    orgCode: string | null | undefined,
    availableWorkspaces: ProjectWorkspaceModel[]
  ): number | null {
    if (typeof window === 'undefined' || !window.localStorage) {
      return null;
    }

    if (!userId || userId === 'anon') {
      return null;
    }

    this.cleanStaleAnonKey(orgCode);

    try {
      // 1. Try reading canonical numeric workspace preference
      const canonicalVal = localStorage.getItem(this.buildCanonicalKey(userId, orgCode));
      if (canonicalVal) {
        const parsedId = Number(canonicalVal);
        if (!isNaN(parsedId) && availableWorkspaces.some((w) => w.id === parsedId && w.status === 'ACTIVE')) {
          return parsedId;
        }
        // Stale or unresolvable canonical key (e.g. database reset, workspace deleted or archived)
        if (availableWorkspaces && availableWorkspaces.length > 0) {
          this.removeWorkspacePreference(userId, orgCode);
        }
      }

      // 2. Fallback to legacy preference (which stored root folderId)
      const legacyScope = orgCode ? orgCode : 'personal';
      const legacyVal = localStorage.getItem(this.buildLegacyKey(userId, legacyScope));
      if (legacyVal) {
        const legacyFolderId = Number(legacyVal);
        if (!isNaN(legacyFolderId)) {
          // Translate ONLY when it matches an active workspace.rootFolderId
          const matchedWs = availableWorkspaces.find(
            (w) => w.rootFolderId === legacyFolderId && w.status === 'ACTIVE'
          );
          if (matchedWs) {
            // Auto-migrate: save canonical key and purge legacy key
            this.saveWorkspacePreference(userId, orgCode, matchedWs.id);
            this.removeLegacy(userId, legacyScope);
            return matchedWs.id;
          }
        }
        // Stale or unresolvable legacy key — clean it up to prevent corruption
        this.removeLegacy(userId, legacyScope);
      }
    } catch (err) {
      console.warn('Failed to read workspace preference from localStorage:', err);
    }

    return null;
  }

  /**
   * Saves the real numeric workspace ID.
   */
  saveWorkspacePreference(
    userId: string | null | undefined,
    orgCode: string | null | undefined,
    workspaceId: number
  ): void {
    if (typeof window === 'undefined' || !window.localStorage) {
      return;
    }

    if (!userId || userId === 'anon') {
      return;
    }

    this.cleanStaleAnonKey(orgCode);

    try {
      localStorage.setItem(this.buildCanonicalKey(userId, orgCode), workspaceId.toString());
    } catch (err) {
      console.warn('Failed to save workspace preference to localStorage:', err);
    }
  }

  /**
   * Removes canonical workspace preference.
   */
  removeWorkspacePreference(userId: string | null | undefined, orgCode: string | null | undefined): void {
    if (typeof window === 'undefined' || !window.localStorage) {
      return;
    }

    if (!userId || userId === 'anon') {
      return;
    }

    this.cleanStaleAnonKey(orgCode);

    try {
      localStorage.removeItem(this.buildCanonicalKey(userId, orgCode));
    } catch (err) {
      console.warn('Failed to remove workspace preference from localStorage:', err);
    }
  }

  private removeLegacy(userId: string | null | undefined, workspaceId: string): void {
    try {
      localStorage.removeItem(this.buildLegacyKey(userId, workspaceId));
    } catch {
      // ignore
    }
  }

  // ── @deprecated Legacy compatibility methods for unmigrated code ──────────
  get(userId: string | null | undefined, workspaceId: string): string | null {
    if (typeof window === 'undefined' || !window.localStorage) return null;
    try {
      return (
        localStorage.getItem(this.buildCanonicalKey(userId, workspaceId)) ||
        localStorage.getItem(this.buildLegacyKey(userId, workspaceId))
      );
    } catch {
      return null;
    }
  }

  save(userId: string | null | undefined, workspaceId: string, subspaceId: string): void {
    if (typeof window === 'undefined' || !window.localStorage) return;
    try {
      localStorage.setItem(this.buildLegacyKey(userId, workspaceId), subspaceId);
    } catch {
      // ignore
    }
  }

  remove(userId: string | null | undefined, workspaceId: string): void {
    if (typeof window === 'undefined' || !window.localStorage) return;
    try {
      localStorage.removeItem(this.buildCanonicalKey(userId, workspaceId));
      localStorage.removeItem(this.buildLegacyKey(userId, workspaceId));
    } catch {
      // ignore
    }
  }
}
