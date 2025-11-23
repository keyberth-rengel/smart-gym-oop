package com.smartgym.api.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private final boolean success;
    private final T data;
    private final String message;
    private final ApiError error;
    private final String timestamp;
    private final String path;
    private final String requestId;

    public ApiResponse(boolean success,
                       T data,
                       String message,
                       ApiError error,
                       String timestamp,
                       String path,
                       String requestId) {
        this.success = success;
        this.data = data;
        this.message = message;
        this.error = error;
        this.timestamp = timestamp;
        this.path = path;
        this.requestId = requestId;
    }

    public static <T> ApiResponse<T> ok(T data, String topMessage, String timestamp, String path) {
        return new ApiResponse<>(true, data, topMessage, null, timestamp, path, RequestContext.getRequestId());
    }

    public static <T> ApiResponse<T> fail(String code, String topMessage, Object details,
                                          String timestamp, String path) {
        return new ApiResponse<>(false, null, topMessage, new ApiError(code, details), timestamp, path, RequestContext.getRequestId());
    }

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public String getMessage() {
        return message;
    }

    public ApiError getError() {
        return error;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getPath() {
        return path;
    }

    public String getRequestId() { return requestId; }
}