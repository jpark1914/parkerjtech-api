package com.parkerjtech.backendserviceapi.controller;

import com.parkerjtech.backendserviceapi.model.SessionResponse;
import com.parkerjtech.backendserviceapi.utils.WorkspaceSessionResolver;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {
    private final WorkspaceSessionResolver sessions;
    public SessionController(WorkspaceSessionResolver sessions) { this.sessions=sessions; }
    @GetMapping("/api/demo/session")
    public ResponseEntity<SessionResponse> bootstrap(HttpServletRequest request,CsrfToken token) {
        var workspace=sessions.bootstrap(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new SessionResponse(
            workspace.id(),workspace.createdAt(),workspace.expiresAt(),token.getToken(),token.getHeaderName()));
    }
}
