package com.security.project.domain.task.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.security.project.domain.chat.entity.ChatMember;
import com.security.project.domain.chat.entity.MemberRole;
import com.security.project.domain.chat.service.ChatAccessGuard;
import com.security.project.domain.chat.repository.ChatRepository;
import com.security.project.domain.task.dto.CreateTaskRequest;
import com.security.project.domain.task.dto.DeleteCompletedTasksResponse;
import com.security.project.domain.task.dto.TaskActivityDto;
import com.security.project.domain.task.dto.TaskDto;
import com.security.project.domain.task.dto.UpdateTaskRequest;
import com.security.project.domain.task.entity.Task;
import com.security.project.domain.task.entity.TaskAction;
import com.security.project.domain.task.entity.TaskActivityLog;
import com.security.project.domain.task.entity.TaskPriority;
import com.security.project.domain.task.entity.TaskStatus;
import com.security.project.domain.task.repository.TaskActivityLogRepository;
import com.security.project.domain.task.repository.TaskRepository;
import com.security.project.domain.user.entity.User;
import com.security.project.domain.user.repository.UserRepository;
import com.security.project.exception.BadRequestException;
import com.security.project.exception.ResourceNotFoundException;

/**
 * Task boards scoped to a chat, with an automatic activity trail.
 *
 * <p>Authorization is enforced here: any chat member may create/read/update tasks and assign them to
 * other members; only the task's creator or a chat ADMIN may delete it. Non-members get 403 without
 * learning whether the chat or task exists. Every mutation writes a {@link TaskActivityLog} entry.</p>
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final TaskActivityLogRepository activityLogRepository;
    private final ChatRepository chatRepository;
    private final ChatAccessGuard chatAccessGuard;
    private final UserRepository userRepository;

    public TaskService(TaskRepository taskRepository,
                       TaskActivityLogRepository activityLogRepository,
                       ChatRepository chatRepository,
                       ChatAccessGuard chatAccessGuard,
                       UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.activityLogRepository = activityLogRepository;
        this.chatRepository = chatRepository;
        this.chatAccessGuard = chatAccessGuard;
        this.userRepository = userRepository;
    }

    @Transactional
    public TaskDto createTask(UUID actorId, UUID chatId, CreateTaskRequest req) {
        chatAccessGuard.requireMember(chatId, actorId);
        if (!chatRepository.existsById(chatId)) {
            throw new ResourceNotFoundException("Chat not found");
        }
        User actor = userRepository.getReferenceById(actorId);

        Task task = new Task();
        task.setChat(chatRepository.getReferenceById(chatId));
        task.setCreatedBy(actor);
        task.setTitle(req.title().trim());
        task.setDescription(req.description());
        task.setPriority(req.priority() == null ? TaskPriority.MEDIUM : req.priority());
        task.setStatus(TaskStatus.TODO);
        if (req.dueDate() != null) {
            task.setDueDate(req.dueDate());
        }
        if (req.labels() != null) {
            task.setLabels(new ArrayList<>(req.labels()));
        }
        User assignee = null;
        if (req.assignedToId() != null) {
            assignee = requireChatMemberUser(chatId, req.assignedToId());
            task.setAssignedTo(assignee);
        }
        Task saved = taskRepository.save(task);

        logActivity(saved, actor, TaskAction.CREATED, null, saved.getTitle());
        if (assignee != null) {
            logActivity(saved, actor, TaskAction.ASSIGNED, null, assignee.getUsername());
        }
        log.info("Task id={} created in chat id={} by user id={}", saved.getId(), chatId, actorId);
        return TaskDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<TaskDto> listTasks(UUID actorId, UUID chatId, TaskStatus status, UUID assigneeId) {
        chatAccessGuard.requireMember(chatId, actorId);
        return taskRepository.search(chatId, status, assigneeId).stream()
                .map(TaskDto::from)
                .toList();
    }

    /** Kanban board: tasks grouped by status, with every column present (even if empty). */
    @Transactional(readOnly = true)
    public Map<TaskStatus, List<TaskDto>> getBoard(UUID actorId, UUID chatId) {
        chatAccessGuard.requireMember(chatId, actorId);
        Map<TaskStatus, List<TaskDto>> board = new LinkedHashMap<>();
        for (TaskStatus s : TaskStatus.values()) {
            board.put(s, new ArrayList<>());
        }
        for (Task t : taskRepository.search(chatId, null, null)) {
            board.get(t.getStatus()).add(TaskDto.from(t));
        }
        return board;
    }

    @Transactional(readOnly = true)
    public TaskDto getTask(UUID actorId, UUID taskId) {
        Task task = loadTask(taskId);
        chatAccessGuard.requireMember(task.getChat().getId(), actorId);
        return TaskDto.from(task);
    }

    @Transactional(readOnly = true)
    public List<TaskActivityDto> getActivity(UUID actorId, UUID taskId) {
        Task task = loadTask(taskId);
        chatAccessGuard.requireMember(task.getChat().getId(), actorId);
        return activityLogRepository.findByTaskIdWithActor(taskId).stream()
                .map(TaskActivityDto::from)
                .toList();
    }

    /** Apply a partial update, logging each field that actually changes. */
    @Transactional
    public TaskDto updateTask(UUID actorId, UUID taskId, UpdateTaskRequest req) {
        Task task = loadTask(taskId);
        UUID chatId = task.getChat().getId();
        chatAccessGuard.requireMember(chatId, actorId);
        User actor = userRepository.getReferenceById(actorId);

        if (req.title() != null && !req.title().isBlank() && !req.title().equals(task.getTitle())) {
            logActivity(task, actor, TaskAction.TITLE_CHANGED, task.getTitle(), req.title().trim());
            task.setTitle(req.title().trim());
        }
        if (req.description() != null && !Objects.equals(req.description(), task.getDescription())) {
            logActivity(task, actor, TaskAction.DESCRIPTION_CHANGED, task.getDescription(), req.description());
            task.setDescription(req.description());
        }
        if (req.status() != null && req.status() != task.getStatus()) {
            logActivity(task, actor, TaskAction.STATUS_CHANGED, task.getStatus().name(), req.status().name());
            task.setStatus(req.status());
        }
        if (req.priority() != null && req.priority() != task.getPriority()) {
            logActivity(task, actor, TaskAction.PRIORITY_CHANGED, task.getPriority().name(), req.priority().name());
            task.setPriority(req.priority());
        }
        if (req.dueDate() != null && !Objects.equals(req.dueDate(), task.getDueDate())) {
            String old = task.getDueDate() == null ? null : task.getDueDate().toString();
            logActivity(task, actor, TaskAction.DUE_DATE_CHANGED, old, req.dueDate().toString());
            task.setDueDate(req.dueDate());
        }
        if (req.assignedToId() != null) {
            UUID currentAssignee = task.getAssignedTo() == null ? null : task.getAssignedTo().getId();
            if (!req.assignedToId().equals(currentAssignee)) {
                User newAssignee = requireChatMemberUser(chatId, req.assignedToId());
                String old = task.getAssignedTo() == null ? null : task.getAssignedTo().getUsername();
                logActivity(task, actor, TaskAction.ASSIGNED, old, newAssignee.getUsername());
                task.setAssignedTo(newAssignee);
            }
        }
        if (req.labels() != null) {
            task.setLabels(new ArrayList<>(req.labels()));
        }
        log.info("Task id={} updated by user id={}", taskId, actorId);
        return TaskDto.from(task);
    }

    /** Delete a task — creator or chat ADMIN only. */
    @Transactional
    public void deleteTask(UUID actorId, UUID taskId) {
        Task task = loadTask(taskId);
        requireCreatorOrAdmin(task, actorId);
        // The activity log cascade-deletes with the task, so deletion is recorded in the app log only.
        taskRepository.delete(task);
        log.info("Task id={} deleted by user id={}", taskId, actorId);
    }

    /**
     * Bulk-clear the completed ({@link TaskStatus#DONE}) tasks of a chat, returning how many were
     * removed. Applies the same authorization as single-task deletion, per task: a chat ADMIN clears
     * every completed task, while a regular member clears only the completed tasks they created.
     * Non-members get 403. Activity logs and labels cascade-delete with each task at the DB level.
     */
    @Transactional
    public DeleteCompletedTasksResponse deleteCompletedTasks(UUID actorId, UUID chatId) {
        ChatMember membership = chatAccessGuard.requireMember(chatId, actorId);
        boolean isAdmin = membership.getRole() == MemberRole.ADMIN;

        List<Task> completed = taskRepository.search(chatId, TaskStatus.DONE, null);
        List<Task> deletable = completed.stream()
                .filter(t -> isAdmin || t.getCreatedBy().getId().equals(actorId))
                .toList();

        if (!deletable.isEmpty()) {
            taskRepository.deleteAll(deletable);
        }
        log.info("Cleared {} completed task(s) from chat id={} by user id={}",
                deletable.size(), chatId, actorId);
        return new DeleteCompletedTasksResponse(deletable.size());
    }

    // --- internals ---------------------------------------------------------

    private void logActivity(Task task, User actor, TaskAction action, String oldValue, String newValue) {
        TaskActivityLog entry = new TaskActivityLog();
        entry.setTask(task);
        entry.setPerformedBy(actor);
        entry.setAction(action);
        entry.setOldValue(oldValue);
        entry.setNewValue(newValue);
        activityLogRepository.save(entry);
    }

    private Task loadTask(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
    }

    private void requireCreatorOrAdmin(Task task, UUID actorId) {
        ChatMember membership = chatAccessGuard.requireMember(task.getChat().getId(), actorId);
        boolean isCreator = task.getCreatedBy().getId().equals(actorId);
        if (!isCreator && membership.getRole() != MemberRole.ADMIN) {
            throw new AccessDeniedException("Only the task creator or a chat admin may delete this task");
        }
    }

    /** Verify the user is a member of the chat and return them (for assignment). */
    private User requireChatMemberUser(UUID chatId, UUID userId) {
        if (!chatAccessGuard.isMember(chatId, userId)) {
            throw new BadRequestException("Assignee is not a member of this chat");
        }
        return userRepository.getByIdOrThrow(userId);
    }
}
