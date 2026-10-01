package com.deviceapi.web.dto;

import com.deviceapi.domain.DeviceState;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDeviceRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String brand,
        @NotNull DeviceState state
) {
}
