package com.deviceapi.web;

import com.deviceapi.domain.Device;
import com.deviceapi.domain.DeviceState;
import com.deviceapi.service.DeviceService;
import com.deviceapi.web.dto.CreateDeviceRequest;
import com.deviceapi.web.dto.DeviceResponse;
import com.deviceapi.web.dto.UpdateDeviceRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    public ResponseEntity<DeviceResponse> create(
            @Valid @RequestBody CreateDeviceRequest request,
            UriComponentsBuilder uriBuilder
    ) {
        Device device = deviceService.create(request.name(), request.brand(), request.state());
        var location = uriBuilder.path("/api/v1/devices/{id}").build(device.getId());
        return ResponseEntity.created(location).body(DeviceMapper.toResponse(device));
    }

    @GetMapping("/{id}")
    public DeviceResponse getById(@PathVariable UUID id) {
        return DeviceMapper.toResponse(deviceService.getById(id));
    }

    @GetMapping
    public Page<DeviceResponse> search(
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) DeviceState state,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return deviceService.search(brand, state, pageable).map(DeviceMapper::toResponse);
    }

    @PutMapping("/{id}")
    public DeviceResponse replace(@PathVariable UUID id, @Valid @RequestBody UpdateDeviceRequest request) {
        return update(id, request);
    }

    @PatchMapping("/{id}")
    public DeviceResponse patch(@PathVariable UUID id, @Valid @RequestBody UpdateDeviceRequest request) {
        return update(id, request);
    }

    private DeviceResponse update(UUID id, UpdateDeviceRequest request) {
        Device device = deviceService.update(id, request.name(), request.brand(), request.state());
        return DeviceMapper.toResponse(device);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deviceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
