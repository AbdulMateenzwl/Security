package com.security.project.domain.task.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.security.project.domain.task.entity.TaskPriority;
import com.security.project.domain.task.entity.TaskStatus;

import jakarta.validation.constraints.Size;

/**
 * Partial update of a task. Every field is optional; a {@code null} field means "leave unchanged".
 * Each field that actually changes is recorded in the task's activity log.
 */
public record UpdateTaskRequest(
        @Size(max = 200)
        String title,

        String description,

        TaskStatus status,

        TaskPriority priority,

        UUID assignedToId,

        Instant dueDate,

        List<@Size(max = 50) String> labels
) {
}
