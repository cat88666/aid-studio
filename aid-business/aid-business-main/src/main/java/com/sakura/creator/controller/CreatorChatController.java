package com.sakura.creator.controller;

import com.sakura.common.sakura.crypto.annotation.CryptoIgnore;
import com.sakura.common.utils.SecurityUtils;
import com.sakura.creator.dto.CreatorChatRequest;
import com.sakura.media.dto.MediaTextGenerateRequest;
import com.sakura.media.service.IMediaGenerationService;
import com.sakura.media.service.MediaTextStreamSink;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Authenticated, project-independent chat using the existing media billing lifecycle. */
@RestController
@RequestMapping("/api/user/creator/chat")
public class CreatorChatController {
    private final IMediaGenerationService mediaGenerationService;
    private final ScheduledExecutorService scheduler;

    public CreatorChatController(IMediaGenerationService mediaGenerationService,
            @Qualifier("scheduledExecutorService") ScheduledExecutorService scheduler) {
        this.mediaGenerationService = mediaGenerationService;
        this.scheduler = scheduler;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @CryptoIgnore
    public ResponseEntity<SseEmitter> stream(@Valid @RequestBody CreatorChatRequest input) {
        Long userId = SecurityUtils.getUserId();
        MediaTextGenerateRequest request = toMediaRequest(input, userId);
        SseEmitter emitter = new SseEmitter(15L * 60L * 1000L);
        ChatSink sink = new ChatSink(emitter, mediaGenerationService, scheduler);
        emitter.onCompletion(sink::close);
        emitter.onError(ignored -> sink.close());
        emitter.onTimeout(() -> { sink.close(); emitter.complete(); });
        // Preparation needs the authenticated request thread. The service owns its worker,
        // concurrency control and settlement; disconnecting only stops UI delivery.
        try {
            mediaGenerationService.generateTextStream(request, sink);
        } catch (RuntimeException failure) {
            sink.onFailed("Unable to start chat. Please try again.");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_EVENT_STREAM);
        headers.setCacheControl("no-cache");
        headers.set("X-Accel-Buffering", "no");
        return ResponseEntity.ok().headers(headers).body(emitter);
    }

    static MediaTextGenerateRequest toMediaRequest(CreatorChatRequest input, Long userId) {
        MediaTextGenerateRequest request = new MediaTextGenerateRequest();
        request.setUserId(userId);
        request.setModelName("creator-elysium-chat");
        request.setStrictModelSelection(true);
        request.setStream(true);
        request.setReasoningEnabled(false);
        request.setIncludeReasoning(false);
        request.setOptions(new HashMap<>(Map.of("max_tokens", 1024)));
        request.setMessages(input.messages().stream().map(message -> {
            MediaTextGenerateRequest.TextMessageItem item = new MediaTextGenerateRequest.TextMessageItem();
            item.setRole(message.role());
            item.setContent(message.content());
            return item;
        }).toList());
        return request;
    }

    static final class ChatSink implements MediaTextStreamSink {
        private final SseEmitter emitter;
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile long taskId;
        private final IMediaGenerationService service;
        private final ScheduledExecutorService scheduler;
        private final AtomicReference<ScheduledFuture<?>> polling = new AtomicReference<>();

        ChatSink(SseEmitter emitter, IMediaGenerationService service, ScheduledExecutorService scheduler) {
            this.emitter = emitter;
            this.service = service;
            this.scheduler = scheduler;
        }
        void close() {
            closed.set(true);
            ScheduledFuture<?> future = polling.getAndSet(null);
            if (future != null) future.cancel(false);
        }
        @Override public void onTaskPrepared(long id) { taskId = id; }
        @Override public void onDelta(String content) {
            send("delta", Map.of("content", content == null ? "" : content), false);
        }
        @Override public void onDone(String fullText, String ignoredAuditSnapshot) {
            send("done", Map.of("content", fullText == null ? "" : fullText,
                    "taskId", taskId, "status", "SUCCEEDED"), true);
        }
        @Override public void onDetached(long id) {
            taskId = id;
            if (closed.get()) return;
            send("status", Map.of("taskId", id, "status", "QUEUED"), false);
            try {
                ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(this::poll,
                        1, 2, TimeUnit.SECONDS);
                if (!polling.compareAndSet(null, future)) future.cancel(false);
                if (closed.get()) close();
            } catch (java.util.concurrent.RejectedExecutionException shuttingDown) {
                onFailed("Chat status unavailable");
            }
        }
        @Override public void onFailed(String ignoredUpstreamMessage) {
            send("error", Map.of("message", "Chat could not be completed. Please try again."), true);
        }
        void poll() {
            if (closed.get()) return;
            try {
                // Only a task created for this authenticated request can reach this sink.
                // Local snapshots neither poll the provider nor submit another generation.
                var task = service.queryTaskLocal(taskId);
                if (task == null) { onFailed("Task unavailable"); return; }
                if ("SUCCEEDED".equals(task.getStatus())) {
                    onDone(task.getTextContent(), null);
                } else if (java.util.Set.of("FAILED", "CANCELLED", "CANCELED").contains(task.getStatus())) {
                    onFailed("Task failed");
                } else {
                    send("heartbeat", Map.of("taskId", taskId), false);
                }
            } catch (RuntimeException unavailable) {
                // A temporary local read failure must not abandon a queued generation.
                send("heartbeat", Map.of("taskId", taskId), false);
            }
        }
        private synchronized void send(String event, Map<String, Object> data, boolean terminal) {
            if (closed.get()) return;
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
                if (terminal) { close(); emitter.complete(); }
            } catch (IOException | IllegalStateException disconnected) {
                close();
            }
        }
    }
}
