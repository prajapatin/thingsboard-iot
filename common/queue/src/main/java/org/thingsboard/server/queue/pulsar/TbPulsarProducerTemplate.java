package org.thingsboard.server.queue.pulsar;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import lombok.Builder;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.thingsboard.server.common.msg.queue.TopicPartitionInfo;
import org.thingsboard.server.queue.TbQueueCallback;
import org.thingsboard.server.queue.TbQueueMsg;
import org.thingsboard.server.queue.TbQueueProducer;
import org.thingsboard.server.queue.common.DefaultTbQueueMsg;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

@Slf4j
public class TbPulsarProducerTemplate<T extends TbQueueMsg> implements TbQueueProducer<T> {

    private final Gson gson = new Gson();
    private final PulsarClient pulsarClient;

    private final String defaultTopic;

    @Getter
    private final TbPulsarSettings settings;

    private final Set<TopicPartitionInfo> topics;

    private final ListeningExecutorService producerExecutor;

    @SneakyThrows
    @Builder
    public TbPulsarProducerTemplate(TbPulsarSettings settings, String defaultTopic)  {
        this.settings = settings;
        producerExecutor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        this.defaultTopic = defaultTopic;
        topics = ConcurrentHashMap.newKeySet();
        this.pulsarClient = PulsarClient.builder()
                .serviceUrl(settings.getServiceUrl())
                .build();
    }

    @Override
    public void init() {

    }

    @Override
    public String getDefaultTopic() {
        return defaultTopic;
    }

    @Override
    public void send(TopicPartitionInfo tpi, T msg, TbQueueCallback callback) {
        try {
            Producer producer = pulsarClient.newProducer(Schema.BYTES).topic(tpi.getFullTopicName()).create();
            producer.send(gson.toJson(new DefaultTbQueueMsg(msg)).getBytes());
            producer.closeAsync();
            if (callback != null) {
                callback.onSuccess(null);
            }
        } catch (IOException e) {
            log.error("Failed publish message: [{}].", msg, e);
            if (callback != null) {
                callback.onFailure(e);
            }
        }
    }

    @Override
    public void stop() {
        if (producerExecutor != null) {
            producerExecutor.shutdownNow();
        }
        if (pulsarClient != null) {
            pulsarClient.closeAsync();
        }
    }
}
