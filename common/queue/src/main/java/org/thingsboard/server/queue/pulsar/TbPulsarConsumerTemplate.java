package org.thingsboard.server.queue.pulsar;

import com.google.gson.Gson;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.*;
import org.springframework.util.StopWatch;
import org.thingsboard.server.queue.TbQueueAdmin;
import org.thingsboard.server.queue.TbQueueMsg;
import org.thingsboard.server.queue.TbQueueMsgDecoder;
import org.thingsboard.server.queue.common.AbstractTbQueueConsumerTemplate;
import org.thingsboard.server.queue.common.DefaultTbQueueMsg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TbPulsarConsumerTemplate<T extends TbQueueMsg> extends AbstractTbQueueConsumerTemplate<Message<byte[]>, T> {
    private final TbQueueAdmin admin;
    private final Gson gson = new Gson();
    private final PulsarClient pulsarClient;
    private final TbPulsarSettings settings;
    private final TbQueueMsgDecoder<T> decoder;
    private Consumer<byte[]> consumer;

    @SneakyThrows
    @Builder
    public TbPulsarConsumerTemplate(TbPulsarSettings settings, TbQueueMsgDecoder<T> decoder, String topic, TbQueueAdmin admin)  {
        super(topic);
        this.settings = settings;
        this.decoder = decoder;
        this.pulsarClient = PulsarClient.builder()
                .serviceUrl(this.settings.getServiceUrl())
                .build();
        this.admin = admin;
    }

    @Override
    protected List<Message<byte[]>> doPoll(long durationInMillis) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        log.trace("poll topic {} maxDuration {}", getTopic(), durationInMillis);
        List<Message<byte[]>> records = new ArrayList<>();
        Message<byte[]> msg = null;
        int maxRecordsInOnePoll = 0;
        do {
            try {
                if (consumer != null) {
                    msg = consumer.receive((int) durationInMillis, TimeUnit.MILLISECONDS);
                    if (msg != null) {
                        records.add(msg);
                        maxRecordsInOnePoll++;
                        consumer.acknowledge(msg);
                    }
                }
            } catch (PulsarClientException e) {
                log.warn("Error in receiving messages: {}", e.getMessage());
            }

        } while (msg != null && maxRecordsInOnePoll < settings.getMaxPollRecords());

        stopWatch.stop();
        log.trace("poll topic {} took {}ms", getTopic(), stopWatch.getTotalTimeMillis());

        return records;
    }

    @Override
    protected T decode(Message<byte[]> record) throws IOException {
        DefaultTbQueueMsg msg = gson.fromJson(new String(record.getValue()), DefaultTbQueueMsg.class);
        return decoder.decode(msg);
    }

    @Override
    protected void doSubscribe(List<String> topicNames) {
        try {
            if (!topicNames.isEmpty()) {
                topicNames.forEach(admin::createTopicIfNotExists);
                log.info("subscribe topics {}", topicNames);
                consumer = this.pulsarClient.newConsumer(Schema.BYTES)
                        .topics(topicNames)
                        .subscriptionName("default")
                        .subscribe();
            } else {
                log.info("unsubscribe due to empty topic list");
                consumer.unsubscribe();
            }
        } catch (PulsarClientException e) {
            log.error("Pulsar client error.", e);
        }
    }

    @Override
    protected void doCommit() {

    }

    @Override
    protected void doUnsubscribe() {
        log.info("unsubscribe topic and close consumer for topic {}", getTopic());
        consumer.unsubscribeAsync();
        pulsarClient.closeAsync();
    }
}
