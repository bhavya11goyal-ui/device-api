package com.deviceapi.web.dto;

import com.deviceapi.domain.DeviceState;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body for PATCH: every field is optional, and one left out of the JSON
 * body means "leave unchanged" (see docs/DECISIONS.md #7). PUT uses
 * {@link DeviceRequest} instead, where every field is required.
 *
 * <p>Blank-but-present values are rejected with {@code @Pattern} (at
 * least one non-whitespace character) rather than a method-level
 * {@code @AssertTrue}: both work, but a field-level constraint reports
 * the violation against {@code name}/{@code brand}, whereas the method
 * form reports it against the derived property name of the validation
 * method, which means nothing to an API client. Like most built-in
 * constraints, {@code @Pattern} and {@code @Size} treat null as valid, so
 * they compose with the null-means-unchanged semantics.
 */
public record UpdateDeviceRequest(
        @Size(max = 255) @Pattern(regexp = ".*\\S.*", message = "must not be blank when provided") String name,
        @Size(max = 255) @Pattern(regexp = ".*\\S.*", message = "must not be blank when provided") String brand,
        DeviceState state
) {
}
