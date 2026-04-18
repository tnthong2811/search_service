package com.exam_bank.search_service.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Value("${exam.events.exchange}")
    private String examExchange;

    // --- CẤU HÌNH CHO TÁC VỤ AI (CŨ) ---
    @Value("${search.events.queue}")
    private String searchQueue;

    @Value("${exam.events.file-uploaded-routing-key}")
    private String fileUploadedRoutingKey;

    // --- CẤU HÌNH CHO TÁC VỤ SYNC ELASTICSEARCH (MỚI) ---
    @Value("${search.sync.queue:search.sync.queue}")
    private String syncQueue;

    @Value("${exam.events.sync-routing-key:exam.sync}")
    private String examSyncRoutingKey;

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public TopicExchange examEventsExchange() {
        return new TopicExchange(examExchange);
    }

    // 1. Queue và Binding cho AI
    @Bean
    public Queue searchEventsQueue() {
        return new Queue(searchQueue, true);
    }

    @Bean
    public Binding bindingSearchQueue(Queue searchEventsQueue, TopicExchange examEventsExchange) {
        return BindingBuilder.bind(searchEventsQueue).to(examEventsExchange).with(fileUploadedRoutingKey);
    }

    // 2. Queue và Binding cho Sync Elasticsearch
    @Bean
    public Queue syncEventsQueue() {
        return new Queue(syncQueue, true);
    }

    @Bean
    public Binding bindingSyncQueue(Queue syncEventsQueue, TopicExchange examEventsExchange) {
        return BindingBuilder.bind(syncEventsQueue).to(examEventsExchange).with(examSyncRoutingKey);
    }
}