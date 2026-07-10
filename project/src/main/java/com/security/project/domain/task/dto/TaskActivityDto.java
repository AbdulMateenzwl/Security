package com.security.project.domain.task.dto;

import java.time.Instant;
import java.util.UUID;

import com.security.project.domain.task.entity.TaskAction;
import com.security.project.domain.task.entity.TaskActivityLog;

/**
 * One entry in a task's activity trail.
 *
 * @param performedBy         actor's user id
 * @param performedByUsername actor's username
 */
public record TaskActivityDto(
        UUID id,
        TaskAction action,
        UUID performedBy,
        String performedByUsername,
        String oldValue,
        String newValue,
        Instant createdAt
) {
    /** Requires {@code log.getPerformedBy()} to be loaded (use the fetch-join query). */
    public static TaskActivityDto from(TaskActivityLog log) {
        return new TaskActivityDto(
                log.getId(),
                log.getAction(),
                log.getPerformedBy().getId(),
                log.getPerformedBy().getUsername(),
                log.getOldValue(),
                log.getNewValue(),
                log.getCreatedAt());
    }
}
