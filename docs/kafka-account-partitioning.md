# 출금 계좌 기준 Kafka 파티셔닝

## 저장과 발행

- `TransferService.requestTransfer()`가 기존 트랜잭션 안에서 TransferLedger와 Outbox를 저장한다.
- `aggregateId`는 기존 `transferId`이며, 새 `partitionKey`는 `String.valueOf(fromAccountId)`이다.
- AFTER_COMMIT과 PENDING Scheduler는 모두 `OutboxRelayer.sendToKafka()`에서 저장된 Key와 원본 Payload를 발행한다.
- Replay API도 TransferLedger의 `fromAccountId`를 Key로 사용한다. Payload의 거래 ID와 송금 정보는 유지한다.
- Topic은 `transfer-topic`, 파티션 수는 기존 3개를 유지한다. Consumer Group과 Offset 정책도 변경하지 않는다.

## 기존 Outbox와 스키마

`partition_key`는 기존 행을 수용하기 위해 nullable이다. 현재 프로젝트는 Hibernate `ddl-auto: update`를 사용하므로 시작 시 컬럼이 추가된다. 스키마를 별도로 관리하는 환경에서는 **새 애플리케이션을 시작하기 전에** 컬럼 존재 여부를 확인하고, 없을 때 다음 DDL을 한 번 적용한다.

```sql
ALTER TABLE outbox_event ADD COLUMN partition_key VARCHAR(255) NULL;
```

기존 행의 Key가 null이면 설정된 ObjectMapper로 원본 `TransferEvent`를 읽는다. 출금 계좌 ID가 양수이며 Payload의 `transferId`가 `aggregateId`와 일치할 때만 출금 계좌 ID를 Key로 사용한다. Payload를 재직렬화하거나 `aggregateId`를 변경하지 않는다. 복구한 Key는 DB에 다시 쓰지 않으며, 같은 원본 Payload에서 매번 동일하게 결정한다. 이로써 복구용 트랜잭션이나 기존 트랜잭션 경계 변경이 필요하지 않다.

손상된 JSON, 누락되거나 유효하지 않은 출금 계좌 ID, 거래 ID 불일치, 비어 있는 저장 Key는 오류 로그를 남기고 발행하지 않는다. 해당 행은 PENDING으로 남아 다음 Scheduler에서도 다시 검증되므로 운영자가 로그의 `outboxId`와 `aggregateId`로 원본을 조사하고 수정해야 한다. 다른 PENDING 행은 계속 처리한다. Kafka 발행 실패와 성공 후 SENT 처리 방식은 기존과 같다.

## 전환 시 주의사항

이 변경은 이미 Kafka에 기록된 메시지의 Key나 파티션을 이동하지 않는다. 기존 `transferId` Key 메시지와 새 계좌 Key 메시지가 함께 남아 있으면 동일 출금 계좌가 여러 파티션에서 동시에 처리될 수 있다. 구버전 Producer가 섞인 롤링 배포 중에도 같은 문제가 발생한다.

전환 구간의 혼재를 줄이려면 다음 순서로 적용한다.

1. 송금 접수와 Replay를 중지하고, 구버전 Relayer로 기존 PENDING Outbox 발행을 완료한다. 발행 불가 행은 원인을 조사한다.
2. 구버전 Producer/Relayer를 모두 중지하고, Consumer가 기존 메시지를 처리하여 파티션별 lag가 해소되었는지 확인한다. 진행 중인 처리와 재시도도 완료되었는지 확인한다.
3. nullable 컬럼을 준비하고 새 버전으로 교체한 뒤 접수와 발행을 재개한다. 배포 때 PENDING으로 남은 구형 Outbox는 새 버전에서 Payload 기준으로 Key를 복구한다.

Consumer Group과 Offset은 유지한다. Offset을 초기화할 필요는 없다. 이미 처리한 거래를 Replay하거나 Kafka 성공 후 SENT 반영 전에 장애가 발생하면 중복 메시지가 생길 수 있으므로 기존 거래 멱등성 처리를 계속 사용한다. 롤백으로 구버전 Producer가 재개되어도 Key 혼재가 발생하므로 같은 전환 절차를 고려한다. nullable 컬럼은 구버전 호환성을 위해 남길 수 있다.

## 순서와 동시성의 범위

동일한 출금 계좌 Key는 기존 Key 기반 파티셔닝 설정과 파티션 수가 유지되는 동안 같은 파티션으로 전달된다. 파티션 수를 변경하면 매핑이 달라질 수 있다. 서로 다른 계좌 Key도 같은 파티션에 배치될 수 있다.

같은 Consumer Group은 파티션에 기록된 순서로 처리하지만, 이는 DB 요청 접수 순서가 아니다. 여러 Relayer, 즉시 발행과 Scheduler의 경합, 비동기 발행, 실패 후 재발행으로 기록 순서가 달라지거나 중복될 수 있다. 이번 변경은 이러한 기존 Outbox 특성을 바꾸지 않는다.

다른 출금 계좌에서 같은 계좌로 입금하는 송금, 다른 Consumer Group, 그 밖의 계좌 변경은 이 Key로 순차화되지 않는다. 따라서 기존 DB 트랜잭션, 계좌 `@Version`, AccountLedger Unique Constraint, TransferFacade의 Exponential Backoff + Random Jitter를 유지한다. Consumer ACK/ErrorHandler와 Outbox 성공/실패 정책도 유지한다.

## 검증 범위

`OutboxRelayerTest`, `TransferServiceOutboxTest`, `InternalReplayTestControllerTest`는 외부 서비스 없이 저장 Key, 두 발행 경로, Payload, 구형 데이터 복구/거부, 실패 후 재발행 및 기존 성공 거래의 멱등성을 검증한다. 기존 `TransferFacadeRetryTest`는 낙관적 락 예외 후 재시도를 검증한다.

실제 Kafka의 파티션 기록 확인과 MySQL의 낙관적 락/Unique Constraint/동시 송금 검증은 별도 통합 환경이 필요하다. 단위 테스트의 KafkaTemplate mock 검증만으로 실제 파티션 배치나 DB 동시성 검증을 대체할 수 없다.

2026-11-10 로컬 검증: Java 21 / Gradle 9.2.1에서 단위 테스트 22건을 통과했다. 이후 WSL Docker에 격리된 MySQL 8.0.45 / Kafka 테스트 환경을 구성하여 기존 DB 동시성 테스트 3건과 신규 DB·Kafka 통합 테스트 6건도 통과했다. 총 31건이며 변경 파일의 `git diff --check`도 통과했다. 실제 파티션 기록, 제약조건 충돌, 롤백, 멱등성 및 재시도 결과와 재현 절차는 [통합 테스트 기록](integration-tests.md)을 참고한다.

## 실행 이미지

새 이미지 태그는 `meatcarrot/bank-app:20261110-account-key`이다. 루트와 `deploy/`의 Compose가 모두 이 태그를 사용한다. 로컬 Compose는 기존 내부 Kafka 리스너 `kafka:29092`, 배포용 Compose는 기존 `kafka:9092` 설정을 유지한다.

Dockerfile은 `build/libs/bank-0.0.1-SNAPSHOT.jar`를 명시적으로 복사한다. `assemble`이 일반 `-plain.jar`를 함께 생성해도 실행 가능한 Boot JAR만 이미지에 포함된다. 프로젝트 버전이 바뀌면 `--build-arg JAR_FILE=build/libs/<새 Boot JAR 이름>`으로 지정할 수 있다.

```bash
# Java 21. CI 모드에서는 외부 DB·Kafka가 필요한 integration 태그 테스트를 제외한다.
CI=true ./gradlew build
docker build -t meatcarrot/bank-app:20261110-account-key .
docker push meatcarrot/bank-app:20261110-account-key
```

2026-11-10 이미지 검증: `linux/amd64`, Java 21 JRE 이미지로 빌드했다. 격리된 MySQL·Kafka에 연결하여 애플리케이션 정상 기동, 회원가입·로그인 HTTP 200과 Access/Refresh Token 발급을 확인했다. 로컬·배포용 Compose의 `config --quiet` 검증도 통과했다. 기존 동시 송금 테스트에도 `integration` 태그를 붙여 외부 DB가 없는 CI와 통합 검증 실행을 구분했다.

기존 환경 교체 시에는 위 전환 절차에 따라 접수 중지, PENDING 발행·Consumer lag 소진을 먼저 완료한다. 이미지 준비 후 대상 환경에 맞는 Compose로 `pull app1` 및 `up -d --no-deps app1`을 실행한다. 두 앱을 사용하는 로컬 구성은 `app1 app2`를 함께 지정한다. Consumer Offset과 Topic 파티션 수를 바꾸지 않는다.
