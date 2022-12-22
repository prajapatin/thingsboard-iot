package org.thingsboard.server.queue.pulsar;

import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Slf4j
@ConditionalOnExpression("'${queue.type:null}'=='pulsar'")
@Component
@Data
public class TbPulsarSettings {
    @Value("${queue.pulsar.serviceUrl}")
    @Getter
    private String serviceUrl;

    @Value("${queue.pulsar.tenantName}")
    @Getter
    private String tenantName;

    @Value("${queue.pulsar.max_poll_records:8192}")
    @Getter
    private int maxPollRecords;
}
