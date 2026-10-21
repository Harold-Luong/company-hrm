package com.company.notification;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import jakarta.annotation.PreDestroy;
import com.company.notification.security.SessionVerifier;
@Component
public class NotificationStreams {
 private final Notifications notifications;
 private final AccountResults accountResults;
 private final SessionVerifier sessions;
 private final java.util.Set<Client> clients = ConcurrentHashMap.newKeySet();
 private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
 private static class Client {
   final Notifications.Actor actor; final Instant expires; final SseEmitter emitter; final String accessToken;
   final AtomicBoolean busy = new AtomicBoolean();
   Notifications.Snapshot last;
   long accountCursor;
   boolean accountStarted;
   Client(Notifications.Actor actor, Instant expires, String accessToken) { this.actor=actor; this.expires=expires; this.accessToken=accessToken; this.emitter=new SseEmitter(65000L); }
 }
 public NotificationStreams(Notifications notifications, AccountResults accountResults, SessionVerifier sessions) { this.notifications=notifications; this.accountResults=accountResults; this.sessions=sessions; }
 public synchronized SseEmitter open(JwtAuthenticationToken token, Long accountAfter) {
   var actor=Notifications.actor(token);
   if (clients.size() >= 500 || clients.stream().filter(c->c.actor.userId().equals(actor.userId())).count() >= 8)
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
   Instant expires=token.getToken().getExpiresAt();
   if (expires == null || !expires.isAfter(Instant.now())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
   // Periodically reauthenticate roles; never keep streaming beyond JWT expiry.
   if (expires.isAfter(Instant.now().plusSeconds(60))) expires=Instant.now().plusSeconds(60);
   var client=new Client(actor, expires, token.getToken().getTokenValue());
   if (actor.reviewer()) client.accountCursor=accountAfter == null ? accountResults.latest(actor) : accountAfter;
   clients.add(client);
   client.emitter.onCompletion(()->clients.remove(client));
   client.emitter.onTimeout(()->{clients.remove(client); client.emitter.complete();});
   client.emitter.onError(error->clients.remove(client));
   return client.emitter;
 }
 @Scheduled(fixedDelay=1000)
 public void tick() {
   for (var client:clients) {
     if (!client.busy.compareAndSet(false,true)) continue;
     executor.submit(()->{
       try {
         if (!Instant.now().isBefore(client.expires)) { clients.remove(client); client.emitter.complete(); return; }
         sessions.verify(client.accessToken);
         var snapshot=notifications.snapshot(client.actor);
         if (!snapshot.equals(client.last)) {
           client.emitter.send(SseEmitter.event().name("notifications").data(snapshot));
           client.last=snapshot;
         } else client.emitter.send(SseEmitter.event().comment("heartbeat"));
         if (client.actor.reviewer()) {
           var batch=accountResults.after(client.actor, client.accountCursor);
           if (!client.accountStarted || !batch.items().isEmpty()) {
             client.emitter.send(SseEmitter.event().name("account-results").data(batch));
             client.accountCursor=batch.cursor();
             client.accountStarted=true;
           }
         }
       } catch (Exception error) { clients.remove(client); client.emitter.completeWithError(error); }
       finally { client.busy.set(false); }
     });
   }
 }
 @PreDestroy public void close() { clients.forEach(c->c.emitter.complete()); clients.clear(); executor.shutdownNow(); }
}
