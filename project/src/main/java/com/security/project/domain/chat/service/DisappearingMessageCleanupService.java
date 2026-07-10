package com.security.project.domain.chat.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.security.project.domain.chat.repository.MessageRepository;

/**
 * Periodically purges expired disappearing messages.
 *
 * <p>History queries already hide messages past their {@code expires_at}; this job removes the rows
 * for good so ciphertext does not linger on disk. It runs on the cron in
 * {@code app.security.disappearing-message-cleanup-cron} and never logs message content.</p>
 */
@Service
public class DisappearingMessageCleanupService {

    private static final Logger log = LoggerFactory.getLogger(DisappearingMessageCleanupService.class);

    private final MessageRepository messageRepository;

    public DisappearingMessageCleanupService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Scheduled(cron = "${app.security.disappearing-message-cleanup-cron}")
    @Transactional
    public void deleteExpiredMessages() {
        int deleted = messageRepository.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("Disappearing-message cleanup removed {} expired message(s)", deleted);
        }
    }
}
