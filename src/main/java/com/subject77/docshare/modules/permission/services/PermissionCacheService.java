package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCacheService {

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    private static final long DEFAULT_TTL_SECONDS = 300; // 5 minutes cache TTL

    public Optional<PermissionLevel> getCachedDocumentPermission(String userEmail, Long documentId) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null || userEmail == null || documentId == null) {
            return Optional.empty();
        }
        try {
            String key = buildDocKey(userEmail, documentId);
            String val = redisTemplate.opsForValue().get(key);
            if (val != null) {
                log.debug("Redis HIT for key {}", key);
                return Optional.of(PermissionLevel.valueOf(val));
            }
        } catch (Exception e) {
            log.warn("Redis error on getCachedDocumentPermission for user={}", userEmail, e);
        }
        return Optional.empty();
    }

    public void cacheDocumentPermission(String userEmail, Long documentId, PermissionLevel level, long ttlSeconds) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null || userEmail == null || documentId == null || level == null) {
            return;
        }
        try {
            String key = buildDocKey(userEmail, documentId);
            redisTemplate.opsForValue().set(key, level.name(), Duration.ofSeconds(ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS));
            log.debug("Redis SET for key {} -> {}", key, level);
        } catch (Exception e) {
            log.warn("Redis error on cacheDocumentPermission", e);
        }
    }

    public Optional<PermissionLevel> getCachedFolderPermission(String userEmail, Long folderId) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null || userEmail == null || folderId == null) {
            return Optional.empty();
        }
        try {
            String key = buildFolderKey(userEmail, folderId);
            String val = redisTemplate.opsForValue().get(key);
            if (val != null) {
                log.debug("Redis HIT for key {}", key);
                return Optional.of(PermissionLevel.valueOf(val));
            }
        } catch (Exception e) {
            log.warn("Redis error on getCachedFolderPermission", e);
        }
        return Optional.empty();
    }

    public void cacheFolderPermission(String userEmail, Long folderId, PermissionLevel level, long ttlSeconds) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null || userEmail == null || folderId == null || level == null) {
            return;
        }
        try {
            String key = buildFolderKey(userEmail, folderId);
            redisTemplate.opsForValue().set(key, level.name(), Duration.ofSeconds(ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS));
        } catch (Exception e) {
            log.warn("Redis error on cacheFolderPermission", e);
        }
    }

    public void evictUserPermissions(String userEmail) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null || userEmail == null) {
            return;
        }
        try {
            Set<String> keys = redisTemplate.keys("perm:user:" + userEmail.toLowerCase() + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Evicted {} Redis cache keys for user={}", keys.size(), userEmail);
            }
        } catch (Exception e) {
            log.warn("Redis error on evictUserPermissions", e);
        }
    }

    public void evictDocumentPermissions(Long documentId) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null || documentId == null) {
            return;
        }
        try {
            Set<String> keys = redisTemplate.keys("perm:user:*:doc:" + documentId);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Evicted {} Redis cache keys for documentId={}", keys.size(), documentId);
            }
        } catch (Exception e) {
            log.warn("Redis error on evictDocumentPermissions", e);
        }
    }

    public void evictAllPermissions() {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return;
        }
        try {
            Set<String> keys = redisTemplate.keys("perm:user:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Evicted {} Redis cache keys for all users", keys.size());
            }
        } catch (Exception e) {
            log.warn("Redis error on evictAllPermissions", e);
        }
    }

    private String buildDocKey(String userEmail, Long documentId) {
        return "perm:user:" + userEmail.toLowerCase() + ":doc:" + documentId;
    }

    private String buildFolderKey(String userEmail, Long folderId) {
        return "perm:user:" + userEmail.toLowerCase() + ":folder:" + folderId;
    }
}
