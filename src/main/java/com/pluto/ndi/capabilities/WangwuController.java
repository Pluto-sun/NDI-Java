package com.pluto.ndi.capabilities;

import com.pluto.ndi.documents.service.*;
import com.pluto.ndi.shared.web.*;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/capability/wangwu")
public class WangwuController extends IntelligenceController {
    public WangwuController(
            IntelligenceService service,
            @Qualifier("wangwuImport") IntelligenceImport imports,
            ApiResponseFactory responses) {
        super(service, imports, responses, IntelligenceKind.WANGWU);
    }
}
