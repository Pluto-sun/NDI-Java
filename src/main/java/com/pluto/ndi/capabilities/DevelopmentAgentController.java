package com.pluto.ndi.capabilities;

import com.pluto.ndi.documents.service.*;
import com.pluto.ndi.shared.web.*;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/capability/agents-development")
public class DevelopmentAgentController extends IntelligenceController {
    public DevelopmentAgentController(
            IntelligenceService service,
            @Qualifier("developmentAgentImport") IntelligenceImport imports,
            ApiResponseFactory responses) {
        super(service, imports, responses, IntelligenceKind.AGENTS_DEVELOPMENT);
    }
}
