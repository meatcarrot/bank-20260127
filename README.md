# Kafka 기반 송금 시스템

Kafka, Outbox Pattern, Ledger 모델을 학습하기 위해 구현한 Spring Boot 기반 송금 시스템입니다.

## 프로젝트 소개

이 프로젝트는 계좌 간 송금을 주제로, 트랜잭션 정합성, 동시성 제어, 메시지 기반 비동기 처리, Outbox Pattern, Ledger 모델을 학습하기 위해 만든 백엔드 프로젝트입니다.

단순 송금 기능 구현을 넘어서 다음과 같은 상황까지 실험했습니다.

- Kafka 장애 상황에서의 메시지 유실 방지
- 동일 메시지 재수신에 대한 멱등성 보장
- 외부 송금 실패를 가정한 보상 트랜잭션(Saga 맛보기)

또한 기능이 늘어난 이후에는 1차 리팩토링을 통해 `transfer`, `ledger`, `outbox` 중심으로 패키지 구조를 정리했고, 리팩토링 이후에도 주요 시나리오 테스트가 정상 동작하는지 다시 검증했습니다.

## 해결하고 싶었던 문제

송금은 단순히 계좌 잔액만 update하는 기능이 아니라, 정합성, 재시도, 중복 처리, 장애 복구까지 함께 고려해야 하는 기능이라고 생각했습니다.

특히 다음과 같은 문제를 직접 해결해보고 싶었습니다.

- DB 저장과 Kafka 메시지 발행이 원자적으로 묶이지 않는 문제
- Kafka 장애 시 메시지가 유실될 수 있는 문제
- 동일 메시지 재수신 시 중복 처리될 수 있는 문제
- 외부 송금 실패 시 rollback 대신 보상 트랜잭션이 필요한 문제

이 프로젝트는 위 문제를 학습하고 검증하기 위해 Outbox Pattern, Ledger 모델, 멱등성 보장, 보상 트랜잭션 구조를 적용한 실험 프로젝트입니다.

## 주요 기능

- 계좌 간 송금 요청 처리
- Kafka 기반 비동기 송금 이벤트 발행 및 소비
- Outbox Pattern 기반 메시지 발행 보장
- AccountLedger 기반 출금/입금 원장 기록
- 중복 메시지 재수신에 대한 멱등성 처리
- 외부 송금 실패 시 보상 트랜잭션 처리
- 테스트용 컨트롤러를 통한 재발행, 실패, 보상 시나리오 검증

## 기술 스택

### Backend
- Java 17
- Spring Boot
- Spring MVC
- Spring Data JPA

### Database
- MySQL

### Messaging
- Apache Kafka
- Spring Kafka

### Serialization / Utilities
- Jackson
- Lombok

### Infra / Runtime
- Tomcat (Spring Boot 내장)
- HikariCP
- Nginx (Reverse Proxy 실습)
- Docker / Docker Compose

## 패키지 구조

프로젝트는 도메인과 역할을 기준으로 아래와 같이 구성되어 있습니다.

```text
com.example.bank
├── account
│   └── 계좌 관련 리포지토리 및 DTO
├── controller
│   └── API 진입점
├── controller.test
│   └── 테스트 및 시나리오 검증용 컨트롤러
├── domain
│   └── 핵심 도메인 엔터티(Account)
├── exception
│   └── 사용자 정의 예외
├── ledger
│   └── AccountLedger 및 원장 관련 로직
├── outbox
│   └── OutboxEvent, OutboxRelayer, 상태 관리
├── transfer
│   └── 송금 요청, 처리, 이벤트, 보상 관련 로직
└── BankApplication
```
### 주요 패키지 설명

#### `domain`
- `Account` 엔터티
- `@Version` 기반 낙관적 락 적용

#### `transfer`
- `TransferService`
- `TransferLedger`
- `TransferEvent`
- `TransferConsumer`
- 외부 송금 실패 보상 관련 이벤트 및 서비스

#### `ledger`
- `AccountLedger`
- `EntryType`
- 원장 기록 저장소

#### `outbox`
- `OutboxEvent`
- `OutboxRelayer`
- `OutboxStatusService`
- 커밋 이후 Kafka 발행 및 재시도 처리

#### `controller.test`
- 시나리오 테스트용 재발행 및 보상 API

## 핵심 설계

### 1. 내부 송금 처리

송금 요청이 들어오면 먼저 고유한 `transferId`를 생성하고, `TransferLedger`를 `PENDING` 상태로 저장합니다.

그 후 `TransferEvent`를 생성하여 `OutboxEvent`로 저장하고, 트랜잭션 커밋 이후 `OutboxRelayer`가 Kafka로 발행합니다.

Kafka Consumer는 메시지를 수신한 뒤 실제 송금 로직을 수행합니다.

- 송금인 계좌 출금
- 수취인 계좌 입금
- `AccountLedger`에 `DEBIT` / `CREDIT` 기록
- `TransferLedger` 상태를 `SUCCESS`로 전환

즉, 요청 저장과 실제 처리 흐름을 분리하여 비동기 구조로 구성했습니다.

### 2. Outbox Pattern

DB 저장과 Kafka 메시지 발행은 서로 다른 시스템에 대한 작업이기 때문에, 하나의 로컬 트랜잭션으로 완전히 묶을 수 없습니다.

이를 해결하기 위해 Outbox Pattern을 적용했습니다.

- `TransferLedger`와 `OutboxEvent`를 같은 DB 트랜잭션으로 저장
- 커밋 이후 `@TransactionalEventListener(AFTER_COMMIT)`로 Kafka 발행
- 발행 실패 시 `PENDING` 상태 유지
- 스케줄러가 `PENDING` 이벤트를 재시도

이를 통해 DB 기록은 성공했지만 메시지 발행은 실패하는 불일치 상황을 줄일 수 있었습니다.

### 주요 패키지 설명

#### `domain`
- `Account` 엔터티
- `@Version` 기반 낙관적 락 적용

#### `transfer`
- `TransferService`
- `TransferLedger`
- `TransferEvent`
- `TransferConsumer`
- 외부 송금 실패 보상 관련 이벤트 및 서비스

#### `ledger`
- `AccountLedger`
- `EntryType`
- 원장 기록 저장소

#### `outbox`
- `OutboxEvent`
- `OutboxRelayer`
- `OutboxStatusService`
- 커밋 이후 Kafka 발행 및 재시도 처리

#### `controller.test`
- 시나리오 테스트용 재발행 및 보상 API

## 핵심 설계

### 1. 내부 송금 처리

송금 요청이 들어오면 먼저 고유한 `transferId`를 생성하고, `TransferLedger`를 `PENDING` 상태로 저장합니다.

그 후 `TransferEvent`를 생성하여 `OutboxEvent`로 저장하고, 트랜잭션 커밋 이후 `OutboxRelayer`가 Kafka로 발행합니다.

Kafka Consumer는 메시지를 수신한 뒤 실제 송금 로직을 수행합니다.

- 송금인 계좌 출금
- 수취인 계좌 입금
- `AccountLedger`에 `DEBIT` / `CREDIT` 기록
- `TransferLedger` 상태를 `SUCCESS`로 전환

즉, 요청 저장과 실제 처리 흐름을 분리하여 비동기 구조로 구성했습니다.

### 2. Outbox Pattern

DB 저장과 Kafka 메시지 발행은 서로 다른 시스템에 대한 작업이기 때문에, 하나의 로컬 트랜잭션으로 완전히 묶을 수 없습니다.

이를 해결하기 위해 Outbox Pattern을 적용했습니다.

- `TransferLedger`와 `OutboxEvent`를 같은 DB 트랜잭션으로 저장
- 커밋 이후 `@TransactionalEventListener(AFTER_COMMIT)`로 Kafka 발행
- 발행 실패 시 `PENDING` 상태 유지
- 스케줄러가 `PENDING` 이벤트를 재시도

이를 통해 DB 기록은 성공했지만 메시지 발행은 실패하는 불일치 상황을 줄일 수 있었습니다.

### 3. Kafka 장애 시 Outbox Retry
- Kafka 전송 실패 시 `OutboxEvent = PENDING`
- 이후 스케줄러가 재시도
- Kafka 복구 후 발행 성공 시 `SENT`

### 4. 동일 이벤트 재수신
- 동일 `transferId`의 메시지를 다시 발행
- 상태 가드 및 Unique 제약으로 중복 처리 방지

### 5. 외부 입금 실패
- 실패 이벤트 발행
- 보상용 `CREDIT` 원장 추가
- `TransferLedger = COMPENSATED`

## 실행 방법

### 1. 환경 준비

아래 구성요소가 필요합니다.

- Java 17
- MySQL
- Kafka
- Docker / Docker Compose (권장)

### 2. DB 설정

MySQL에 데이터베이스를 생성하고 애플리케이션 설정 파일에 접속 정보를 입력합니다.

초기 데이터는 `data.sql`을 통해 로드할 수 있습니다.

### 3. Kafka 실행

Kafka와 Zookeeper를 먼저 실행합니다.

```bash
docker compose up -d
```
### 4. 애플리케이션 실행
./gradlew bootRun

또는 IDE에서 `BankApplication`을 실행합니다.

---

### 5. API 테스트

**예시 엔드포인트**

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/accounts/{id}` | 계좌 조회 |
| `POST` | `/transfer` | 이체 요청 |

**테스트용 엔드포인트**

- Replay 관련 API
- 외부 실패 / 보상 관련 API

---

---

## 트러블슈팅 / 배운 점

### 1. Outbox는 "커밋 이후 발행"이 핵심

처음에는 엔터티 자체를 이벤트로 넘기거나, 커밋 전후 경계가 모호해 문제가 생길 수 있었습니다.
이를 `OutboxCreatedEvent(outboxId)` 형태로 정리하면서 커밋 이후 발행 흐름을 명확히 했습니다.

### 2. 멱등성은 상태 가드만으로 끝나지 않는다

애플리케이션 레벨에서 `SUCCESS` 상태를 체크하더라도, 예외적인 재처리 상황에서는 **DB Unique 제약이 최후 방어선** 역할을 할 수 있다는 점을 확인했습니다.

### 3. 비즈니스 예외와 시스템 예외를 다르게 다뤄야 한다

- **비즈니스 예외** (잔액 부족 등): 상태를 남기고 종료
- **시스템 예외**: 롤백과 재시도를 고려

### 4. Web Server와 WAS 분리 실습

Spring Boot 단독 구조에서 Nginx를 앞단에 붙여 아래 구조로 확장했습니다.
```
Client → Nginx → Spring Boot → MySQL
```
- 정적 파일은 Nginx가 직접 응답
- API 요청은 Reverse Proxy로 Spring Boot에 전달

이를 통해 Web Server와 WAS의 역할 차이를 프로젝트 수준에서 직접 확인했습니다.

---

## 한계와 개선 방향

### 현재 한계

- 단일 애플리케이션 인스턴스 기준 구현
- Saga는 최소 수준의 보상 실험만 반영
- 모니터링 및 운영 지표 수집 미구현
- 실제 타행 연동은 mock 수준에 가까움

### 개선 방향

- Kafka Consumer 그룹 기반 확장성 검토
- Outbox / TransferLedger 모니터링 기능 추가
- Retry 정책 고도화 및 실패 큐 분리
- Saga 흐름 구체화
- 관리자용 조회 API를 추가해 N+1, 인덱스 설계를 실전형으로 확장
- README, 아키텍처 다이어그램, 운영 구조 문서화 고도화