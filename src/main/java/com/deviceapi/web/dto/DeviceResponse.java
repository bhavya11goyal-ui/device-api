package com.deviceapi.web.dto;

import com.deviceapi.domain.DeviceState;

import java.time.Instant;
import java.util.UUID;

public record DeviceResponse(
        UUID id,
        String name,
        String brand,
        DeviceState state,
        Instant creationTime
) {
}
