package org.thingsboard.server.queue.provider;

import com.google.protobuf.util.JsonFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.queue.Queue;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.gen.js.JsInvokeProtos;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.queue.TbQueueAdmin;
import org.thingsboard.server.queue.TbQueueConsumer;
import org.thingsboard.server.queue.TbQueueProducer;
import org.thingsboard.server.queue.TbQueueRequestTemplate;
import org.thingsboard.server.queue.common.DefaultTbQueueRequestTemplate;
import org.thingsboard.server.queue.common.TbProtoJsQueueMsg;
import org.thingsboard.server.queue.common.TbProtoQueueMsg;
import org.thingsboard.server.queue.discovery.NotificationsTopicService;
import org.thingsboard.server.queue.discovery.TbServiceInfoProvider;
import org.thingsboard.server.queue.pulsar.*;
import org.thingsboard.server.queue.settings.*;

import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnExpression("'${queue.type:null}'=='pulsar' && '${service.type:null}'=='monolith'")
public class PulsarMonolithQueueFactory implements TbCoreQueueFactory, TbRuleEngineQueueFactory, TbVersionControlQueueFactory {

    private final NotificationsTopicService notificationsTopicService;
    private final TbPulsarSettings pulsarSettings;
    private final TbServiceInfoProvider serviceInfoProvider;
    private final TbQueueCoreSettings coreSettings;
    private final TbQueueRuleEngineSettings ruleEngineSettings;
    private final TbQueueTransportApiSettings transportApiSettings;
    private final TbQueueTransportNotificationSettings transportNotificationSettings;
    private final TbQueueRemoteJsInvokeSettings jsInvokeSettings;
    private final TbQueueVersionControlSettings vcSettings;
    private final TbQueueAdmin coreAdmin;
    private final TbQueueAdmin ruleEngineAdmin;
    private final TbQueueAdmin jsExecutorRequestAdmin;
    private final TbQueueAdmin jsExecutorResponseAdmin;
    private final TbQueueAdmin transportApiRequestAdmin;
    private final TbQueueAdmin transportApiResponseAdmin;
    private final TbQueueAdmin notificationAdmin;
    private final TbQueueAdmin fwUpdatesAdmin;
    private final TbQueueAdmin vcAdmin;

    private final AtomicLong consumerCount = new AtomicLong();

    public PulsarMonolithQueueFactory(NotificationsTopicService notificationsTopicService, TbPulsarSettings pulsarSettings,
                                     TbServiceInfoProvider serviceInfoProvider,
                                     TbQueueCoreSettings coreSettings,
                                     TbQueueRuleEngineSettings ruleEngineSettings,
                                     TbQueueTransportApiSettings transportApiSettings,
                                     TbQueueTransportNotificationSettings transportNotificationSettings,
                                     TbQueueRemoteJsInvokeSettings jsInvokeSettings,
                                     TbQueueVersionControlSettings vcSettings,
                                     TbPulsarTopicConfigs pulsarTopicConfigs) {
        this.notificationsTopicService = notificationsTopicService;
        this.pulsarSettings = pulsarSettings;
        this.serviceInfoProvider = serviceInfoProvider;
        this.coreSettings = coreSettings;
        this.ruleEngineSettings = ruleEngineSettings;
        this.transportApiSettings = transportApiSettings;
        this.transportNotificationSettings = transportNotificationSettings;
        this.jsInvokeSettings = jsInvokeSettings;
        this.vcSettings = vcSettings;

        this.coreAdmin = new TbPulsarAdmin(pulsarSettings, pulsarTopicConfigs.getCoreConfigs());
        this.ruleEngineAdmin = new TbPulsarAdmin(pulsarSettings, pulsarTopicConfigs.getRuleEngineConfigs());
        this.jsExecutorRequestAdmin = new TbPulsarAdmin(pulsarSettings, pulsarTopicConfigs.getJsExecutorRequestConfigs());
        this.jsExecutorResponseAdmin = new TbPulsarAdmin(pulsarSettings, pulsarTopicConfigs.getJsExecutorResponseConfigs());
        this.transportApiRequestAdmin = new TbPulsarAdmin(pulsarSettings, pulsarTopicConfigs.getTransportApiRequestConfigs());
        this.transportApiResponseAdmin = new TbPulsarAdmin(pulsarSettings, pulsarTopicConfigs.getTransportApiResponseConfigs());
        this.notificationAdmin = new TbPulsarAdmin(pulsarSettings, pulsarTopicConfigs.getNotificationsConfigs());
        this.fwUpdatesAdmin = new TbPulsarAdmin(pulsarSettings, pulsarTopicConfigs.getFwUpdatesConfigs());
        this.vcAdmin = new TbPulsarAdmin(pulsarSettings, pulsarTopicConfigs.getVcConfigs());
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<TransportProtos.ToUsageStatsServiceMsg>> createToUsageStatsServiceMsgProducer() {
        TbPulsarProducerTemplate.TbPulsarProducerTemplateBuilder<TbProtoQueueMsg<TransportProtos.ToUsageStatsServiceMsg>> requestBuilder = TbPulsarProducerTemplate.builder();
        requestBuilder.settings(pulsarSettings);
        requestBuilder.defaultTopic(coreSettings.getUsageStatsTopic());
        return requestBuilder.build();
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<TransportProtos.ToTransportMsg>> createTransportNotificationsMsgProducer() {
        TbPulsarProducerTemplate.TbPulsarProducerTemplateBuilder<TbProtoQueueMsg<TransportProtos.ToTransportMsg>> requestBuilder = TbPulsarProducerTemplate.builder();
        requestBuilder.settings(pulsarSettings);
        requestBuilder.defaultTopic(transportNotificationSettings.getNotificationsTopic());
        return requestBuilder.build();
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<TransportProtos.ToRuleEngineMsg>> createRuleEngineMsgProducer() {
        TbPulsarProducerTemplate.TbPulsarProducerTemplateBuilder<TbProtoQueueMsg<TransportProtos.ToRuleEngineMsg>> requestBuilder = TbPulsarProducerTemplate.builder();
        requestBuilder.settings(pulsarSettings);
        requestBuilder.defaultTopic(ruleEngineSettings.getTopic());
        return requestBuilder.build();
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<TransportProtos.ToRuleEngineNotificationMsg>> createRuleEngineNotificationsMsgProducer() {
        TbPulsarProducerTemplate.TbPulsarProducerTemplateBuilder<TbProtoQueueMsg<TransportProtos.ToRuleEngineNotificationMsg>> requestBuilder = TbPulsarProducerTemplate.builder();
        requestBuilder.settings(pulsarSettings);
        requestBuilder.defaultTopic(ruleEngineSettings.getTopic());
        return requestBuilder.build();
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<TransportProtos.ToCoreMsg>> createTbCoreMsgProducer() {
        TbPulsarProducerTemplate.TbPulsarProducerTemplateBuilder<TbProtoQueueMsg<TransportProtos.ToCoreMsg>> requestBuilder = TbPulsarProducerTemplate.builder();
        requestBuilder.settings(pulsarSettings);
        requestBuilder.defaultTopic(coreSettings.getTopic());
        return requestBuilder.build();
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<TransportProtos.ToCoreNotificationMsg>> createTbCoreNotificationsMsgProducer() {
        TbPulsarProducerTemplate.TbPulsarProducerTemplateBuilder<TbProtoQueueMsg<TransportProtos.ToCoreNotificationMsg>> requestBuilder = TbPulsarProducerTemplate.builder();
        requestBuilder.settings(pulsarSettings);
        requestBuilder.defaultTopic(coreSettings.getTopic());
        return requestBuilder.build();
    }

    @Override
    public TbQueueConsumer<TbProtoQueueMsg<TransportProtos.ToVersionControlServiceMsg>> createToVersionControlMsgConsumer() {
        TbPulsarConsumerTemplate.TbPulsarConsumerTemplateBuilder<TbProtoQueueMsg<TransportProtos.ToVersionControlServiceMsg>> consumerBuilder = TbPulsarConsumerTemplate.builder();
        consumerBuilder.settings(pulsarSettings);
        consumerBuilder.topic(vcSettings.getTopic());
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), TransportProtos.ToVersionControlServiceMsg.parseFrom(msg.getData()), msg.getHeaders()));
        consumerBuilder.admin(vcAdmin);
        return consumerBuilder.build();
    }

    @Override
    public TbQueueConsumer<TbProtoQueueMsg<TransportProtos.ToCoreMsg>> createToCoreMsgConsumer() {
        TbPulsarConsumerTemplate.TbPulsarConsumerTemplateBuilder<TbProtoQueueMsg<TransportProtos.ToCoreMsg>> consumerBuilder = TbPulsarConsumerTemplate.builder();
        consumerBuilder.settings(pulsarSettings);
        consumerBuilder.topic(coreSettings.getTopic());
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), TransportProtos.ToCoreMsg.parseFrom(msg.getData()), msg.getHeaders()));
        consumerBuilder.admin(coreAdmin);
        return consumerBuilder.build();
    }

    @Override
    public TbQueueConsumer<TbProtoQueueMsg<TransportProtos.ToUsageStatsServiceMsg>> createToUsageStatsServiceMsgConsumer() {
        TbPulsarConsumerTemplate.TbPulsarConsumerTemplateBuilder<TbProtoQueueMsg<TransportProtos.ToUsageStatsServiceMsg>> consumerBuilder = TbPulsarConsumerTemplate.builder();
        consumerBuilder.settings(pulsarSettings);
        consumerBuilder.topic(coreSettings.getUsageStatsTopic());
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), TransportProtos.ToUsageStatsServiceMsg.parseFrom(msg.getData()), msg.getHeaders()));
        consumerBuilder.admin(coreAdmin);
        return consumerBuilder.build();
    }

    @Override
    public TbQueueConsumer<TbProtoQueueMsg<TransportProtos.ToOtaPackageStateServiceMsg>> createToOtaPackageStateServiceMsgConsumer() {
        TbPulsarConsumerTemplate.TbPulsarConsumerTemplateBuilder<TbProtoQueueMsg<TransportProtos.ToOtaPackageStateServiceMsg>> consumerBuilder = TbPulsarConsumerTemplate.builder();
        consumerBuilder.settings(pulsarSettings);
        consumerBuilder.topic(coreSettings.getOtaPackageTopic());
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), TransportProtos.ToOtaPackageStateServiceMsg.parseFrom(msg.getData()), msg.getHeaders()));
        consumerBuilder.admin(fwUpdatesAdmin);
        return consumerBuilder.build();
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<TransportProtos.ToOtaPackageStateServiceMsg>> createToOtaPackageStateServiceMsgProducer() {
        TbPulsarProducerTemplate.TbPulsarProducerTemplateBuilder<TbProtoQueueMsg<TransportProtos.ToOtaPackageStateServiceMsg>> requestBuilder = TbPulsarProducerTemplate.builder();
        requestBuilder.settings(pulsarSettings);
        requestBuilder.defaultTopic(coreSettings.getOtaPackageTopic());
        return requestBuilder.build();
    }

    @Override
    public TbQueueConsumer<TbProtoQueueMsg<TransportProtos.ToRuleEngineMsg>> createToRuleEngineMsgConsumer(Queue configuration) {
        TbPulsarConsumerTemplate.TbPulsarConsumerTemplateBuilder<TbProtoQueueMsg<TransportProtos.ToRuleEngineMsg>> consumerBuilder = TbPulsarConsumerTemplate.builder();
        consumerBuilder.settings(pulsarSettings);
        consumerBuilder.topic(configuration.getTopic());
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), TransportProtos.ToRuleEngineMsg.parseFrom(msg.getData()), msg.getHeaders()));
        consumerBuilder.admin(ruleEngineAdmin);
        return consumerBuilder.build();
    }

    @Override
    public TbQueueConsumer<TbProtoQueueMsg<TransportProtos.ToRuleEngineNotificationMsg>> createToRuleEngineNotificationsMsgConsumer() {
        TbPulsarConsumerTemplate.TbPulsarConsumerTemplateBuilder<TbProtoQueueMsg<TransportProtos.ToRuleEngineNotificationMsg>> consumerBuilder = TbPulsarConsumerTemplate.builder();
        consumerBuilder.settings(pulsarSettings);
        consumerBuilder.topic(notificationsTopicService.getNotificationsTopic(ServiceType.TB_RULE_ENGINE, serviceInfoProvider.getServiceId()).getFullTopicName());
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), TransportProtos.ToRuleEngineNotificationMsg.parseFrom(msg.getData()), msg.getHeaders()));
        consumerBuilder.admin(notificationAdmin);
        return consumerBuilder.build();
    }

    @Override
    public TbQueueConsumer<TbProtoQueueMsg<TransportProtos.ToCoreNotificationMsg>> createToCoreNotificationsMsgConsumer() {
        TbPulsarConsumerTemplate.TbPulsarConsumerTemplateBuilder<TbProtoQueueMsg<TransportProtos.ToCoreNotificationMsg>> consumerBuilder = TbPulsarConsumerTemplate.builder();
        consumerBuilder.settings(pulsarSettings);
        consumerBuilder.topic(notificationsTopicService.getNotificationsTopic(ServiceType.TB_CORE, serviceInfoProvider.getServiceId()).getFullTopicName());
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), TransportProtos.ToCoreNotificationMsg.parseFrom(msg.getData()), msg.getHeaders()));
        consumerBuilder.admin(notificationAdmin);
        return consumerBuilder.build();
    }

    @Override
    public TbQueueConsumer<TbProtoQueueMsg<TransportProtos.TransportApiRequestMsg>> createTransportApiRequestConsumer() {
        TbPulsarConsumerTemplate.TbPulsarConsumerTemplateBuilder<TbProtoQueueMsg<TransportProtos.TransportApiRequestMsg>> consumerBuilder = TbPulsarConsumerTemplate.builder();
        consumerBuilder.settings(pulsarSettings);
        consumerBuilder.topic(transportApiSettings.getRequestsTopic());
        consumerBuilder.decoder(msg -> new TbProtoQueueMsg<>(msg.getKey(), TransportProtos.TransportApiRequestMsg.parseFrom(msg.getData()), msg.getHeaders()));
        consumerBuilder.admin(transportApiRequestAdmin);
        return consumerBuilder.build();
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<TransportProtos.TransportApiResponseMsg>> createTransportApiResponseProducer() {
        TbPulsarProducerTemplate.TbPulsarProducerTemplateBuilder<TbProtoQueueMsg<TransportProtos.TransportApiResponseMsg>> requestBuilder = TbPulsarProducerTemplate.builder();
        requestBuilder.settings(pulsarSettings);
        requestBuilder.defaultTopic(transportApiSettings.getResponsesTopic());
        return requestBuilder.build();
    }

    @Override
    @Bean
    public TbQueueRequestTemplate<TbProtoJsQueueMsg<JsInvokeProtos.RemoteJsRequest>, TbProtoQueueMsg<JsInvokeProtos.RemoteJsResponse>> createRemoteJsRequestTemplate() {
        TbPulsarProducerTemplate.TbPulsarProducerTemplateBuilder<TbProtoJsQueueMsg<JsInvokeProtos.RemoteJsRequest>> requestBuilder = TbPulsarProducerTemplate.builder();
        requestBuilder.settings(pulsarSettings);
        requestBuilder.defaultTopic(jsInvokeSettings.getRequestTopic());

        TbPulsarConsumerTemplate.TbPulsarConsumerTemplateBuilder<TbProtoQueueMsg<JsInvokeProtos.RemoteJsResponse>> responseBuilder = TbPulsarConsumerTemplate.builder();
        responseBuilder.settings(pulsarSettings);
        responseBuilder.topic(jsInvokeSettings.getResponseTopic() + "." + serviceInfoProvider.getServiceId());
        responseBuilder.decoder(msg -> {
                    JsInvokeProtos.RemoteJsResponse.Builder builder = JsInvokeProtos.RemoteJsResponse.newBuilder();
                    JsonFormat.parser().ignoringUnknownFields().merge(new String(msg.getData(), StandardCharsets.UTF_8), builder);
                    return new TbProtoQueueMsg<>(msg.getKey(), builder.build(), msg.getHeaders());
                }
        );
        responseBuilder.admin(jsExecutorResponseAdmin);

        DefaultTbQueueRequestTemplate.DefaultTbQueueRequestTemplateBuilder
                <TbProtoJsQueueMsg<JsInvokeProtos.RemoteJsRequest>, TbProtoQueueMsg<JsInvokeProtos.RemoteJsResponse>> builder = DefaultTbQueueRequestTemplate.builder();
        builder.queueAdmin(jsExecutorResponseAdmin);
        builder.requestTemplate(requestBuilder.build());
        builder.responseTemplate(responseBuilder.build());
        builder.maxPendingRequests(jsInvokeSettings.getMaxPendingRequests());
        builder.maxRequestTimeout(jsInvokeSettings.getMaxRequestsTimeout());
        builder.pollInterval(jsInvokeSettings.getResponsePollInterval());
        return builder.build();
    }

    @Override
    public TbQueueProducer<TbProtoQueueMsg<TransportProtos.ToVersionControlServiceMsg>> createVersionControlMsgProducer() {
        TbPulsarProducerTemplate.TbPulsarProducerTemplateBuilder<TbProtoQueueMsg<TransportProtos.ToVersionControlServiceMsg>> requestBuilder = TbPulsarProducerTemplate.builder();
        requestBuilder.settings(pulsarSettings);
        requestBuilder.defaultTopic(vcSettings.getTopic());
        return requestBuilder.build();
    }

    @PreDestroy
    private void destroy(){
        if (coreAdmin != null) {
            coreAdmin.destroy();
        }
        if (ruleEngineAdmin != null) {
            ruleEngineAdmin.destroy();
        }
        if (jsExecutorRequestAdmin != null) {
            jsExecutorRequestAdmin.destroy();
        }
        if (jsExecutorResponseAdmin != null) {
            jsExecutorResponseAdmin.destroy();
        }
        if (transportApiRequestAdmin != null) {
            transportApiRequestAdmin.destroy();
        }
        if (transportApiResponseAdmin != null) {
            transportApiResponseAdmin.destroy();
        }
        if (notificationAdmin != null) {
            notificationAdmin.destroy();
        }
        if (fwUpdatesAdmin != null) {
            fwUpdatesAdmin.destroy();
        }
        if (vcAdmin != null) {
            vcAdmin.destroy();
        }
    }
}
