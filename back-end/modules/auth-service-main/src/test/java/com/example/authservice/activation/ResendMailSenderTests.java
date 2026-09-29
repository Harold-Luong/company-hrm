package com.example.authservice.activation;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResendMailSenderTests {
    private ActivationSettings settings() {
        var settings = new ActivationSettings(); settings.apiKey = "test-only-key";
        ReflectionTestUtils.setField(settings, "secret", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        return settings;
    }
    private ActivationMailQueue.Mail mail() {
        return new ActivationMailQueue.Mail(UUID.randomUUID(), UUID.randomUUID(), "employee@example.com", "HRM <hrm@example.com>",
                "https://hrm.example.com/activate", OffsetDateTime.parse("2026-10-01T12:00:00Z"), 1);
    }
    @Test
    @SuppressWarnings("unchecked")
    void sendsCorrectResendContractWithStablePayloadAndKeyOnRetry() throws Exception {
        var settings = settings(); var tokens = new ActivationTokens(settings); var mapper = new ObjectMapper();
        var client = mock(HttpClient.class); HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200); when(response.body()).thenReturn("{\"id\":\"resend-id\"}");
        doReturn(response).when(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        var sender = new ResendMailSender(settings, tokens, mapper, client, URI.create("https://api.resend.com/emails"));
        var mail = mail();
        assertThat(sender.send(mail).id()).isEqualTo("resend-id");
        assertThat(sender.send(mail).id()).isEqualTo("resend-id");
        var requests = ArgumentCaptor.forClass(HttpRequest.class); verify(client, times(2)).send(requests.capture(), any());
        var first = requests.getAllValues().getFirst(); var second = requests.getAllValues().getLast();
        assertThat(first.uri()).isEqualTo(URI.create("https://api.resend.com/emails"));
        assertThat(first.headers().firstValue("Authorization")).contains("Bearer test-only-key");
        assertThat(first.headers().firstValue("Idempotency-Key")).contains("hrm-activation-v1/" + mail.id());
        assertThat(first.headers()).isEqualTo(second.headers());
        String body = body(first); assertThat(body(second)).isEqualTo(body);
        var json = mapper.readTree(body);
        assertThat(json.path("to").get(0).asText()).isEqualTo("employee@example.com");
        assertThat(json.path("text").asText()).contains("https://hrm.example.com/activate?token=" + tokens.derive(mail.id()));
        assertThat(first.timeout()).contains(java.time.Duration.ofSeconds(15));
    }
    @Test
    @SuppressWarnings("unchecked")
    void transientErrorsRetryButInvalidProviderKeyStopsWithoutLeakingResponse() throws Exception {
        var settings = settings(); var client = mock(HttpClient.class); HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(429, 503, 403);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Retry-After", List.of("120")), (a, b) -> true));
        when(response.body()).thenReturn("sensitive provider detail");
        doReturn(response).when(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        var sender = new ResendMailSender(settings, new ActivationTokens(settings), new ObjectMapper(), client, URI.create("https://api.resend.com/emails"));
        var rateLimited = sender.send(mail()); assertThat(rateLimited.retryable()).isTrue(); assertThat(rateLimited.retryAfter()).isEqualTo(120);
        assertThat(sender.send(mail()).retryable()).isTrue();
        var rejected = sender.send(mail()); assertThat(rejected.retryable()).isFalse(); assertThat(rejected.error()).isEqualTo("RESEND_HTTP_403");
        doThrow(new java.io.IOException("sensitive transport detail")).when(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        assertThat(sender.send(mail()).error()).isEqualTo("DELIVERY_UNAVAILABLE");
    }
    @Test
    void workerOnlyAcknowledgesAcceptedEmailAndSchedulesFailedDelivery() {
        var queue = mock(ActivationMailQueue.class); var sender = mock(ResendMailSender.class);
        var worker = new ActivationMailWorker(queue, sender); ReflectionTestUtils.setField(worker, "workerEnabled", true);
        var mail = mail(); when(queue.claim()).thenReturn(mail, null);
        when(sender.send(mail)).thenReturn(new ResendMailSender.Delivery(null, "RESEND_HTTP_503", true, 0));
        worker.deliver(); verify(queue).failed(mail, "RESEND_HTTP_503", true, 0); verify(queue, never()).sent(any(), any());
        when(queue.claim()).thenReturn(mail, null); when(sender.send(mail)).thenReturn(new ResendMailSender.Delivery("resend-id", null, false, 0));
        worker.deliver(); verify(queue).sent(mail, "resend-id");
    }
    private String body(HttpRequest request) throws Exception {
        var subscriber = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
        request.bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<>() {
            public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) { subscriber.onSubscribe(subscription); }
            public void onNext(java.nio.ByteBuffer buffer) { subscriber.onNext(List.of(buffer)); }
            public void onError(Throwable error) { subscriber.onError(error); }
            public void onComplete() { subscriber.onComplete(); }
        });
        return subscriber.getBody().toCompletableFuture().get();
    }
}
