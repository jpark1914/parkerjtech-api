package com.parkerjtech.backendserviceapi.api;

public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    public ApiException(int status,String code,String detail) {
        super(detail); this.status=status; this.code=code;
    }
    public int status() { return status; }
    public String code() { return code; }
    public static ApiException workspaceRequired() {
        return new ApiException(401,"WORKSPACE_REQUIRED","Start or refresh your demo session using GET /api/demo/session.");
    }
}
