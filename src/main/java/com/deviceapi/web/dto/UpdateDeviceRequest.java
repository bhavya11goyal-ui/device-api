package com.deviceapi.web.dto;

import com.deviceapi.domain.DeviceState;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * Shared body shape for both PUT and PATCH - every field is optional; a
 * field left out of the JSON body (or sent as null) means "leave
 * unchanged" (see docs/DECISIONS.md #7). PUT is simply the case where the
 * caller happens to send all three. {@code @Size} treats null as valid by
 * convention (like most built-in constraints), so it composes cleanly
 * with the null-means-unchanged semantics here.
 */
public record UpdateDeviceRequest(
        @Size(max = 255) String name,
        @Size(max = 255) String brand,
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
