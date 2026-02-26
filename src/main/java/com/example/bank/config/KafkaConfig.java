package com.example.bank.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    public static final String TRANSFER_TOPIC = "transfer-topic";

    //"우체국에 새로운 사물함 만들기"
    @Bean
    public NewTopic transferTopic() {
        return TopicBuilder.name(TRANSFER_TOPIC) // 이름은 "transfer-topic"으로 하고
                .partitions(3)                  // 3개의 분산 통로를 만들어줘 (나중에 속도 향상!)
                .replicas(1)                    // 복사본은 1개만 (테스트용이니까)
                .build();
    }
    // 왜 하나요? 애플리케이션이 켜질 때 카프카 서버에 접속해서
    // "송금 데이터 담을 방 하나 만들어놔!"라고 미리 주문을 넣는 겁니다.


    // "자바 언어를 카프카 언어로 번역하는 통역사"
    // 자바 객체 -> JSON으로
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 💡 핵심: "2026-02-25" 같은 날짜 형식을 이해할 수 있게 특수 모듈을 끼워넣음
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        // 날짜를 [2026, 2, 25] 이런 숫자 배열이 아니라 "2026-02-25..." 예쁜 글자로 바꿔줘
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
    // 왜 하나요? 우리가 보낼 데이터에 LocalDateTime이 들어있어서 그렇습니다.
    // 이 설정이 없으면 카프카에 날짜를 보낼 때 에러가 나거나 아주 이상한 숫자로
    // 찍히게 됩니다.


    // "우편물 발송 규정 정의하기"
    // String -> byte로
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        // 1. 어디로 보낼까? (카프카 서버 주소)
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        // 2. 메시지 머리표(Key)는 어떤 형식? (글자니까 String)
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // 3. 메시지 내용(Value)은 어떤 형식? (JSON 문자열로 보낼 거니까 String)
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        return new DefaultKafkaProducerFactory<>(config);
    }
    // 핵심 개념 (Serializer): '직렬화'라고 부릅니다.
    // 자바 세상의 데이터를 카프카 세상의 '0과 1'의 흐름으로 변환하는 포장지라고
    // 생각하세요.
    // 우리는 둘 다 **StringSerializer(글자 포장지)**를 선택했습니다.

    // 실제로 메시지를 쏘는 도구
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        // 위에서 만든 공장(producerFactory)에서 발송기를 찍어내서 스프링에게 전달!
        return new KafkaTemplate<>(producerFactory());
    }
    // 왜 하나요? 이 녀석이 있어야 우리가 TransferProducer에서
    // kafkaTemplate.send()라는 명령어를 쓸 수 있습니다.
    // 스프링이 관리하는 공용 발송 도구라고 보시면 됩니다.


    // "우편물 수령 및 해독 규정 정의하기"
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> config = new HashMap<>();

        // 1. 어디서 가져올까? (카프카 서버 주소)
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

        // 2. 내 이름표(명찰)는 무엇인가? (Consumer Group ID)
        // 이 이름이 같아야 카프카가 "아, 님이 어제 거기까지 읽었지!"라고 기억해줍니다.
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "transfer-group");

        // 3. 메시지 머리표(Key) 해독기 (포장 풀기: Deserializer)
        // 보낼 때 StringSerializer로 포장했으니, 받을 때도 String으로 풀어야 합니다.
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        // 4. 메시지 내용(Value) 해독기
        // 보낼 때 JSON 글자로 보냈으니, 받을 때도 일단 글자(String)로 읽어옵니다.
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        return new DefaultKafkaConsumerFactory<>(config);
    }

    // "실제로 메시지를 귀 기울여 듣는 일꾼 관리소"
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();

        // 위에서 만든 수령 규정(consumerFactory)을 일꾼들에게 교육시킵니다.
        factory.setConsumerFactory(consumerFactory());

        return factory;
    }
    // 왜 하나요?
    // Producer는 우리가 원할 때 '발송'만 하면 되지만,
    // Consumer는 카프카 서버를 24시간 내내 '감시'하고 있어야 합니다.
    // 이 설정이 있어야 @KafkaListener라는 어노테이션이 달린 메서드에
    // 스프링이 '전담 일꾼'을 배치해서 실시간으로 메시지를 낚아챌 수 있습니다.


}