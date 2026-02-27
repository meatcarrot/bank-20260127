package com.example.bank.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper; // Spring이 자동으로 넣어주는 Jackson 도구

    public void send(TransferEvent event) {
        try {
            // 객체를 JSON 문자열로 변환
            String message = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(
                    "transfer-topic",
                    String.valueOf(event.fromAccountId()),
                    message
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("메시지 변환 실패!", e);
        }
    }
}