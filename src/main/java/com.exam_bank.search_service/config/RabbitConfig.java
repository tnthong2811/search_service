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

    @Value("${search.events.queue}")
    private String searchQueue;

    @Value("${exam.events.file-uploaded-routing-key}")
    private String fileUploadedRoutingKey;

    // Đảm bảo Spring Boot tự động convert JSON thành Java Object
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public TopicExchange examEventsExchange() {
        return new TopicExchange(examExchange);
    }

    @Bean
    public Queue searchEventsQueue() {
        return new Queue(searchQueue, true); // true = durable (không mất tin nhắn khi tắt server)
    }

    @Bean
    public Binding bindingSearchQueue(Queue searchEventsQueue, TopicExchange examEventsExchange) {
        return BindingBuilder.bind(searchEventsQueue).to(examEventsExchange).with(fileUploadedRoutingKey);
    }
}