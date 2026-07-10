package com.security.project.domain.task.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.security.project.domain.task.entity.TaskPriority;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create a task in a chat.
 *
 * @param title        required, 1–200 chars
 * @param description  optional free text
 * @param priority     optional; defaults to MEDIUM
 * @param assignedToId optional assignee (must be a member of the same chat)
 * @param dueDate      optional due date
 * @param labels       optional labels
 */
public record CreateTaskRequest(
        @NotBlank
        @Size(max = 200)
        String title,

        String description,

        TaskPriority priority,

        UUID assignedToId,

        Instant dueDate,

        List<@Size(max = 50) String> labels
) {
}
