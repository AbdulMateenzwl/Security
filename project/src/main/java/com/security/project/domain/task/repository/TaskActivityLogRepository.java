package com.security.project.domain.task.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.security.project.domain.task.entity.TaskActivityLog;

public interface TaskActivityLogRepository extends JpaRepository<TaskActivityLog, UUID> {

    /** A task's activity trail, newest first, with the actor eagerly loaded for its username. */
    @Query("""
            SELECT l FROM TaskActivityLog l
              JOIN FETCH l.performedBy
             WHERE l.task.id = :taskId
             ORDER BY l.createdAt DESC
            """)
    List<TaskActivityLog> findByTaskIdWithActor(@Param("taskId") UUID taskId);
}
