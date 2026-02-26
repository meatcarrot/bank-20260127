package com.example.bank.event;


import com.example.bank.service.TransferService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferConsumer {

    private final ObjectMapper objectMapper;
    private final TransferService transferService;

    @KafkaListener(
            topics = "transfer-topic",
            groupId = "transfer-group"
    )
    public void consume(String message) {
        try {
            log.info("카프카에서 메세지 수신: {}", message);

            // 1. JSON문자열을 다시 TransferEvent 객체로 역직렬화
            TransferEvent event = objectMapper.readValue(message, TransferEvent.class);

            // 2. 실제 DB 처리를 위해 서비스로 전달
            // (아직 서비스에 실제 처리 로직은 안 만들었으나, 추가할 예정)
            transferService.processTransfer(event);

        } catch(JsonProcessingException e){
            log.error("메세지 변환 실패", e);
        } catch (Exception e) {
            log.error("송금 처리 중 예상하지 못한 에러 발생", e);
        }
    }
}
