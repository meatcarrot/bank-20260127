# MySQL·Kafka 통합 검증 기록

## 격리 환경

2026-11-10, `version4` 작업 트리에서 실행했다. Windows에 Docker CLI는 없었지만 WSL `Ubuntu-22.04`에 Docker 28.2.2와 Compose v5.0.2, 필요한 이미지가 있어 이를 사용했다.

| 항목 | 구성 |
| --- | --- |
| Compose 프로젝트 | `bank-key-it` |
| 정의 | `infra/integration/docker-compose.yml` |
| MySQL | `mysql:8.0`, 실제 서버 8.0.45, `localhost:13306/bank_key_it` |
| DB 사용자 | 테스트 전용 `bank_it` |
| Kafka | `confluentinc/cp-kafka:7.5.0`, `localhost:19092` |
| ZooKeeper | `confluentinc/cp-zookeeper:7.5.0`, 외부 포트 노출 없음 |
| Topic | `transfer-topic`, 기존 운영 코드 정의대로 3개 파티션 / 복제 1 |
| 테스트 런타임 | Java 21 / Gradle 9.2.1 |

기존 `bank-*` 컨테이너, `bank` 데이터베이스와 볼륨, 루트 `docker-compose.yml`은 변경하지 않았다. MySQL 데이터는 테스트 컨테이너의 tmpfs에 저장한다. 외부 포트는 loopback에만 노출한다. Kafka 자동 Topic 생성은 끄고, 새 통합 테스트가 `KafkaConfig.transferTopic()` 정의를 Admin API로 명시적으로 생성·검증한다.

## 수행한 검증

두 번의 Gradle 실행에서 총 **31건 통과, 실패·오류·skip 0건**이다.

| 테스트 | 건수 | 확인한 결과 |
| --- | ---: | --- |
| 기존 `AccountOptimisticLockTest` | 1 | 같은 MySQL 버전을 읽고 동시에 갱신하면 한쪽만 성공하고 다른 쪽은 낙관적 락 예외 |
| 기존 `TransferConcurrency100Test` | 2 | 10건·100건 동시 송금 성공. 100건 처리 후 출금 잔액 0, 입금 잔액 10,000, 거래 기록 100건, 재시도 초과 0건 |
| 신규 `TransferPartitionIntegrationTest` | 6 | 실제 DB·Kafka를 통한 아래 검증 |
| `OutboxRelayerTest` | 18 | 저장 Key, 구형 데이터 검증, 실패 후 재발행 |
| `TransferServiceOutboxTest` | 2 | 거래 식별자·Key 분리, Payload, 기존 성공 거래의 멱등성 |
| `InternalReplayTestControllerTest` | 1 | Replay Key 및 Payload |
| 기존 `TransferFacadeRetryTest` | 1 | 두 종류의 낙관적 락 예외 후 재시도 |

신규 통합 테스트 6건은 다음을 확인한다.

1. 실제 `requestTransfer()`의 DB 커밋 후 AFTER_COMMIT으로 3개 메시지가 발행된다. 같은 출금 계좌의 서로 다른 거래 2건과 다른 출금 계좌의 거래 1건을 사용한다. Outbox Key·Payload·거래 ID가 실제 DB에서 유지되고 SENT로 반영된다.
2. 같은 Outbox 3건을 PENDING으로 돌리되 1건의 `partition_key`를 실제 DB에서 null로 만든다. Scheduler 진입 메서드로 재발행하고, 원본 JSON과 동일한 Value 및 같은 Key·파티션을 확인한다. 복구한 Key는 DB에 다시 쓰지 않는다. Replay 경로에서도 같은 3건의 계좌 Key·파티션과 기존 Replay 형식의 Value를 확인한다. 이 시나리오는 하나의 테스트에서 총 9건을 검증한다.
3. 손상된 JSON의 구형 Outbox는 PENDING에 남고 발행되지 않는다. 함께 저장한 정상 구형 Outbox는 원본 계좌 Key로 Kafka에 기록되고 SENT가 된다.
4. 동일 `(transaction_id, account_id, type)`의 AccountLedger를 두 트랜잭션이 동시에 삽입하면 1건만 커밋된다. 다른 삽입은 실제 MySQL 오류 1062 / SQLState 23000에 따른 `DataIntegrityViolationException`이다. 세 컬럼 중 하나가 다른 원장 행은 정상 삽입된다.
5. 같은 계좌 버전을 읽은 두 트랜잭션의 갱신 중 하나만 성공하며 DB 잔액과 version 증가량이 일치한다. 별도로, 송금 중 원장 Unique Constraint 위반 시 출금·입금 잔액과 TransferLedger 상태가 모두 롤백된다.
6. 성공한 송금을 다시 처리해도 잔액은 한 번만 반영되고 원장도 차변·대변 2건만 남는다.

기존 동시 송금 테스트 로그에서도 실제 낙관적 락 충돌과 TransferFacade 재시도가 관찰됐다. 신규 테스트는 생산용 Service, 트랜잭션 프록시, Repository, Relayer, 상태 처리 서비스, Replay 메서드, Kafka Producer를 사용한다. KafkaTemplate·Repository mock을 사용하지 않는다. 관찰용 Consumer는 별도 그룹 ID로 파티션을 직접 할당하고 Offset을 커밋하지 않는다. 업무 Consumer와 자동 Scheduler는 새 테스트 컨텍스트에 포함하지 않아 의도하지 않은 소비·중복 발행을 막는다. `retryFailedEvents()`를 직접 호출하여 Scheduler 발행 경로를 검증한다.

최종 실행의 실제 Kafka 관찰 로그는 다음과 같다. 계좌 ID는 격리된 테스트 DB에서 생성된 값이며, 다른 실행의 계좌 ID·파티션 결과가 반드시 이 숫자와 같아야 하는 것은 아니다.

```text
KAFKA_PARTITIONS {6=1, 7=0}; records=9; paths=AFTER_COMMIT,SCHEDULER,REPLAY
```

## 재현 절차 (Windows PowerShell + WSL)

아래 명령은 저장소 루트에서 실행한다. 기존 DB 테스트에는 테이블 삭제가 있으므로 지정한 테스트 DB 환경 변수를 그대로 사용한다. 새 통합 테스트는 `BANK_INTEGRATION_TEST=true`일 때만 실행되며, 주소가 고정된 테스트 DB `bank_key_it`에만 `create-drop`을 적용한다.

```powershell
# 최초 실행 또는 이전 테스트 환경 정리 후 시작
wsl -d Ubuntu-22.04 -- docker compose -p bank-key-it -f /mnt/c/Users/ningu/IdeaProjects/bank/infra/integration/docker-compose.yml up -d --wait

$env:JAVA_HOME = 'C:/Users/ningu/AppData/Local/Programs/IntelliJ IDEA 2025.3.2/jbr'
$env:GRADLE_USER_HOME = "$env:USERPROFILE/.gradle"
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://localhost:13306/bank_key_it'
$env:SPRING_DATASOURCE_USERNAME = 'bank_it'
$env:SPRING_DATASOURCE_PASSWORD = 'bank_it'
$env:KAFKA_BOOTSTRAP_SERVERS = 'localhost:19092'
$env:BANK_INTEGRATION_TEST = 'true'
$env:CI = 'false'

./gradlew.bat test --no-daemon --tests com.example.bank.AccountOptimisticLockTest --tests com.example.bank.TransferConcurrency100Test

# 필요하면 첫 실행의 build/test-results/test XML을 별도로 복사한다.
./gradlew.bat test --no-daemon --tests com.example.bank.integration.TransferPartitionIntegrationTest --tests com.example.bank.outbox.OutboxRelayerTest --tests com.example.bank.transfer.TransferServiceOutboxTest --tests com.example.bank.controller.test.InternalReplayTestControllerTest --tests com.example.bank.TransferFacadeRetryTest

# 테스트 전용 컨테이너·네트워크·볼륨 정리. 다시 실행할 때는 up으로 새 환경 생성.
wsl -d Ubuntu-22.04 -- docker compose -p bank-key-it -f /mnt/c/Users/ningu/IdeaProjects/bank/infra/integration/docker-compose.yml down --volumes
```

두 test 실행은 기존 전체 애플리케이션 컨텍스트의 업무 Consumer가 새 Kafka 검증 중 동작하지 않도록 분리했다. 실제 실행은 이미 설치된 Gradle 9.2.1 배포판과 작업 폴더의 `.gradle/verification-home` 의존성 캐시를 이용하여 `--offline --project-cache-dir .gradle/verification-project-cache`로 수행했다. XML 증거는 `.gradle/integration-evidence`에 두 실행을 모두 보관했다. 기본 HTML 보고서 `build/reports/tests/test/index.html`은 마지막 실행의 결과만 표시한다. 후속 이미지 빌드 단계에서는 `CI=true`로 전체 `build`를 실행하여 단위 테스트 22건 통과와 기존 Disabled 테스트 1건 skip을 확인했다.

## 검증 한계

이번 검증은 단일 브로커·3개 파티션의 실제 기록 및 로컬 MySQL 동시성 테스트다. 브로커 장애, Consumer 재균형, 다중 Relayer 장애 복구 및 배포 중 구·신 Key 혼재까지 검증한 것은 아니다. 동일 출금 계좌 Key가 같은 파티션에 기록되는 결과가 DB 요청 접수 순서나 다른 출금 계좌로부터의 입금 순서까지 보장하지는 않는다. 기존 DB 트랜잭션·Optimistic Lock·재시도는 계속 필요하다.
