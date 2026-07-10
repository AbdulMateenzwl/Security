package com.security.project.domain.chat.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.security.project.domain.chat.entity.Chat;

public interface ChatRepository extends JpaRepository<Chat, UUID> {
}
