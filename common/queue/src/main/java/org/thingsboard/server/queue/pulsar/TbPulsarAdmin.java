package org.thingsboard.server.queue.pulsar;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.PulsarClientException;
import org.thingsboard.server.queue.TbQueueAdmin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class TbPulsarAdmin implements TbQueueAdmin {
    private final PulsarAdmin client;
    private final Map<String, String> topicConfigs;
    private final Set<String> topics = ConcurrentHashMap.newKeySet();
    private final int numPartitions;

    private  final  String tenantName;


    @SneakyThrows
    public TbPulsarAdmin(TbPulsarSettings settings, Map<String, String> topicConfigs)  {
        this.client = PulsarAdmin.builder().serviceHttpUrl(settings.getServiceUrl()).build();
        this.topicConfigs = topicConfigs;
        List<String> namespaces = this.client.namespaces().getNamespaces(settings.getTenantName());
        namespaces.forEach(n -> {
            try {
                topics.addAll(client.topics().getList(n));
            } catch (PulsarAdminException e) {
                log.error("Failed to get all topics.", e);
            }
        });

        String numPartitionsStr = topicConfigs.get(TbPulsarTopicConfigs.NUM_PARTITIONS_SETTING);
        if (numPartitionsStr != null) {
            numPartitions = Integer.parseInt(numPartitionsStr);
            topicConfigs.remove("partitions");
        } else {
            numPartitions = 1;
        }

        tenantName = settings.getTenantName();
    }

    @Override
    public void createTopicIfNotExists(String topic) {
        if (topics.contains(topic)) {
            return;
        }
        try {
            client.topics().createPartitionedTopic(topic, numPartitions);
            topics.add(topic);

        } catch (PulsarAdminException e) {
            log.error("Failed to get all topics.", e);
        }
    }

    @Override
    public void destroy() {
        if (client != null) {
            client.close();
        }
    }

    @Override
    public void deleteTopic(String topic) {
        try {
            client.topics().delete(topic, true);
        } catch (PulsarAdminException e) {
            log.error("Failed to delete pulsar topic [{}].", topic, e);
        }
    }

}
