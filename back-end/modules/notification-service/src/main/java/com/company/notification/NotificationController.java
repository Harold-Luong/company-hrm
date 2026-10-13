package com.company.notification;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.http.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
 private final Notifications notifications;
 private final NotificationStreams streams;
 public NotificationController(Notifications notifications, NotificationStreams streams) { this.notifications=notifications; this.streams=streams; }
 @GetMapping public ResponseEntity<Notifications.Page> list(@RequestParam(required=false) Long before, JwtAuthenticationToken actor) {
   return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(notifications.list(Notifications.actor(actor), before));
 }
 @PatchMapping("/{id}/read") public ResponseEntity<Void> read(@PathVariable UUID id, JwtAuthenticationToken actor) {
   notifications.read(id, Notifications.actor(actor)); return ResponseEntity.noContent().build();
 }
 public record ReadAll(long through) {}
 @PostMapping("/read-all") public ResponseEntity<Void> readAll(@RequestBody ReadAll body, JwtAuthenticationToken actor) {
   notifications.readAll(Notifications.actor(actor), body.through()); return ResponseEntity.noContent().build();
 }
 @GetMapping(value="/stream", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
 public ResponseEntity<SseEmitter> stream(JwtAuthenticationToken actor, @RequestParam(required=false) Long accountAfter) {
   if (accountAfter != null && accountAfter < 0) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST);
   return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Accel-Buffering","no").body(streams.open(actor, accountAfter));
 }
}
