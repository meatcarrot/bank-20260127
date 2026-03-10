    package com.example.bank.event;


    import com.example.bank.service.TransferService;
    import com.fasterxml.jackson.core.JsonProcessingException;
    import com.fasterxml.jackson.databind.ObjectMapper;
    import lombok.RequiredArgsConstructor;
    import lombok.extern.slf4j.Slf4j;
    import org.springframework.dao.DataIntegrityViolationException;
    import org.springframework.kafka.annotation.KafkaListener;
    import org.springframework.kafka.support.Acknowledgment;
    import org.springframework.stereotype.Component;
    import org.springframework.transaction.annotation.Transactional;

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
        public void consume(String message, Acknowledgment ack) {
            try {
                log.info("카프카에서 메세지 수신: {}", message);

                // 1. JSON문자열을 다시 TransferEvent 객체로 역직렬화
                TransferEvent event = objectMapper.readValue(message, TransferEvent.class);

                // 2. 실제 DB 처리를 위해 서비스로 전달
                // 핵심 비지니스 로직은 서비스에 위임한다
                transferService.processTransfer(event);

                // 3. 서비스가 에러 없이 끝나면 ACK
                ack.acknowledge();

            } catch(JsonProcessingException e){
                log.error("메세지 변환 실패", e);
            } catch(DataIntegrityViolationException e) {
                log.warn("이미 처리된 거래입니다: {}", e.getMessage());
                ack.acknowledge();
            }
            catch (Exception e) {
                log.error("송금 처리 중 예상하지 못한 에러 발생", e);
                throw e;
            }
        }
    }
