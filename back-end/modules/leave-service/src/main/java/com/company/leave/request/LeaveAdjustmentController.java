package com.company.leave.request;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import static com.company.leave.request.LeaveModels.*;

@RestController
@RequestMapping("/api/v1/leave/requests")
public class LeaveAdjustmentController {
    private final LeaveAdjustmentService service;
    public LeaveAdjustmentController(LeaveAdjustmentService service) { this.service = service; }

    @GetMapping("/adjustments/inbox")
    ResponseEntity<Page> inbox(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            JwtAuthenticationToken actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.inbox(page, size, actor));
    }
    @GetMapping("/{id}/adjustments")
    ResponseEntity<List<Adjustment>> list(@PathVariable UUID id, JwtAuthenticationToken actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(id, actor));
    }
    @PostMapping("/{id}/adjustments")
    ResponseEntity<Request> submit(@PathVariable UUID id, @RequestHeader(value = "If-Match", required = false) String match,
            @Valid @RequestBody AdjustmentSubmission body, JwtAuthenticationToken actor) {
        return response(service.submit(id, match, body, actor));
    }
    @PatchMapping("/{id}/adjustments/{adjustmentId}/{action:approve|reject|cancel}")
    ResponseEntity<Request> decide(@PathVariable UUID id, @PathVariable UUID adjustmentId, @PathVariable String action,
            @RequestHeader(value = "If-Match", required = false) String match,
            @Valid @RequestBody(required = false) Decision body, JwtAuthenticationToken actor) {
        Status target = switch (action) { case "approve" -> Status.APPROVED; case "reject" -> Status.REJECTED; default -> Status.CANCELLED; };
        return response(service.decide(id, adjustmentId, match, target, body == null ? null : body.note(), actor));
    }
    private ResponseEntity<Request> response(Request request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag("\"" + request.version() + "\"").body(request);
    }
}
