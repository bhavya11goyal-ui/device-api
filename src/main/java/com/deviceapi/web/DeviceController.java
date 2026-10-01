package com.deviceapi.web;

import com.deviceapi.domain.Device;
import com.deviceapi.domain.DeviceState;
import com.deviceapi.service.DeviceService;
import com.deviceapi.web.dto.DeviceRequest;
import com.deviceapi.web.dto.DeviceResponse;
import com.deviceapi.web.dto.UpdateDeviceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
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
@Tag(name = "Devices", description = "Create, fetch, update, and delete device resources")
public class DeviceController {

    private static final String PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    @Operation(summary = "Create a new device")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Device created"),
            @ApiResponse(responseCode = "400", description = "Validation failed (blank/oversized name or brand, missing state)",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<DeviceResponse> create(
            @Valid @RequestBody DeviceRequest request,
            UriComponentsBuilder uriBuilder
    ) {
        Device device = deviceService.create(request.name(), request.brand(), request.state());
        var location = uriBuilder.path("/api/v1/devices/{id}").build(device.getId());
        return ResponseEntity.created(location).body(DeviceMapper.toResponse(device));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single device by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device found"),
            @ApiResponse(responseCode = "404", description = "No device with that id",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public DeviceResponse getById(@PathVariable UUID id) {
        return DeviceMapper.toResponse(deviceService.getById(id));
    }

    @GetMapping
    @Operation(summary = "Fetch devices, optionally filtered by brand and/or state",
            description = "brand and state are optional and compose (both together AND-filter). "
                    + "Supports standard Spring Data page/size/sort query params; "
                    + "results are newest-first by default.")
    public Page<DeviceResponse> search(
            @Parameter(description = "Exact brand match") @RequestParam(required = false) String brand,
            @Parameter(description = "Exact state match") @RequestParam(required = false) DeviceState state,
            @PageableDefault(size = 20, sort = "creationTime", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return deviceService.search(brand, state, pageable).map(DeviceMapper::toResponse);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Full replacement of a device",
            description = "All fields are required - the body must carry a complete representation. "
                    + "Use PATCH to change only some fields. "
                    + "name/brand cannot change while the device is IN_USE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device replaced"),
            @ApiResponse(responseCode = "400", description = "Validation failed (any field missing, blank, or oversized)",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No device with that id",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "name/brand change rejected (device is IN_USE), or a concurrent modification was detected",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public DeviceResponse replace(@PathVariable UUID id, @Valid @RequestBody DeviceRequest request) {
        Device device = deviceService.update(id, request.name(), request.brand(), request.state());
        return DeviceMapper.toResponse(device);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Partial update of a device",
            description = "Fields omitted from the request body are left unchanged. "
                    + "name/brand cannot change while the device is IN_USE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No device with that id",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "name/brand change rejected (device is IN_USE), or a concurrent modification was detected",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public DeviceResponse patch(@PathVariable UUID id, @Valid @RequestBody UpdateDeviceRequest request) {
        Device device = deviceService.update(id, request.name(), request.brand(), request.state());
        return DeviceMapper.toResponse(device);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a device", description = "A device cannot be deleted while it is IN_USE.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Device deleted"),
            @ApiResponse(responseCode = "404", description = "No device with that id",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Device is IN_USE and cannot be deleted",
                    content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deviceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
