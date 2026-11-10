package com.sakura.compose.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakura.common.sakura.crypto.annotation.CryptoIgnore;
import com.sakura.common.annotation.Anonymous;
import com.sakura.common.core.domain.AjaxResult;
import com.sakura.compose.controller.dto.ImsCallbackRequest;
import com.sakura.compose.service.ImsCallbackService;

import lombok.RequiredArgsConstructor;

/** 阿里云 IMS 回调入口。 */
@RestController
@RequestMapping("/api/media/callback")
@RequiredArgsConstructor
public class ImsCallbackController
{
    private final ImsCallbackService callbackService;

    @Anonymous
    @CryptoIgnore
    @PostMapping("/ims")
    public AjaxResult onCallback(@RequestBody(required = false) ImsCallbackRequest request)
    {
        callbackService.handle(request == null ? null : request.getJobId(),
                request == null ? null : jsonText(request.getMessageBody() != null
                        ? request.getMessageBody() : request.getEventMessage()),
                request == null ? null : jsonText(request.getUserData()));
        return AjaxResult.success();
    }

    private String jsonText(com.fasterxml.jackson.databind.JsonNode node)
    {
        if (node == null || node.isNull())
        {
            return null;
        }
        return node.isTextual() ? node.asText() : node.toString();
    }
}
