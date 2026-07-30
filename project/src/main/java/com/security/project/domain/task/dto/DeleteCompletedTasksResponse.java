package com.security.project.domain.task.dto;

/** Result of clearing completed tasks from a chat: how many were deleted. */
public record DeleteCompletedTasksResponse(int deleted) {
}
