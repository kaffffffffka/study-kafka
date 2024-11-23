package com.example;

import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class SimpleProducer {
    private final static Logger logger = LoggerFactory.getLogger(SimpleProducer.class);
    private final static String TOPIC_NAME = "test";
    private final static String BOOTSTRAP_SERVERS = "localhost:9092"; // 전송하고자 하는 카프카 클러스터 서버의 IP/port

    public static void main(String[] args) throws ExecutionException, InterruptedException {

        Properties configs = new Properties();  // KafkaProducer 인스턴스 생성을 위한 프로듀서 옵션 설정
        configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
//        configs.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, CustomPartitioner.class.getName());

        KafkaProducer<String, String> producer = new KafkaProducer<>(configs);
        // 따로 partitioner 지정하지 않을 시 DefaultPartitioner로 설정되어 파티션이 정해짐.
        // Producer API 에서는 UniformStickyPartitioner 와 RoundRobinPartitioner 2개 파티션 제공
        // 카프카 클라이언트 라이브러릴 2.5.0 버전에서는 파티셔너를 지정하지 않은 경우, UniformStickyPartitioner 가 기본 설정

        String messageValue = "testMessage";
        // ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC_NAME, messageValue); // 예제에서는 topic 과 value만 설정
//        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC_NAME,"messageKey", messageValue);
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC_NAME, 0,"messageKey2", messageValue);

//        RecordMetadata metadata = producer.send(record).get(); // 즉각적인 전송이 아닌 프로듀서 내부에 가지고 있다가 배치 형태로 묶어서 전송
//        logger.info(metadata.toString());
        producer.send(record, new ProducerCallback());

        logger.info("{}", record);
        producer.flush(); // 내부 버퍼에 가지고 있던 레코드 배치를 브로커로 전송
        producer.close(); // producer 인스턴스의 리소스들을 안전하게 종료
    }
}