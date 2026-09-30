package com.deviceapi.exception;

import java.util.UUID;

public class DeviceInUseException extends RuntimeException {

    public DeviceInUseException(UUID id) {
        super("Device is in use and cannot be deleted: " + id);
    }
}
