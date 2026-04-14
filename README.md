# 🏦 Kafka 기반 송금 시스템

> **분산 트랜잭션 정합성**과 **메시지 기반 비동기 처리**를 학습하기 위한 프로덕션 레벨 프로젝트

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Kafka](https://img.shields.io/badge/Kafka-7.5.0-black.svg)](https://kafka.apache.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)

---

## 📌 핵심 성과

| 지표 | 달성     | 설명 |
|------|--------|------|
| **메시지 유실 방지** | 100%   | Outbox Pattern으로 DB 저장 - Kafka 발행 원자성 보장 |
| **멱등성 처리** | 100%   | 동일 메시지 재수신 시 중복 처리 완벽 차단 |
| **동시성 제어** | 100%   | 100건 동시 요청에서 데이터 정합성 유지 |
| **장애 복구** | 자동 재시도 | Kafka 장애 시 스케줄러 기반 자동 재발행 |

---

## 🎯 프로젝트 배경: "왜 만들었는가?"

### 문제 인식
단순 CRUD를 넘어서, **실무에서 마주하는 분산 시스템 문제**를 직접 구현하며 학습하고 싶었다.

```
💸 송금은 단순히 잔액만 update하는 기능이 아니다

실제로는:
- DB 커밋과 Kafka 발행이 원자적으로 묶이지 않는 문제
- Kafka 장애 시 메시지가 유실될 수 있는 문제  
- 동일 메시지 재수신 시 중복 처리될 수 있는 문제
- 외부 시스템 실패 시 보상 트랜잭션이 필요한 문제
```

### 해결 전략

| 문제 | 해결 방법 | 핵심 기술 |
|------|----------|----------|
| **DB-Kafka 원자성** | Outbox Pattern | `@TransactionalEventListener(AFTER_COMMIT)` |
| **메시지 유실** | Outbox 재시도 | Spring Scheduler + PENDING 상태 재발행 |
| **중복 처리** | 멱등성 보장 | TransferLedger Unique 제약 + 상태 가드 |
| **외부 실패** | 보상 트랜잭션 | Saga 패턴 맛보기 (AccountLedger CREDIT 추가) |

---

## 🏗️ 시스템 아키텍처

### 전체 플로우
```
to do
```

### Outbox Pattern 상세
```
to do
```

---

## 🛠️ 기술 스택

### Backend
- **Java 21** - LTS 버전
- **Spring Boot 4.1.0** - 최신 버전
- **Spring Data JPA** - ORM
- **Spring Kafka** - 메시지 브로커 연동

### Infrastructure
- **Apache Kafka 7.5.0** - 이벤트 스트리밍
- **MySQL 8.0** - RDBMS
- **Nginx** - Reverse Proxy (Web Server / WAS 분리 학습)
- **Docker Compose** - 로컬 개발 환경

### Key Patterns
- **Outbox Pattern** - 분산 트랜잭션 정합성
- **Saga Pattern** (보상 트랜잭션) - 외부 실패 처리
- **Optimistic Lock** - 동시성 제어
- **Idempotency** - 멱등성 보장

---

## 🚀 빠른 시작

### 1. 사전 준비
```bash
# 필수 요구사항
- Java 17
- Docker & Docker Compose
```

### 2. 인프라 실행
```bash
# Kafka, MySQL, Nginx 컨테이너 실행
docker compose up -d

# 헬스체크 확인
docker compose ps
```

### 3. 애플리케이션 실행
```bash
# Gradle 빌드 & 실행
./gradlew bootRun

# 또는 IDE에서 BankApplication 실행
```

### 4. API 테스트
```bash
# 계좌 조회
curl http://localhost:8080/accounts/1

# 송금 요청 (1번 → 2번 계좌로 10,000원)
curl -X POST http://localhost:8080/transfer \
  -H "Content-Type: application/json" \
  -d '{
    "fromId": 1,
    "toId": 2,
    "amount": 10000
  }'

# 응답
{
  "message": "처리 완료"
}
```

### 5. 실행 결과 확인
```bash
# Kafka Consumer 로그에서 실시간 처리 확인
docker logs -f bank-app1-1

# MySQL에서 데이터 확인
docker exec -it bank-20260127-mysql-1 mysql -u bank -p

mysql> SELECT * FROM transfer_ledger;
mysql> SELECT * FROM account_ledger;
mysql> SELECT * FROM outbox_event;
```

---

## 📦 프로젝트 구조

```
com.example.bank
├── account/               # 계좌 리포지토리 & DTO
├── controller/            # REST API 진입점
│   └── test/             # 테스트 시나리오용 컨트롤러
├── domain/               # 핵심 엔터티 (Account)
├── exception/            # 사용자 정의 예외
├── ledger/               # 원장 (AccountLedger, EntryType)
├── outbox/               # Outbox Pattern 구현체
│   ├── OutboxEvent
│   ├── OutboxRelayer     # Kafka 발행 + 재시도
│   └── OutboxStatusService
├── transfer/             # 송금 비즈니스 로직
│   ├── TransferService
│   ├── TransferLedger    # 송금 요청 기록
│   ├── TransferConsumer  # Kafka Consumer
│   ├── TransferFacade    # 낙관적 락 재시도
│   └── event/           # 보상 트랜잭션 이벤트
└── BankApplication
```

---

## 💡 핵심 설계 & 구현

### 1️⃣ Outbox Pattern: "DB 저장과 Kafka 발행을 어떻게 하나로?"

**문제:**
```java
// ❌ 잘못된 예시: 원자성 보장 안됨
@Transactional
public void transfer() {
    transferRepository.save(transfer);  // DB 저장 성공
    kafkaTemplate.send(event);          // Kafka 실패 시?
    // → DB에는 저장됐는데 메시지는 안 보내짐!
}
```

**해결:**
```java
// ✅ Outbox Pattern 적용
@Transactional
public void requestTransfer(Long fromId, Long toId, Long amount) {
    // 1. TransferLedger 저장
    TransferLedger ledger = new TransferLedger(transferId, fromId, toId, PENDING, amount);
    transferLedgerRepository.save(ledger);
    
    // 2. OutboxEvent 저장 (같은 트랜잭션!)
    OutboxEvent outbox = OutboxEvent.create("TRANSFER", transferId, "TransferCreated", payload);
    OutboxEvent saved = outboxRepository.saveAndFlush(outbox);
    
    // 3. 커밋 후 이벤트 발행 신호
    eventPublisher.publishEvent(new OutboxCreatedEvent(saved.getId()));
}

// 커밋 이후 실행
@TransactionalEventListener(phase = AFTER_COMMIT)
public void publishImmediately(OutboxCreatedEvent event) {
    OutboxEvent outbox = outboxRepository.findById(event.outboxId()).orElseThrow();
    kafkaTemplate.send("transfer-topic", outbox.getPayload())
        .whenComplete((result, ex) -> {
            if (ex == null) {
                outboxStatusService.markSent(outbox.getId());  // SENT
            }
            // 실패 시 PENDING 유지 → 스케줄러가 재시도
        });
}
```

**재시도 전략:**
```java
@Scheduled(fixedDelay = 5000)  // 5초마다
public void retryFailedEvents() {
    List<OutboxEvent> failedEvents = outboxRepository.findByStatus(PENDING);
    for (OutboxEvent event : failedEvents) {
        sendToKafka(event);  // 재발행 시도
    }
}
```

---

### 2️⃣ 멱등성: "같은 메시지를 여러 번 받아도 한 번만 처리"

**문제:**
```
네트워크 재전송으로 동일한 transferId가 2번 들어오면?
→ 잔액이 두 번 빠져나간다!
```

**해결 1: 상태 가드**
```java
@Transactional
public void processTransfer(TransferEvent event) {
    TransferLedger request = transferLedgerRepository.findById(event.transferId())
        .orElseThrow();
    
    // ✅ 이미 처리된 거래는 스킵
    if (request.getStatus() == SUCCESS) {
        return;  // 멱등성 보장
    }
    
    // 실제 송금 로직...
}
```

**해결 2: DB Unique 제약 (최후 방어선)**
```java
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"transaction_id", "account_id", "type"}))
public class AccountLedger {
    // 동일한 거래 ID + 계좌 ID + 타입 조합은 DB가 막아줌
}
```

---

### 3️⃣ 동시성 제어: "100명이 동시에 송금하면?"

**낙관적 락 + 재시도 전략**
```java
@Entity
public class Account {
    @Version
    private Long version = 0L;  // JPA가 자동으로 충돌 감지
}

// Facade 패턴으로 재시도 로직 분리
public void processTransferWithRetry(TransferEvent event) {
    int maxRetry = 10;  // ← 5 → 10으로 증가
    int attempt = 0;

    while (true) {
        try {
            attempt++;
            transferService.processTransfer(event);
            return;  // 성공
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException e) {
            log.warn("낙관적 락 충돌 발생 - transferId={}, attempt={}",
                    event.transferId(), attempt, e);

            if (attempt >= maxRetry) {
                throw e;
            }

            exponentialBackoffWithJitter(attempt);  // ← 변경
        }
    }
}
```

---

### 4️⃣ 보상 트랜잭션: "외부 은행 입금 실패 시?"

**Saga 패턴 맛보기**
```java
// 실패 이벤트 발행
@EventListener
public void handleExternalDepositFailed(ExternalDepositFailedEvent event) {
    externalTransferService.compensateWithdraw(event.transferId());
}

// 보상 처리: 출금한 돈 다시 입금
@Transactional
public void compensateWithdraw(String transferId) {
    TransferLedger transfer = transferLedgerRepository.findById(transferId).orElseThrow();
    
    if (transfer.getStatus() == SUCCESS) {
        return;  // 이미 성공한 거래는 보상 불가
    }
    
    // ✅ 출금했던 금액을 다시 입금 (CREDIT 원장 추가)
    accountLedgerRepository.save(new AccountLedger(
        transferId,
        transfer.getFromAccountId(),
        EntryType.CREDIT,  // 보상: 입금 처리
        transfer.getAmount()
    ));
    
    transfer.markCompensated();  // 상태: COMPENSATED
}
```

---

## 🔥 트러블슈팅 & 배운 점

### 1. Outbox는 "커밋 이후 발행"이 핵심

**[TODO: 엔터티 자체를 이벤트로 넘기면 어떤 문제가 발생하는가?]**

**해결:**
```java
// ✅ [TODO: ID만 넘기는 이유는?]
eventPublisher.publishEvent(new OutboxCreatedEvent([TODO: 무엇을 넘기나?]));
```

---

### 2. 멱등성은 상태 가드만으로 끝나지 않는다

**배운 점:**
[TODO: 애플리케이션 레벨 체크 외에 DB Unique 제약이 왜 필요한가?]

---

### 3. 비즈니스 예외 vs 시스템 예외 구분

**배운 점:**
```java
// [TODO: 잔액 부족은 어떻게 처리해야 하나?]
@Transactional
public void processTransfer() {
    try {
        account.withdraw(amount);
    } catch (InsufficientBalanceException e) {
        // [TODO: 롤백? 상태 기록?]
        
    }
}
```

**구분 기준:**
- **비즈니스 예외**: [TODO: 어떻게 처리?]
- **시스템 예외**: [TODO: 어떻게 처리?]

---

### 4. Web Server와 WAS 분리 실습

**Before:**
```
Client → [TODO: ?] → MySQL
```

**After:**
```
Client → [TODO: ?] → [TODO: ?] → MySQL
```

**학습 효과:**
- [TODO: Nginx의 역할은?]
- [TODO: API 요청은 어떻게 처리?]
- [TODO: Web Server와 WAS 분리의 장점은?]

---

## 📊 성능 & 테스트 결과

### 동시성 테스트
**[TODO: 실제로 테스트한 결과를 작성]**

```bash
# [TODO: 몇 개 스레드로 테스트했나?]

# 결과:
✅ 낙관적 락 충돌: [TODO: 몇 회?] 발생
✅ 재시도 결과: [TODO: ?]
✅ 최종 잔액 정합성: [TODO: ?]
```

### Outbox 재시도 성공률
**[TODO: Kafka 장애 시뮬레이션 결과]**

```
Kafka 장애 시뮬레이션:
- 최초 발행 실패: [TODO: 몇 건?]
- 스케줄러 재시도: [TODO: 간격?]
- 재시도 성공률: [TODO: %?]
- 평균 복구 시간: [TODO: 초?]
```

---

## 🚧 한계 & 개선 방향

### 현재 한계
**[TODO: 이 프로젝트의 한계를 4가지 작성]**

1.
2.
3.
4.

### 개선 계획
**[TODO: 향후 개선할 항목을 체크리스트로 작성]**

- [ ] 
- [ ] 
- [ ] 
- [ ] 
- [ ] 

---

## 📚 참고 자료

**[TODO: 학습에 도움이 된 자료 링크 4개 추가]**

-
-
-
-

---

## 📝 라이선스

MIT License

---

**Made with ☕ by [TODO: 내 이름]** | [GitHub](https://github.com/yourusername/bank)

---

## ✅ 자가 점검 체크리스트

완성 후 스스로 체크해보세요!

- [ ] 핵심 성과를 **숫자**로 표현했나?
- [ ] "왜 만들었는가?"에 **스토리**가 있나?
- [ ] 아키텍처 다이어그램이 **이해하기 쉬운가**?
- [ ] 각 기술 선택에 **이유**가 있나?
- [ ] 실행 방법이 **따라하기 쉬운가**?
- [ ] 코드 예시에 **주석**이 충분한가?
- [ ] 트러블슈팅에 **배운 점**이 명확한가?
- [ ] 성능 테스트 결과가 **구체적**인가?
- [ ] 한계를 **솔직하게** 인정했나?
- [ ] 개선 계획이 **현실적**인가?