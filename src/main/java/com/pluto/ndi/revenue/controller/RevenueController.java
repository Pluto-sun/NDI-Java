package com.pluto.ndi.revenue.controller;

import com.pluto.ndi.revenue.domain.*;
import com.pluto.ndi.revenue.domain.Revenue.*;
import com.pluto.ndi.revenue.mapper.RevenueMapper;
import com.pluto.ndi.revenue.service.RevenueService;
import com.pluto.ndi.shared.security.ActorPrincipal;
import com.pluto.ndi.shared.web.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/revenue/entries")
public class RevenueController {
    private final RevenueService service;
    private final ApiResponseFactory responses;

    public RevenueController(RevenueService service, ApiResponseFactory responses) {
        this.service = service;
        this.responses = responses;
    }

    @GetMapping
    public ApiResponse<RevenueMapper.Listing> list(
            Authentication auth,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String province,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String state,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return responses.success(
                service.list(
                        actor(auth),
                        new RevenueMapper.Filter(month, province, search, state, page, size)));
    }

    @GetMapping("/reference-data")
    public ApiResponse<RevenueService.Reference> reference(Authentication auth) {
        return responses.success(service.reference(actor(auth)));
    }

    public record Create(@NotNull Map<String, String> source) {}

    @PostMapping
    public ApiResponse<Entry> create(Authentication auth, @Valid @RequestBody Create body) {
        return responses.success(service.create(actor(auth), body.source()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Entry> detail(Authentication auth, @PathVariable UUID id) {
        return responses.success(service.detail(actor(auth), id));
    }

    public record Recognize(@Min(0) long version, @NotNull Recognition recognition) {}

    @PostMapping("/{id}/recognition")
    public ApiResponse<Entry> recognize(
            Authentication auth, @PathVariable UUID id, @Valid @RequestBody Recognize body) {
        return responses.success(
                service.recognize(actor(auth), id, body.version(), body.recognition()));
    }

    public record Decide(
            @Min(0) long entryVersion,
            @Min(0) long requestVersion,
            @NotBlank String decision,
            String opinion) {}

    @PostMapping("/requests/{id}/decision")
    public ApiResponse<Entry> decide(
            Authentication auth, @PathVariable UUID id, @Valid @RequestBody Decide body) {
        return responses.success(
                service.decide(
                        actor(auth),
                        id,
                        body.entryVersion(),
                        body.requestVersion(),
                        body.decision(),
                        body.opinion()));
    }

    @GetMapping("/{id}/requests")
    public ApiResponse<List<Request>> requests(Authentication auth, @PathVariable UUID id) {
        return responses.success(service.requests(actor(auth), id));
    }

    @GetMapping("/{id}/history")
    public ApiResponse<List<History>> history(Authentication auth, @PathVariable UUID id) {
        return responses.success(service.history(actor(auth), id));
    }

    @GetMapping("/requests")
    public ApiResponse<Page<Request>> requestPage(
            Authentication auth,
            @RequestParam(defaultValue = "mine") String view,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return responses.success(service.requestPage(actor(auth), view, page, size));
    }

    @GetMapping("/pending")
    public ApiResponse<List<Request>> pending(Authentication auth) {
        return responses.success(service.pending(actor(auth)));
    }

    @GetMapping("/counts")
    public ApiResponse<RevenueMapper.Counts> counts(Authentication auth) {
        return responses.success(service.counts(actor(auth)));
    }

    @GetMapping("/notices")
    public ApiResponse<List<Notice>> notices(Authentication auth) {
        return responses.success(service.notices(actor(auth)));
    }

    @PostMapping("/notices/{id}/read")
    public ApiResponse<Void> read(Authentication auth, @PathVariable UUID id) {
        service.read(actor(auth), id);
        return responses.success(null);
    }

    private UUID actor(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof ActorPrincipal p))
            throw new BusinessException(RevenueError.forbidden());
        return p.userId();
    }
}
