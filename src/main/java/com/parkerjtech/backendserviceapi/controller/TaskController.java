package com.parkerjtech.backendserviceapi.controller;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.parkerjtech.backendserviceapi.model.TaskResponse;
import com.parkerjtech.backendserviceapi.model.ItemsResponse;
import com.parkerjtech.backendserviceapi.service.TaskService;
import com.parkerjtech.backendserviceapi.utils.WorkspaceSessionResolver;
@RestController
public class TaskController {
    private final WorkspaceSessionResolver sessions;
    private final TaskService service;
    public TaskController(WorkspaceSessionResolver sessions,TaskService service) { this.sessions=sessions; this.service=service; }
    @GetMapping("/api/demo/tasks")
    public ItemsResponse<TaskResponse> list(HttpServletRequest request) {
        return new ItemsResponse<>(service.list(sessions.requireWorkspaceId(request)).stream().map(TaskResponse::from).toList());
    }
}
