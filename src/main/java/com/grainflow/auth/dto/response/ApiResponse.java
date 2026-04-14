package com.grainflow.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Generic wrapper for all API responses — provides consistent structure across all endpoints
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> {

    private String status;
    private String message;
    private T data;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    // Successful response with data and message
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .status("success")
                .message(message)
                .data(data)
                .build();
    }

    // Successful response with data only
    public static <T> ApiResponse<T> success(T data) {
        return success(data, "OK");
    }

    // Error response — data can carry field errors or null
    public static <T> ApiResponse<T> error(T data, String message) {
        return ApiResponse.<T>builder()
                .status("error")
                .message(message)
                .data(data)
                .build();
    }

    // Error response without data
    public static <T> ApiResponse<T> error(String message) {
        return error(null, message);
    }
}
