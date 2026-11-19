package com.pluto.ndi.capabilities;

import com.pluto.ndi.documents.service.*;
import com.pluto.ndi.shared.web.*;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/capability/agents-live")
public class LiveAgentController extends IntelligenceController {
    public LiveAgentController(
            IntelligenceService service,
            @Qualifier("liveAgentImport") IntelligenceImport imports,
            ApiResponseFactory responses) {
        super(service, imports, responses, IntelligenceKind.AGENTS_LIVE);
    }

    public record TokenInput(UUID id, String month, String amount) {}

    @GetMapping("/{id}/tokens")
    public ApiResponse<?> tokens(Authentication auth, @PathVariable UUID id) {
        return responses.success(service.tokens(actor(auth), id));
    }

    @PostMapping("/{id}/tokens")
    public ApiResponse<?> saveToken(
            Authentication auth, @PathVariable UUID id, @RequestBody TokenInput input) {
        return responses.success(
                service.saveToken(actor(auth), id, input.id(), input.month(), input.amount()));
    }

    @PostMapping("/{id}/tokens/{token}/screenshot")
    public ApiResponse<?> upload(
            Authentication auth,
            @PathVariable UUID id,
            @PathVariable UUID token,
            @RequestBody UploadCommand input) {
        return responses.success(service.upload(actor(auth), id, token, input));
    }

    @PostMapping("/{id}/tokens/{token}/screenshot/{document}")
    public ApiResponse<?> complete(
            Authentication auth,
            @PathVariable UUID id,
            @PathVariable UUID token,
            @PathVariable UUID document) {
        service.complete(actor(auth), id, token, document);
        return responses.success(null);
    }

    @GetMapping("/{id}/tokens/{token}/screenshot")
    public ApiResponse<?> download(
            Authentication auth, @PathVariable UUID id, @PathVariable UUID token) {
        return responses.success(service.download(actor(auth), id, token));
    }
}
