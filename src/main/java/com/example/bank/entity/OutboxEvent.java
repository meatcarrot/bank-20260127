package com.example.bank.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter // 모든 필드에 Getter를 붙이는 게 관례상 편합니다.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String aggregateType;
    private String aggregateId;
    private String eventType;

    @Lob
    private String payload;

    @Enumerated(EnumType.STRING)
    private OutboxStatus status = OutboxStatus.PENDING;

    private LocalDateTime createdAt;

    public OutboxEvent(Long id) {
    }

    public void markAsSent(){
        this.status = OutboxStatus.SENT;
    }

    public static OutboxEvent create(String aggregateType, String aggregateId, String eventType, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.payload = payload;
        event.status = OutboxStatus.PENDING;
        event.createdAt = LocalDateTime.now();
        return event;
    }
}
