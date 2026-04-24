package com.grainflow.auth.kafka;

import java.util.UUID;

public record SubscriptionStatusEvent(
        UUID companyId,
        String status
) {}
