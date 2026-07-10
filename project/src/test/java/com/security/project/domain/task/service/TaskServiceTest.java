package com.security.project.domain.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.security.project.domain.chat.entity.Chat;
import com.security.project.domain.chat.repository.ChatRepository;
import com.security.project.domain.chat.service.ChatAccessGuard;
import com.security.project.domain.task.dto.CreateTaskRequest;
import com.security.project.domain.task.entity.Task;
import com.security.project.domain.task.entity.TaskAction;
import com.security.project.domain.task.entity.TaskActivityLog;
import com.security.project.domain.task.repository.TaskActivityLogRepository;
import com.security.project.domain.task.repository.TaskRepository;
import com.security.project.domain.user.entity.User;
import com.security.project.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock TaskRepository taskRepository;
    @Mock TaskActivityLogRepository activityLogRepository;
    @Mock ChatRepository chatRepository;
    @Mock ChatAccessGuard chatAccessGuard;
    @Mock UserRepository userRepository;

    @InjectMocks TaskService service;

    @Test
    void createTask_setsCreatorAndLogsCreatedActivity() {
        UUID actorId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        Chat chatRef = new Chat();
        chatRef.setId(chatId);

        when(chatRepository.existsById(chatId)).thenReturn(true);
        when(userRepository.getReferenceById(actorId)).thenReturn(actor);
        when(chatRepository.getReferenceById(chatId)).thenReturn(chatRef);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateTaskRequest req = new CreateTaskRequest("Write spec", null, null, null, null, null);
        service.createTask(actorId, chatId, req);

        // Regression guard: createdBy must be set (it was once forgotten, causing an NPE + NOT NULL breach).
        ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(saved.capture());
        assertThat(saved.getValue().getCreatedBy()).isSameAs(actor);

        // A CREATED activity entry is written automatically.
        ArgumentCaptor<TaskActivityLog> logged = ArgumentCaptor.forClass(TaskActivityLog.class);
        verify(activityLogRepository).save(logged.capture());
        assertThat(logged.getValue().getAction()).isEqualTo(TaskAction.CREATED);
        assertThat(logged.getValue().getNewValue()).isEqualTo("Write spec");
    }
}
