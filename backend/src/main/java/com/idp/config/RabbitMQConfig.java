package com.idp.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${idp.queues.fraud-analysis:fraud.analysis.queue}")
    private String fraudAnalysisQueue;

    @Value("${idp.queues.fraud-result:fraud.result.queue}")
    private String fraudResultQueue;

    @Value("${idp.queues.audit-stream:audit.event.queue}")
    private String auditStreamQueue;

    @Value("${idp.queues.flag-updated:flag.updated.queue}")
    private String flagUpdatedQueue;

    @Value("${idp.queues.scaffold-job:job.scaffold.queue}")
    private String scaffoldJobQueue;

    @Value("${idp.queues.exchange:idp.direct.exchange}")
    private String exchange;

    @Value("${idp.queues.routing-key:fraud.analysis.routing.key}")
    private String routingKey;

    @Bean
    public DirectExchange idpExchange() {
        return new DirectExchange(exchange, true, false);
    }

    @Bean
    public Queue fraudAnalysisQueue() {
        return QueueBuilder.durable(fraudAnalysisQueue).build();
    }

    @Bean
    public Queue fraudResultQueue() {
        return QueueBuilder.durable(fraudResultQueue).build();
    }

    @Bean
    public Queue auditStreamQueue() {
        return QueueBuilder.durable(auditStreamQueue).build();
    }

    @Bean
    public Queue flagUpdatedQueue() {
        return QueueBuilder.durable(flagUpdatedQueue).build();
    }

    @Bean
    public Queue scaffoldJobQueue() {
        return QueueBuilder.durable(scaffoldJobQueue).build();
    }

    @Bean
    public Binding fraudAnalysisBinding() {
        return BindingBuilder.bind(fraudAnalysisQueue()).to(idpExchange()).with(routingKey);
    }

    @Bean
    public Binding fraudResultBinding() {
        return BindingBuilder.bind(fraudResultQueue()).to(idpExchange()).with("fraud.result.routing.key");
    }

    @Bean
    public Binding auditStreamBinding() {
        return BindingBuilder.bind(auditStreamQueue()).to(idpExchange()).with("audit.event.routing.key");
    }

    @Bean
    public Binding flagUpdatedBinding() {
        return BindingBuilder.bind(flagUpdatedQueue()).to(idpExchange()).with("flag.updated.routing.key");
    }

    @Bean
    public Binding scaffoldJobBinding() {
        return BindingBuilder.bind(scaffoldJobQueue()).to(idpExchange()).with("job.scaffold.routing.key");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
