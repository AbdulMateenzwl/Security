package com.security.project.domain.task.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.security.project.domain.task.entity.Task;
import com.security.project.domain.task.entity.TaskStatus;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    /**
     * Tasks in a chat, newest first, with optional status / assignee filters. A {@code null} filter
     * matches everything.
     */
    @Query("""
            SELECT t FROM Task t
             WHERE t.chat.id = :chatId
               AND (:status IS NULL OR t.status = :status)
               AND (:assigneeId IS NULL OR t.assignedTo.id = :assigneeId)
             ORDER BY t.createdAt DESC
            """)
    List<Task> search(@Param("chatId") UUID chatId,
                      @Param("status") TaskStatus status,
                      @Param("assigneeId") UUID assigneeId);
}
