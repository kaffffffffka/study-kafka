package com.example;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;

public class SimpleConsumer {

    private final static Logger logger = LoggerFactory.getLogger(SimpleConsumer.class);
    private final static String TOPIC_NAME = "test";
    private final static String BOOTSTRAP_SERVERS = "localhost:9092";
    private final static String GROUP_ID = "test-group"; // kafka에서 컨슈머 그룹을 기준으로 컨슈머 offset을 관리.

    private static KafkaConsumer<String, String> consumer;
    private static Map<TopicPartition, OffsetAndMetadata> currentOffset = new HashMap<>();

    public static void main(String[] args) {
        // 셧다운 훅이란 사용자 또는 운영체제로부터 종료 요청을 받으면 실행하는 스레드
        Runtime.getRuntime().addShutdownHook(new ShutdownThread());

        Properties configs = new Properties();
        configs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        configs.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
        configs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        configs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        consumer = new KafkaConsumer<>(configs);
        consumer.subscribe(Arrays.asList(TOPIC_NAME), new RebalanceListener());

        // 컨슈머를 운영할 때 subscribe() 메서드를 사용하여 구독 형태로 사용하는 것 외에도 assign() 을 사용해
        // 직접 파티션을 컨슈머에 명시적으로 할당할 수 있다.
//        consumer.assign(Collections.singleton(new TopicPartition(TOPIC_NAME, 0)));

        // 컨슈머에 할당된 파티션 확인이 가능하다.
//        Set<TopicPartition> assignedTopicPartition = consumer.assignment();

        try {

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                // duration은 브로커로부터 데이터를 가져올 때 컨슈머 버퍼에 데이터를 기다리기 위한 타임아웃 간격.

                for (ConsumerRecord<String, String> record : records) {
                    logger.info("record:{}", record);
                    currentOffset.put(
                        new TopicPartition(record.topic(), record.partition()),
                        new OffsetAndMetadata(record.offset() + 1, null));

                    consumer.commitSync(); // 현재 처리한 offset에 1을 더한 값을 커밋해야한다. 이후에 컨슈머가 poll()을 수행할때 마지막으로 커밋한 오프셋부터 레코드를 리턴하기 때문이다.

                }

//            consumer.commitSync(); // poll() 메서드로 받은 가장 마지막 레코드의 오프셋을 기준으로 커밋.
//            consumer.commitAsync(new OffsetCommitCallback() {
//                @Override
//                public void onComplete(Map<TopicPartition, OffsetAndMetadata> map, Exception e) {
//
//                }
//            }); // 비동기 offset 커밋, OffsetCommitCallback 으로 콜백 처리가 가능하다.
            }

            // 정상적으로 종료되지 않은 컨슈머는 세션 타임아웃이 발생할때까지 컨슈머 그룹에 남게 된다.
            // 이로 인해 실제로는 종료되었지만 더는 동작을 하지 않는 컨슈머가 존재하기 때문에 파티션의 데이터는 소모되지 못하고
            // 컨슈머 랙이 늘어나게 된다.
            // 컨슈머를 안전하게 종료하기 위해 KafkaConsumer 클래스는 wakeup() 메서드를 지원한다.
            // wakeup() 메서드가 실행된 이후 poll() 메서드가 호출되면 WakeupException 예외가 발생한다.
        } catch (WakeupException e) {
            logger.warn("Wakeup consumer");

        } finally {
            consumer.close();
        }
    }

    static class ShutdownThread extends Thread {
        public void run() {
            logger.info("Shutdown hook");
            consumer.wakeup();
        }
    }

    public static class RebalanceListener implements ConsumerRebalanceListener {

        /**
         * 리벨런스가 끝난 뒤에 파티션이 할당 완료되면 호출되는 메서드이다.
         *
         * @param partitions
         */
        @Override
        public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
            logger.warn("Partitions are assigned : " + partitions.toString());

        }

        /**
         * 리벨런스가 시작되기 직전에 호출되는 메서드이다.
         *
         * @param partitions
         */
        @Override
        public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            logger.warn("Partitions are revoked : " + partitions.toString());
            consumer.commitSync();
        }
    }
}