package com.example;

import java.util.Collection;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * 컨슈머 그룹에서 컨슈머가 추가 또는 제거되면 파티션을 컨슈머에 재항당하는 과정인 리밸런스가 일어난다.
 * poll() 메서드를 통해 반환받은 데이터를 모두 처리하기 전에 리밸런스가 발생하면 데이터를 중복 처리할 수 있다.
 * poll() 메서드를 통해 받은 데이터 중 일부를 처리했으나 커밋하지 않았기 때문이다.
 *
 * 리밸런스 발생 시 데이터를 중복 처리하지 않게 하기 위해서는 리밸런스 발생 시 처리한 데이터를 기준으로 커밋을 시도해야 한다.
 *
 * 리밸런스 발생을 감지하기 위해 카프카 라이브러리는 ConsumerRebalanceListener 인터페이스를 지원한다.
 *
 *
 */
public class RebalanceListener implements ConsumerRebalanceListener {
    private final static Logger logger = LoggerFactory.getLogger(RebalanceListener.class);

    /**
     * 리벨런스가 끝난 뒤에 파티션이 할당 완료되면 호출되는 메서드이다.
     * @param partitions
     */
    @Override
    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        logger.warn("Partitions are assigned : " + partitions.toString());

    }

    /**
     * 리벨런스가 시작되기 직전에 호출되는 메서드이다.
     * @param partitions
     */
    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        logger.warn("Partitions are revoked : " + partitions.toString());
    }
}