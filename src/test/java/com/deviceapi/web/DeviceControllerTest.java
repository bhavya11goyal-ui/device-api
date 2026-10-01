package com.deviceapi.web;

import com.deviceapi.domain.Device;
import com.deviceapi.domain.DeviceState;
import com.deviceapi.exception.DeviceInUseException;
import com.deviceapi.exception.DeviceNameBrandLockedException;
import com.deviceapi.exception.DeviceNotFoundException;
import com.deviceapi.service.DeviceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceController.class)
class DeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DeviceService deviceService;

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        Device device = Device.create("Pixel 9", "Google", DeviceState.AVAILABLE);
        when(deviceService.create("Pixel 9", "Google", DeviceState.AVAILABLE)).thenReturn(device);

        mockMvc.perform(post("/api/v1/devices")
                        .contentType("application/json")
                        .content("""
                                {"name":"Pixel 9","brand":"Google","state":"AVAILABLE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/devices/" + device.getId()))
                .andExpect(jsonPath("$.id").value(device.getId().toString()))
                .andExpect(jsonPath("$.name").value("Pixel 9"))
                .andExpect(jsonPath("$.brand").value("Google"))
                .andExpect(jsonPath("$.state").value("AVAILABLE"));
    }

    @Test
    void createWithBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/devices")
                        .contentType("application/json")
                        .content("""
                                {"name":"","brand":"Google","state":"AVAILABLE"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByIdReturns200WhenFound() throws Exception {
        Device device = Device.create("Pixel 9", "Google", DeviceState.AVAILABLE);
        when(deviceService.getById(device.getId())).thenReturn(device);

        mockMvc.perform(get("/api/v1/devices/{id}", device.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(device.getId().toString()));
    }

    @Test
    void getByIdReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(deviceService.getById(id)).thenThrow(new DeviceNotFoundException(id));

        mockMvc.perform(get("/api/v1/devices/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Device Not Found"));
    }

    @Test
    void unexpectedExceptionReturns500WithGenericDetailNotInternals() throws Exception {
        UUID id = UUID.randomUUID();
        when(deviceService.getById(id)).thenThrow(new IllegalStateException("connection pool exhausted: secret-internal-detail"));

        mockMvc.perform(get("/api/v1/devices/{id}", id))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-internal-detail"))));
    }

    @Test
    void searchWithNoParamsListsAllDevices() throws Exception {
        Device device = Device.create("Pixel 9", "Google", DeviceState.AVAILABLE);
        when(deviceService.search(isNull(), isNull(), any())).thenReturn(new PageImpl<>(List.of(device)));

        mockMvc.perform(get("/api/v1/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void searchWithBrandFiltersByBrand() throws Exception {
        Device device = Device.create("Pixel 9", "Google", DeviceState.AVAILABLE);
        when(deviceService.search(eq("Google"), isNull(), any())).thenReturn(new PageImpl<>(List.of(device)));

        mockMvc.perform(get("/api/v1/devices").param("brand", "Google"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].brand").value("Google"));

        verify(deviceService).search(eq("Google"), isNull(), any());
    }

    @Test
    void searchWithStateFiltersByState() throws Exception {
        Device device = Device.create("Pixel 9", "Google", DeviceState.IN_USE);
        when(deviceService.search(isNull(), eq(DeviceState.IN_USE), any())).thenReturn(new PageImpl<>(List.of(device)));

        mockMvc.perform(get("/api/v1/devices").param("state", "IN_USE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].state").value("IN_USE"));

        verify(deviceService).search(isNull(), eq(DeviceState.IN_USE), any());
    }

    @Test
    void searchWithBrandAndStateCombinesBothFilters() throws Exception {
        Device device = Device.create("Pixel 9", "Google", DeviceState.IN_USE);
        when(deviceService.search(eq("Google"), eq(DeviceState.IN_USE), any())).thenReturn(new PageImpl<>(List.of(device)));

        mockMvc.perform(get("/api/v1/devices").param("brand", "Google").param("state", "IN_USE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        verify(deviceService).search(eq("Google"), eq(DeviceState.IN_USE), any());
    }

    @Test
    void putReplacesDeviceAndReturns200() throws Exception {
        Device updated = Device.create("Pixel 9 Pro", "Google", DeviceState.IN_USE);
        UUID id = updated.getId();
        when(deviceService.update(eq(id), eq("Pixel 9 Pro"), eq("Google"), eq(DeviceState.IN_USE)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/v1/devices/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {"name":"Pixel 9 Pro","brand":"Google","state":"IN_USE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pixel 9 Pro"));
    }

    @Test
    void putTriggeringNameBrandLockReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(deviceService.update(eq(id), any(), any(), any()))
                .thenThrow(new DeviceNameBrandLockedException(id));

        mockMvc.perform(put("/api/v1/devices/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {"name":"New Name","brand":"Google","state":"IN_USE"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Device Locked"));
    }

    @Test
    void putTriggeringConcurrentModificationReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(deviceService.update(eq(id), any(), any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Device.class, id));

        mockMvc.perform(put("/api/v1/devices/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {"name":"New Name","brand":"Google","state":"IN_USE"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Concurrent Modification"));
    }

    @Test
    void patchWithOnlyStateLeavesOtherFieldsUntouched() throws Exception {
        Device updated = Device.create("Pixel 9", "Google", DeviceState.AVAILABLE);
        UUID id = updated.getId();
        when(deviceService.update(id, null, null, DeviceState.AVAILABLE)).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/devices/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {"state":"AVAILABLE"}
                                """))
                .andExpect(status().isOk());

        verify(deviceService).update(id, null, null, DeviceState.AVAILABLE);
    }

    @Test
    void patchWithBlankNameReturns400() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/devices/{id}", id)
                        .contentType("application/json")
                        .content("""
                                {"name":"   "}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteReturns204WhenNotInUse() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/devices/{id}", id))
                .andExpect(status().isNoContent());

        verify(deviceService).delete(id);
    }

    @Test
    void deleteReturns409WhenInUse() throws Exception {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new DeviceInUseException(id)).when(deviceService).delete(id);

        mockMvc.perform(delete("/api/v1/devices/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Device In Use"));
    }
}
