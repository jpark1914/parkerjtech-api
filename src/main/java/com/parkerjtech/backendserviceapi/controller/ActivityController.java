package com.parkerjtech.backendserviceapi.controller;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.parkerjtech.backendserviceapi.model.ActivityResponse;
import com.parkerjtech.backendserviceapi.model.ItemsResponse;
import com.parkerjtech.backendserviceapi.service.ActivityService;
import com.parkerjtech.backendserviceapi.utils.WorkspaceSessionResolver;
@RestController
public class ActivityController {
    private final WorkspaceSessionResolver sessions;
    private final ActivityService service;
    public ActivityController(WorkspaceSessionResolver sessions,ActivityService service) { this.sessions=sessions; this.service=service; }
    @GetMapping("/api/demo/activity")
    public ItemsResponse<ActivityResponse> list(HttpServletRequest request) {
        return new ItemsResponse<>(service.list(sessions.requireWorkspaceId(request)).stream().map(ActivityResponse::from).toList());
    }
}
