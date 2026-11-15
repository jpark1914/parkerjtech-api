package com.parkerjtech.backendserviceapi.controller;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.parkerjtech.backendserviceapi.model.*;
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
    @PatchMapping("/api/demo/tasks/{id}/status")
    public ResponseEntity<TaskResponse> complete(@PathVariable UUID id,@Valid @RequestBody CompleteTaskRequest body,HttpServletRequest request) {
        var task=service.complete(sessions.requireWorkspaceId(request),id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(TaskResponse.from(task));
    }
}
