package com.sakura.media.yyz.controller;

import com.sakura.common.sakura.crypto.annotation.CryptoIgnore;
import com.sakura.common.annotation.Anonymous;
import com.sakura.media.dto.KlingCallbackContext;
import com.sakura.media.service.KlingCallbackResult;
import com.sakura.media.service.KlingCallbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Enumeration;

/** 可灵新版 Webhook 接收端点。 */
@RestController
@RequestMapping("/api/media/callback")
@RequiredArgsConstructor
@Tag(name = "可灵任务回调", description = "接收并验签可灵任务状态通知")
public class KlingCallbackController {

    private final KlingCallbackService klingCallbackService;

    @Anonymous
    @CryptoIgnore
    @PostMapping("/kling")
    @Operation(summary = "接收可灵回调", description = "使用原始请求体验签并幂等收口任务状态")
    public ResponseEntity<Void> onCallback(@RequestBody(required = false) String rawBody, HttpServletRequest request) {
        KlingCallbackContext context = new KlingCallbackContext();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            context.putHeader(name, request.getHeader(name));
        }
        KlingCallbackResult result = klingCallbackService.handleKlingCallback(rawBody, context);
        return switch (result) {
            case ACCEPTED -> ResponseEntity.ok().build();
            case INVALID_SIGNATURE_OR_PAYLOAD -> ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            case RETRYABLE_INTERNAL -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        };
    }
}
