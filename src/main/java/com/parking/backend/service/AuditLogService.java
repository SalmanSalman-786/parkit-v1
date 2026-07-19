package com.parking.backend.service;

import com.parking.backend.model.*;
import com.parking.backend.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(
            String userId,
            String username,
            String name,
            AuditActorRole role,
            AuditAction action,
            String entityType,
            String entityId,
            String description,
            String ipAddress,
            boolean success) {

        AuditLog log = new AuditLog();

        log.setTimestamp(LocalDateTime.now());

        log.setUserId(userId);
        log.setUsername(username);
        log.setName(name);

        log.setRole(role);

        log.setAction(action);

        log.setEntityType(entityType);
        log.setEntityId(entityId);

        log.setDescription(description);

        log.setIpAddress(ipAddress);

        log.setSuccess(success);

        auditLogRepository.save(log);
    }

    public void logAnonymous(
            AuditActorRole role,
            AuditAction action,
            String username,
            String description,
            String ipAddress) {

        AuditLog log = new AuditLog();

        log.setTimestamp(LocalDateTime.now());

        log.setUserId("UNKNOWN");
        log.setUsername(username);
        log.setName("UNKNOWN");

        log.setRole(role);
        log.setAction(action);

        log.setEntityType("USER");
        log.setEntityId("UNKNOWN");

        log.setDescription(description);

        log.setIpAddress(ipAddress);

        log.setSuccess(false);

        auditLogRepository.save(log);
    }

}