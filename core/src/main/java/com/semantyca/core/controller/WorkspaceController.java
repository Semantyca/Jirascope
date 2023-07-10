
package com.semantyca.core.controller;

import com.semantyca.core.dto.Workspace;
import com.semantyca.core.service.LanguageService;
import com.semantyca.core.service.ModuleService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/workspace")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkspaceController {

    @Inject
    LanguageService languageService;

    @Inject
    ModuleService moduleService;

    @GET
    @Path("/")
    public Response get() {
        return Response.ok(new Workspace(languageService, moduleService)).build();
    }
}
