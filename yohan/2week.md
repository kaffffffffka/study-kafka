# AdminClient
내부적으로 CompletableFuture로 구현이 되어 있음
```java
    private Map<Uuid, KafkaFuture<Void>> handleDeleteTopicsUsingIds(Collection<Uuid> topicIds, DeleteTopicsOptions options) {
        Map<Uuid, KafkaFutureImpl<Void>> topicFutures = new HashMap(topicIds.size());
        List<Uuid> validTopicIds = new ArrayList(topicIds.size());

        for(Uuid topicId : topicIds) {
            if (topicId.equals(Uuid.ZERO_UUID)) {
                KafkaFutureImpl<Void> future = new KafkaFutureImpl();
                future.completeExceptionally(new InvalidTopicException("The given topic ID '" + topicId + "' cannot be represented in a request."));
                topicFutures.put(topicId, future);
            } else if (!topicFutures.containsKey(topicId)) {
                topicFutures.put(topicId, new KafkaFutureImpl());
                validTopicIds.add(topicId);
            }
        }

        if (!validTopicIds.isEmpty()) {
            long now = this.time.milliseconds();
            long deadline = this.calcDeadlineMs(now, options.timeoutMs());
            Call call = this.getDeleteTopicsWithIdsCall(options, topicFutures, validTopicIds, Collections.emptyMap(), now, deadline);
            this.runnable.call(call, now);
        }

        return new HashMap(topicFutures);
    }

public boolean completeExceptionally(Throwable newException) {
    return this.completableFuture.kafkaCompleteExceptionally((Throwable)(newException instanceof CompletionException ? new CompletionException(newException) : newException));
}
```

그래서 실제 요청을 보낸 순간 처리되지 않기에 주의 필요


# Produce
```bash
[appuser@0e5bab7a86f2 peter-test01-0]$ kafka-dump-log  --print-data-log --files ./00000000000000000000.log
Dumping ./00000000000000000000.log
Log starting offset: 0

baseOffset: 0 lastOffset: 0 count: 1 baseSequence: 0 lastSequence: 0 producerId: 1000 producerEpoch: 0 partitionLeaderEpoch: 0 isTransactional: false isControl: false deleteHorizonMs: OptionalLong.empty position: 0 CreateTime: 1731413316107 size: 81 magic: 2 compresscodec: none crc: 4097350449 isvalid: true
| offset: 0 CreateTime: 1731413316107 keySize: -1 valueSize: 13 sequence: 0 headerKeys: [] payload: test message1

baseOffset: 1 lastOffset: 1 count: 1 baseSequence: 1 lastSequence: 1 producerId: 1000 producerEpoch: 0 partitionLeaderEpoch: 0 isTransactional: false isControl: false deleteHorizonMs: OptionalLong.empty position: 81 CreateTime: 1731413319606 size: 81 magic: 2 compresscodec: none crc: 2966262959 isvalid: true
| offset: 1 CreateTime: 1731413319606 keySize: -1 valueSize: 13 sequence: 1 headerKeys: [] payload: test message2
```

- partitionLeaderEpoch: 파티션 리더의 에포크입니다. 리더가 변경될 때마다 증가(Epoch 모두 같은 의미)
- position: 이 메시지가 로그 세그먼트 내에서 시작되는 바이트 위치
- isTransactional: 트랜잭션 여부를 나타내며, true일 경우 메시지가 트랜잭션 내에서 전송
- isControl: 메시지가 컨트롤 메시지인지 여부 , 트랜잭션 상태 관리를 위한 메시지

# Producer 파티션 전략

```bash
sh-4.4$ kafka-dump-log --files  00000000000000000000.log --print-data-log
Dumping 00000000000000000000.log
Log starting offset: 0
baseOffset: 0 lastOffset: 0 count: 1 baseSequence: -1 lastSequence: -1 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 0 isTransactional: false isControl: false deleteHorizonMs: OptionalLong.empty position: 0 CreateTime: 1731503191140 size: 80 magic: 2 compresscodec: none crc: 3896851581 isvalid: true
| offset: 0 CreateTime: 1731503191140 keySize: -1 valueSize: 12 sequence: -1 headerKeys: [] payload: test message
baseOffset: 1 lastOffset: 1 count: 1 baseSequence: -1 lastSequence: -1 producerId: -1 producerEpoch: -1 partitionLeaderEpoch: 0 isTransactional: false isControl: false deleteHorizonMs: OptionalLong.empty position: 80 CreateTime: 1731503191141 size: 80 magic: 2 compresscodec: none crc: 3956997555 isvalid: true
```
`kafkaProps.put(ProducerConfig.ACKS_CONFIG, "1");`
해당 설정을 하게 되면 `enable.idempotence`의 기본 조건이 깨지게 되어 멱등성을 보장하지 못하게 된다.
그래서 위 producerId가 -1로 나오게 된다.

## option
### batch.size
배치로 묶이는 데이터 크기를 결정합니다. Producer는 메시지를 batch.size만큼 묶어서 한 번에 전송
`props.put("batch.size", 32768); // 배치 크기를 32KB로 설정`

### buffer.memory
Producer가 브로커로 전송하기 전에 메시지를 임시로 저장하는 메모리 버퍼 크기
`props.put("buffer.memory", 67108864); // 64MB로 설정`

### acks
`acks=0`: 브로커로 보내기만 하고 응답은 받지 않음 -> retry가 의미가 없음
`acks=1`: 브로커(리더)에게 보낸 후 리더에게서만 응답값을 받는 경우
`acks=all`: 브로커(리더)에게 보낸 후 Replica까지 모두 Commit 되는 것을 확인

Retries를 조정하는 것보다 `delivery.timeout.ms`를 조정


### enable.idempotence, max.in.flight.requests.per.connection
한 번에 위 설정된 값만큼 batch를 전송
순서 보장은 파티션 단위에서만 보장


### Rack Awareness

Rack을 분산시키는 것 - 같은 장비에 카프카를 띄우지 않기 위한 방법
`broker.rack=<name>`

## Replication

### Leader partition

Producer와 Consumer는 리더 파티션을 통해서만 데이터를 읽고 씁니다.(2.4부터 follower 읽기 가능 - 성능 저하)
기본적으로 pull 방식

리더 파티션이 하나의 브로커에만 몰릴 경우 성능 저하 -> 옵션 설정을 통해 자동 분산 Hot Spot 방지
`autoleader.rebalance.enable: true` 기본값

리더가 없으면 해당 파티션은 사용이 불가

### ISR

리더 파티션에서 ISR 내 모든 레플리카가 데이터를 복제 완료한 지점(offset)을 나타냄
ISR은 리더가 관리
리더 파티션과 팔로워 파티션은 하이워터마크로 동기화를 체크하고 있는데 리더 파티션을 잘 따라잡고 있는 팔로워 파티션들을 의미

`replica.lag.max.messages=4` : Leader 파티션과 Follower 파티션하고 얼마나 차이를 둘 것인지를 지정
`replica.lag.time.max.ms=10000` : follower가 fetch 요청을 어느 주기로 하는지로 판단.
**팔로워가 replica.lag.max.messages나 replica.lag.time.max.ms를 초과하면 ISR에서 제외**

`min.insync.replicas` : 해당 값보다 적은 isr을 가지고 있으면 에러 발생


## Controller

Kafka cluster중 하나가 Controller가 된다.
Controller는 모든 브로커의 정보를 캐시하며 Leader 장애시 Leader Election을 수행
Controller가 죽으면 주키퍼가 선출함

# Consumer

같은 컨슈머 그룹에서 하나의 컨슈머는 하나의 파티션을 담당

```java
kafkaProps.put(ProducerConfig.BATCH_SIZE_CONFIG,4);
kafkaProps.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, RoundRobinPartitioner.class.getName());
```
정말 그런지 확인 하기 위해서는 프로듀서 설정을 위와 같은 형태로 해주거나  RoundRobin방식을 이용하면 된다.
-> 위 설정을 하지 않으면 스티키하게 설정이 되어서 하나의 파티션에만 값이 들어가게 된다.


## consumer Lag

현재 프로듀서와 컨슈머의 데이터 처리량의 차이를 뜻한다.
```bash
GROUP           TOPIC           PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG             CONSUMER-ID                                                 HOST            CLIENT-ID
multi-group     test            0          33              120             87              consumer-multi-group-1-610da093-3f3c-4296-8541-ddc31b826b0e /172.18.0.1     consumer-multi-group-1
```

CURRENT-OFFSET : 컨슈머의 offset
LOG-END-OFFSET: 마지막 offset
LAG: (LOG-END-OFFSET -  CURRENT-OFFSET)의 값을 의미

Lag의 값이 크다면 컨슈머를 확인해야 하며 파티션을 늘려서 처리량을 늘려야 한다.
이 때 주의할 점으로는 파티션은 한 번 늘리면 줄 일 수 없으며 기존과 같은 키값인데도 불구하고 다른 파티션에 저장 될 수 있다.
=> 이를 해결 하기 위해서는 커스텀 파티셔너를 이용

## ReBalancing

하나의 파티션은 하나의 컨슈머만 사용(같은 컨슈머 그룹)

`partition.assignment.strategy` : 컨슈머 파티션 할당 전략

__consumer_offset의 리더 파티션을 가지고 있는 브로커가 그룹 코디네이터가 됨
파티션 할당 계산은 클라이언트가 진행

컨슈머가 중간에 탈퇴하거나, 합류하거나 토픽을 변경하거나 메타데이터가 변경되는 경우 리밸런싱 진행
모든 컨슈머 일시중지 -> 파티션 재할당 -> 다시 시작

컨슈머 장애는 하트비트 또는 poll로 판단

1. Consumer group 멤버 고정
2. session.timeout.ms 튜닝 -> 더 많은 시간을 줌으로써 rejoin 할 수 있는 시간을 부여 -> 그러나 컨슈머 장애 판단이 오래 걸림
3. max.poll 사이즈 조절 -> 너무 크면 데이터를 처리하는 시간이 걸이졈ㄴ서 poll을 호출 못함

## Eager Rebalancing Protocol
1. 기존 연결된 파티션 모두 제거
2. 다시 모든 컨슈머 참여 요청
3. 계산
4. 파티션 정보 전달

[From Eager to Smarter in Apache Kafka Consumer Rebalances](https://cwiki.apache.org/confluence/display/KAFKA/KIP-429%3A+Kafka+Consumer+Incremental+Rebalance+Protocol)


## Cooperative Rebalancing Protocol
리밸런싱 과정이 2번 일어남
1. 컨슈머 참여
2. join group 정보 전달(자기 자신의 정보 전달)
3. 해당 정보를 토대로 revoke할 파티션 정한 후 전달
4. 해당 파티션만 revoke

1. 다시 join group 요청
2. reovke된 파티션 할당

## 스태틱 멤버십

컨슈머에게 ID를 부여 -> 이를 통해 컨슈머가 종료되어도 리밸런싱을 바로 하지 않고 대기
=> 정상 종료의 경우에는 리밸런싱 진행

### 스태틱 멤버십이 없는 경우와의 차이가 무엇???
=> 재연결과정에서 차이가 있다는데...

## 전송 전달
## Exactly Once
`enable.idempotence`의 값을 true로 설정하면 Exactly Once가 적용됨

**Producer ID (PID)**

프로듀서가 브로커에 연결될 때 고유한 **Producer ID (PID)**를 할당

**Sequence Number**

각 메시지는 고유한 Sequence Number를 가짐

위 2개의 값을 이용해서 중복 전송을 확인하고 처리함

```scss
Producer (PID=1234)
    |
    |-- Msg(Seq=1) ---> [Broker]
    |-- Msg(Seq=2) ---> [Broker]
    |-- Msg(Seq=3) ---> [Broker]
             ^
             |---- Msg(Seq=2) 재전송 발생 (네트워크 장애 등)
             
[Broker]
    |
    |- PID=1234, Seq=1 --> 메시지 처리
    |- PID=1234, Seq=2 --> 메시지 처리
    |- PID=1234, Seq=2 --> 중복 메시지, 무시
    |- PID=1234, Seq=3 --> 메시지 처리
```
offset과 Sequence Number는 다른 개념
-> offset은 저장하고 난 후에 반환하는 개념인데 위의 경우 아직 저장되기 전 메시지 값이니...

.snapshot에 위 값들을 저장하고 비교

### Transaction
여러 요청을 원자적으로 처리하기 위한 방법

**producer**

`enable.idempotence`  true 설정 필수 (기본값)
`transaction.id`를 설정하고 보내야 함

**Consumer**

`isolation.level: read_committed` 로 설정되어야만 commit된 메시지 확인 가능
`enable.auto.commit` 중복 처리 로직은 따로 작성해야 함 -> 처리하고 나서 consumer offset을 되감기 해서 다시 처리하는 경우 또 처리할 가능성 있음

1. Producer -> Metadata Request: 트랜잭션 코디네이터 찾기
2. Producer -> Transaction Coordinator: InitPidRequest (PID 요청)
3. Coordinator: 트랜잭션 로그에 transactional.id와 PID 매칭
4. Producer -> Coordinator: beginTransaction() 호출 (트랜잭션 시작)
5. Producer -> Coordinator: AddPartitionsToTxnRequest (파티션 추가)
6. Producer -> Partition Leader: 메시지 전송 (PID와 함께)
7. Producer -> Coordinator: commitTransaction() 또는 abortTransaction()
8. Coordinator: 트랜잭션 로그 업데이트 (Commit/Abort 상태 기록) -> Coordinator 자체도 저장하고 브로커에게도 요청을 보냄 이를 통해 브로커는 해당 메시지 뒤에 commit 메시지를 남김
9. Partition Leader: 메시지 Commit/Abort 상태에 따라 처리



---

# Spring kafka

### 1. **싱글 스레드 컨슈머**

- **기본적인 컨슈머 유형으로 단일 스레드로 동작**합니다.
- 스프링 카프카에서 가장 간단한 형태의 컨슈머 구현입니다.
- 특정 토픽과 파티션에 대해 하나의 스레드가 할당되어 메시지를 읽습니다.

#### 예시:
```java
@KafkaListener(topics = "my-topic", groupId = "my-group")
public void consume(String message) {
    System.out.println("Received message: " + message);
}
```

#### 특징:
- 사용이 간단하고 코드가 직관적임.
- 성능이 제한적이며, 병렬 처리가 필요하지 않은 경우 적합.

---

### 2. **멀티 스레드 컨슈머**
- **여러 스레드로 병렬 처리가 가능한 컨슈머**입니다.
- 한 그룹에 여러 컨슈머 인스턴스를 생성하여 각 파티션을 병렬로 처리합니다.
- 스프링 카프카에서는 동일한 그룹 ID를 가진 여러 컨슈머를 생성하면 자동으로 멀티 스레딩이 구성됩니다.

#### 설정 방법:
```java
@KafkaListener(topics = "my-topic", groupId = "my-group", concurrency = "3")
public void consume(String message) {
    System.out.println("Received message: " + message);
}
```

#### 특징:
- **`concurrency` 속성**을 설정하여 병렬 처리 가능.
- 토픽의 파티션 수만큼 병렬 처리가 이루어짐.
- 동일한 그룹 내에서는 각 컨슈머가 서로 다른 파티션을 읽음.

---

### 3. **배치 컨슈머 (Batch Listener)**
- **한 번에 여러 메시지를 읽어 처리**할 수 있는 컨슈머입니다.
- 기본적으로 스프링 카프카는 한 번에 하나의 메시지를 처리하지만, 배치 리스너를 사용하면 여러 메시지를 리스트 형태로 받을 수 있습니다.
- 대량의 메시지를 효율적으로 처리하고자 할 때 유용합니다.

#### 설정 방법:
1. **`containerFactory` 설정**:
   ```java
   @KafkaListener(topics = "my-topic", groupId = "my-group", containerFactory = "batchFactory")
   public void consume(List<String> messages) {
       System.out.println("Received messages: " + messages);
   }
   ```

2. **배치 리스너 컨테이너 팩토리 정의**:
   ```java
   @Bean
   public ConcurrentKafkaListenerContainerFactory<String, String> batchFactory(
           ConsumerFactory<String, String> consumerFactory) {
       ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
       factory.setConsumerFactory(consumerFactory);
       factory.setBatchListener(true); // 배치 리스너 활성화
       return factory;
   }
   ```

#### 특징:
- 효율적으로 대량의 메시지를 처리.
- 메시지를 리스트로 처리하기 때문에 개별 메시지 단위의 제어가 어렵다.

---

### 4. **Acknowledge Mode 컨슈머**
- 메시지의 **수동 커밋 또는 자동 커밋** 방식을 제어할 수 있는 컨슈머입니다.
- 기본적으로 Kafka는 메시지를 읽고 처리 완료를 Kafka에 알려야(커밋) 합니다.
- 스프링 카프카는 다양한 Acknowledge 모드를 제공합니다:
  - `AUTO`: 메시지가 읽히면 자동 커밋.
  - `MANUAL`: 메시지 처리가 완료되면 사용자가 명시적으로 커밋.
  - `MANUAL_IMMEDIATE`: 즉시 커밋.

#### 설정 방법:
```java
@KafkaListener(topics = "my-topic", groupId = "my-group", containerFactory = "manualAckFactory")
public void consume(String message, Acknowledgment ack) {
    System.out.println("Received message: " + message);
    // 메시지 처리 완료 후 커밋
    ack.acknowledge();
}
```

**컨테이너 팩토리 설정**:
```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, String> manualAckFactory(
        ConsumerFactory<String, String> consumerFactory) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
    return factory;
}
```

#### 특징:
- 메시지 처리 상태를 명확히 제어 가능.
- 실패 시 커밋을 지연하여 재처리 로직 구현 가능.

---

### 5. **Transactional 컨슈머**
- Kafka와의 트랜잭션을 보장하는 컨슈머입니다.
- 데이터베이스나 다른 외부 시스템과 연계하여 **"모두 처리되거나 아무것도 처리되지 않음"**을 보장합니다.
- 컨슈머와 프로듀서를 함께 사용하는 경우 유용합니다.

#### 설정 방법:
1. **트랜잭션 설정**:
   ```java
   @Bean
   public KafkaTransactionManager<String, String> kafkaTransactionManager(ProducerFactory<String, String> producerFactory) {
       return new KafkaTransactionManager<>(producerFactory);
   }
   ```

2. **트랜잭션 활성화**:
   ```java
   @KafkaListener(topics = "my-topic", groupId = "my-group", containerFactory = "transactionalFactory")
   @Transactional
   public void consume(String message) {
       System.out.println("Processed in transaction: " + message);
   }
   ```

#### 특징:
- 메시지 처리의 **원자성** 보장.
- 다소 성능 오버헤드가 있을 수 있음.

---

### 요약
- **싱글 스레드 컨슈머**: 간단한 메시지 처리.
- **멀티 스레드 컨슈머**: 병렬 처리로 성능 최적화.
- **배치 컨슈머**: 대량 메시지 처리 최적화.
- **Acknowledge Mode 컨슈머**: 메시지 커밋을 세밀히 제어.
- **Transactional 컨슈머**: 트랜잭션 처리로 원자성 보장.
