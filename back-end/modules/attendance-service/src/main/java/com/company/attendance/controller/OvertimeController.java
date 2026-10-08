package com.company.attendance.controller;
import com.company.attendance.dto.*;
import com.company.attendance.dto.OvertimeModels.*;
import com.company.attendance.service.OvertimeService;
import com.company.attendance.security.CorporateNetwork;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/attendance/overtime") @RequiredArgsConstructor
public class OvertimeController {
    private final OvertimeService service;
    private final CorporateNetwork network;
    @GetMapping
    public PageResponse<Response> mine(@RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,JwtAuthenticationToken actor) { return service.list(false,status,page,size,actor); }
    @GetMapping("/inbox") @PreAuthorize("hasAnyRole('HR','ADMIN')")
    public PageResponse<Response> inbox(@RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,JwtAuthenticationToken actor) { return service.list(true,status,page,size,actor); }
    @PostMapping
    public ResponseEntity<Response> create(@Valid @RequestBody Write body,@RequestHeader(value="Idempotency-Key",required=false) String key,JwtAuthenticationToken actor) { return ResponseEntity.status(HttpStatus.CREATED).body(service.create(body,key,actor)); }
    @PostMapping("/{id}/decision") @PreAuthorize("hasAnyRole('HR','ADMIN')")
    public Response decide(@PathVariable UUID id,@Valid @RequestBody AttendanceRequestModels.Decision body,@RequestHeader(value="If-Match",required=false) String match,JwtAuthenticationToken actor) { return service.decide(id,body,match,actor); }
    @PostMapping("/{id}/cancel")
    public Response cancel(@PathVariable UUID id,@RequestHeader(value="If-Match",required=false) String match,JwtAuthenticationToken actor) { return service.cancel(id,match,actor); }
    @PostMapping("/{id}/check-in")
    public Response in(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false) String key,HttpServletRequest request,JwtAuthenticationToken actor) { return service.punch(id,true,key,network.requireAllowed(request),actor); }
    @PostMapping("/{id}/check-out")
    public Response out(@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false) String key,HttpServletRequest request,JwtAuthenticationToken actor) { return service.punch(id,false,key,network.requireAllowed(request),actor); }
}
