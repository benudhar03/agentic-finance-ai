package com.finance.ai.memory.model;

import com.finance.ai.crypto.EncryptedStringConverter;
import com.finance.ai.model.MessageRole;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation_messages", indexes = {
        @Index(name = "idx_conv_msg_conversation_id", columnList = "conversationId")
})
@Getter @Setter
@NoArgsConstructor
public class ConversationMessageAudit {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}