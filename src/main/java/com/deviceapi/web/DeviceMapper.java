package com.deviceapi.web;

import com.deviceapi.domain.Device;
import com.deviceapi.web.dto.DeviceResponse;

final class DeviceMapper {

    private DeviceMapper() {
    }

    static DeviceResponse toResponse(Device device) {
        return new DeviceResponse(
                device.getId(),
                device.getName(),
                device.getBrand(),
                device.getState(),
                device.getCreationTime()
        );
    }
}
