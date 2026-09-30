package com.deviceapi.exception;

import java.util.UUID;

public class DeviceNameBrandLockedException extends RuntimeException {

    public DeviceNameBrandLockedException(UUID id) {
        super("Device name and brand cannot be changed while the device is in use: " + id);
    }
}
