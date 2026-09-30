package com.deviceapi.web.dto;

import com.deviceapi.domain.DeviceState;
import jakarta.validation.constraints.AssertTrue;

/**
 * Shared body shape for both PUT and PATCH - every field is optional; a
 * field left out of the JSON body (or sent as null) means "leave
 * unchanged" (see docs/DECISIONS.md #7). PUT is simply the case where the
 * caller happens to send all three.
 */
public record UpdateDeviceRequest(
        String name,
        String brand,
        DeviceState state
) {

    @AssertTrue(message = "must not be blank when provided")
    private boolean isNameValid() {
        return name == null || !name.isBlank();
    }

    @AssertTrue(message = "must not be blank when provided")
    private boolean isBrandValid() {
        return brand == null || !brand.isBlank();
    }
}
