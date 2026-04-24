package com.grainflow.auth.kafka;

import com.grainflow.auth.entity.Company;
import com.grainflow.auth.repository.CompanyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionStatusConsumer")
class SubscriptionStatusConsumerTest {

    @Mock private CompanyRepository companyRepository;
    @InjectMocks private SubscriptionStatusConsumer consumer;

    @Test
    @DisplayName("handle: company exists → subscriptionStatus updated")
    void handle_companyExists_updatesStatus() {
        UUID companyId = UUID.randomUUID();
        Company company = new Company();
        company.setId(companyId);
        company.setSubscriptionStatus("INACTIVE");

        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));

        consumer.handle(new SubscriptionStatusEvent(companyId, "ACTIVE"));

        assertThat(company.getSubscriptionStatus()).isEqualTo("ACTIVE");
        verify(companyRepository).save(company);
    }

    @Test
    @DisplayName("handle: company not found → no save, no exception")
    void handle_companyNotFound_doesNothing() {
        UUID companyId = UUID.randomUUID();
        when(companyRepository.findById(companyId)).thenReturn(Optional.empty());

        consumer.handle(new SubscriptionStatusEvent(companyId, "ACTIVE"));

        verify(companyRepository, never()).save(any());
    }
}
