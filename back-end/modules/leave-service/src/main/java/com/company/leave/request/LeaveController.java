package com.company.leave.request;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import static com.company.leave.request.LeaveModels.*;

@RestController
@RequestMapping("/api/v1/leave/requests")
public class LeaveController {
    private final LeaveService service;
    public LeaveController(LeaveService service) { this.service = service; }
    @PostMapping
    ResponseEntity<Request> submit(@Valid @RequestBody Submission body, JwtAuthenticationToken actor) {
        Request request = service.submit(body, actor);
        return ResponseEntity.created(URI.create("/api/v1/leave/requests/" + request.id()))
                .cacheControl(CacheControl.noStore()).eTag("\"" + request.version() + "\"").body(request);
    }
    @GetMapping("/mine")
    ResponseEntity<Page> mine(@RequestParam(required = false) Status status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, JwtAuthenticationToken actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(false, status, page, size, actor));
    }
    @GetMapping("/balance")
    ResponseEntity<Balance> balance(@RequestParam(required = false) Integer year,
            JwtAuthenticationToken actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.balance(year, actor));
    }
    @GetMapping("/inbox")
    ResponseEntity<Page> inbox(@RequestParam(required = false) Status status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, JwtAuthenticationToken actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(true, status, page, size, actor));
    }
    @GetMapping("/pending-count")
    ResponseEntity<PendingCount> pendingCount(JwtAuthenticationToken actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.pendingCount(actor));
    }
    @GetMapping("/{id}")
    ResponseEntity<Request> get(@PathVariable UUID id, JwtAuthenticationToken actor) { return response(service.get(id, actor)); }
    @GetMapping("/{id}/history")
    ResponseEntity<List<History>> history(@PathVariable UUID id, JwtAuthenticationToken actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.history(id, actor));
    }
    @PatchMapping("/{id}/approve")
    ResponseEntity<Request> approve(@PathVariable UUID id, @RequestHeader(value = "If-Match", required = false) String match,
            @Valid @RequestBody Decision body, JwtAuthenticationToken actor) {
        return response(service.decide(id, Status.APPROVED, match, body.note(), actor));
    }
    @PatchMapping("/{id}/reject")
    ResponseEntity<Request> reject(@PathVariable UUID id, @RequestHeader(value = "If-Match", required = false) String match,
            @Valid @RequestBody Decision body, JwtAuthenticationToken actor) {
        return response(service.decide(id, Status.REJECTED, match, body.note(), actor));
    }
    @PatchMapping("/{id}/cancel")
    ResponseEntity<Request> cancel(@PathVariable UUID id, @RequestHeader(value = "If-Match", required = false) String match,
            JwtAuthenticationToken actor) { return response(service.decide(id, Status.CANCELLED, match, null, actor)); }
    private ResponseEntity<Request> response(Request request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag("\"" + request.version() + "\"").body(request);
    }
}
