package com.sakura.creator.controller;

import com.sakura.common.sakura.crypto.annotation.CryptoIgnore;
import com.sakura.common.utils.SecurityUtils;
import com.sakura.common.exception.ServiceException;
import com.sakura.creator.dto.CreatorChatRequest;
import com.sakura.media.dto.MediaTextGenerateRequest;
import com.sakura.media.service.IMediaGenerationService;
import com.sakura.media.service.MediaTextStreamSink;
import com.sakura.model.dto.AiModelListRequest;
import com.sakura.model.service.IAiModelBusinessService;
import com.sakura.model.vo.AiModelVO;
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
    private static final String DEFAULT_MODEL_CODE = "creator-elysium-chat";
    private final IMediaGenerationService mediaGenerationService;
    private final IAiModelBusinessService modelBusinessService;
    private final ScheduledExecutorService scheduler;

    public CreatorChatController(IMediaGenerationService mediaGenerationService,
            IAiModelBusinessService modelBusinessService,
            @Qualifier("scheduledExecutorService") ScheduledExecutorService scheduler) {
        this.mediaGenerationService = mediaGenerationService;
        this.modelBusinessService = modelBusinessService;
        this.scheduler = scheduler;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @CryptoIgnore
    public ResponseEntity<SseEmitter> stream(@Valid @RequestBody CreatorChatRequest input) {
        Long userId = SecurityUtils.getUserId();
        MediaTextGenerateRequest request = toMediaRequest(input, userId);
        // Use the same enabled-model/provider and capability filtering as the public picker.
        // Never let a stale or forged choice silently fall back to another billed model.
        AiModelVO selected = requireAvailableModel(request.getModelName());
        request.setCapabilityCode(selected.getCapabilityCode());
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
        request.setModelName(input.modelCode() == null || input.modelCode().isBlank()
                ? DEFAULT_MODEL_CODE : input.modelCode().trim());
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

    private AiModelVO requireAvailableModel(String modelCode) {
        AiModelListRequest query = new AiModelListRequest();
        query.setModelType("text");
        query.setGenerateMode("text");
        return modelBusinessService.listAvailableModels(query).stream()
                .filter(model -> modelCode.equals(model.getModelCode())
                        && "text".equalsIgnoreCase(model.getModelType()))
                .findFirst().orElseThrow(() -> new ServiceException("所选模型不可用，请重新选择"));
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
