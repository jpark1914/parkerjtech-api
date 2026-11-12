package com.parkerjtech.backendserviceapi.workspace;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import com.parkerjtech.backendserviceapi.api.ApiException;

@Component
public class WorkspaceSessionResolver {
    private static final String ATTRIBUTE="demoWorkspaceId";
    private final WorkspaceService service;
    public WorkspaceSessionResolver(WorkspaceService service) { this.service=service; }
    public DemoWorkspace bootstrap(HttpServletRequest request) {
        var session=request.getSession(true);
        Object associated=session.getAttribute(ATTRIBUTE);
        var workspace=service.bootstrap(associated instanceof UUID id?id:null);
        session.setAttribute(ATTRIBUTE,workspace.id());
        return workspace;
    }
    public UUID requireWorkspaceId(HttpServletRequest request) {
        var session=request.getSession(false);
        if(session==null || !(session.getAttribute(ATTRIBUTE) instanceof UUID id)) throw ApiException.workspaceRequired();
        try {
            return service.requireActive(id).id();
        } catch(ApiException ex) {
            session.removeAttribute(ATTRIBUTE);
            throw ex;
        }
    }
}
