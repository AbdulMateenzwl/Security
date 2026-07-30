package com.security.project.domain.task.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.security.project.domain.task.dto.CreateTaskRequest;
import com.security.project.domain.task.dto.DeleteCompletedTasksResponse;
import com.security.project.domain.task.dto.TaskActivityDto;
import com.security.project.domain.task.dto.TaskDto;
import com.security.project.domain.task.dto.UpdateTaskRequest;
import com.security.project.domain.task.entity.TaskStatus;
import com.security.project.domain.task.service.TaskService;
import com.security.project.domain.user.entity.User;

import jakarta.validation.Valid;

/**
 * Task-board endpoints. All require authentication; chat membership and creator/admin authorization
 * are enforced in {@link TaskService}.
 */
@RestController
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/api/chats/{chatId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDto create(@AuthenticationPrincipal User user,
                          @PathVariable UUID chatId,
                          @Valid @RequestBody CreateTaskRequest request) {
        return taskService.createTask(user.getId(), chatId, request);
    }

    @GetMapping("/api/chats/{chatId}/tasks")
    public List<TaskDto> list(@AuthenticationPrincipal User user,
                              @PathVariable UUID chatId,
                              @RequestParam(required = false) TaskStatus status,
                              @RequestParam(required = false) UUID assignee) {
        return taskService.listTasks(user.getId(), chatId, status, assignee);
    }

    @GetMapping("/api/chats/{chatId}/tasks/board")
    public Map<TaskStatus, List<TaskDto>> board(@AuthenticationPrincipal User user,
                                                @PathVariable UUID chatId) {
        return taskService.getBoard(user.getId(), chatId);
    }

    /**
     * Clear the completed (DONE) tasks in a chat. A chat ADMIN removes every completed task; a regular
     * member removes only the completed tasks they created. Returns how many were deleted.
     */
    @DeleteMapping("/api/chats/{chatId}/tasks/completed")
    public DeleteCompletedTasksResponse deleteCompleted(@AuthenticationPrincipal User user,
                                                        @PathVariable UUID chatId) {
        return taskService.deleteCompletedTasks(user.getId(), chatId);
    }

    @GetMapping("/api/tasks/{taskId}")
    public TaskDto get(@AuthenticationPrincipal User user, @PathVariable UUID taskId) {
        return taskService.getTask(user.getId(), taskId);
    }

    @PutMapping("/api/tasks/{taskId}")
    public TaskDto update(@AuthenticationPrincipal User user,
                          @PathVariable UUID taskId,
                          @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.updateTask(user.getId(), taskId, request);
    }

    @DeleteMapping("/api/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user, @PathVariable UUID taskId) {
        taskService.deleteTask(user.getId(), taskId);
    }

    @GetMapping("/api/tasks/{taskId}/activity")
    public List<TaskActivityDto> activity(@AuthenticationPrincipal User user, @PathVariable UUID taskId) {
        return taskService.getActivity(user.getId(), taskId);
    }
}
