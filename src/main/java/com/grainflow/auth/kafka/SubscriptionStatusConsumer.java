package com.grainflow.auth.kafka;

import com.grainflow.auth.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionStatusConsumer {

    private final CompanyRepository companyRepository;

    @KafkaListener(topics = "subscription.status.changed", groupId = "auth-service")
    @Transactional
    public void handle(SubscriptionStatusEvent event) {
        log.info("[Kafka] subscription.status.changed received: companyId={} status={}", event.companyId(), event.status());
        companyRepository.findById(event.companyId()).ifPresentOrElse(company -> {
            company.setSubscriptionStatus(event.status());
            companyRepository.save(company);
            log.info("Company subscription updated: companyId={} status={}", event.companyId(), event.status());
        }, () -> log.warn("Company not found: companyId={}", event.companyId()));
    }
}
