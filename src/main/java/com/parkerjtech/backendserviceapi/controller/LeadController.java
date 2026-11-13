package com.parkerjtech.backendserviceapi.controller;
import java.net.URI;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.parkerjtech.backendserviceapi.model.*;
import com.parkerjtech.backendserviceapi.service.LeadService;
import com.parkerjtech.backendserviceapi.utils.WorkspaceSessionResolver;
@RestController
@RequestMapping("/api/demo/leads")
public class LeadController {
    private final WorkspaceSessionResolver sessions;
    private final LeadService leads;
    public LeadController(WorkspaceSessionResolver sessions,LeadService leads) { this.sessions=sessions; this.leads=leads; }
    @PostMapping
    public ResponseEntity<LeadResponse> create(@Valid @RequestBody CreateLeadRequest body,HttpServletRequest request) {
        var lead=leads.create(sessions.requireWorkspaceId(request),body.toCommand());
        return ResponseEntity.created(URI.create("/api/demo/leads/"+lead.id())).cacheControl(CacheControl.noStore()).body(LeadResponse.from(lead));
    }
    @GetMapping
    public ItemsResponse<LeadResponse> list(HttpServletRequest request) {
        return new ItemsResponse<>(leads.list(sessions.requireWorkspaceId(request)).stream().map(LeadResponse::from).toList());
    }
    @GetMapping("/{id}")
    public LeadResponse get(@PathVariable UUID id,HttpServletRequest request) {
        return LeadResponse.from(leads.get(sessions.requireWorkspaceId(request),id));
    }
}
