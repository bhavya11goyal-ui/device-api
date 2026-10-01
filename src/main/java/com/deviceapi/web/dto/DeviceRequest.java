package com.deviceapi.web.dto;

import com.deviceapi.domain.DeviceState;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A complete device representation, used by both POST (create) and PUT
 * (full replacement) - the two operations take the same thing, so they
 * share one shape rather than having a near-identical record each. Every
 * field is required; PATCH uses {@link UpdateDeviceRequest}, where fields
 * are optional. See docs/DECISIONS.md #7.
 */
public record DeviceRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String brand,
        @NotNull DeviceState state
) {
}
