package com.security.project.domain.task.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.security.project.domain.task.entity.Task;
import com.security.project.domain.task.entity.TaskPriority;
import com.security.project.domain.task.entity.TaskStatus;

/**
 * A task as returned to clients.
 *
 * @param assignedTo assignee user id, or null if unassigned
 * @param createdBy  creator user id
 */
public record TaskDto(
        UUID id,
        UUID chatId,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        UUID assignedTo,
        UUID createdBy,
        Instant dueDate,
        List<String> labels,
        Instant createdAt,
        Instant updatedAt
) {
    /** Must be called within an open session (touches the lazy {@code labels} collection). */
    public static TaskDto from(Task t) {
        return new TaskDto(
                t.getId(),
                t.getChat().getId(),
                t.getTitle(),
                t.getDescription(),
                t.getStatus(),
                t.getPriority(),
                t.getAssignedTo() == null ? null : t.getAssignedTo().getId(),
                t.getCreatedBy().getId(),
                t.getDueDate(),
                new ArrayList<>(t.getLabels()),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }
}
