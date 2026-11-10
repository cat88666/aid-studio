package com.sakura.storyboard.service;

import com.sakura.rps.dto.AssetExtractTaskVO;
import com.sakura.storyboard.dto.StoryboardImageUpscaleRequest;
import com.sakura.billing.vo.BillingQuoteVO;

/**
 * 分镜图高清服务。
 *
 * @author 视觉AID
 */
public interface IStoryboardImageUpscaleService
{
    /**
     * 提交分镜图高清任务。
     *
     * @param request 入参（含 genRecordId / modelCode / 可选 resolution）
     * @param userId  当前登录用户 ID
     * @return 任务视图（taskId + PENDING）
     */
    AssetExtractTaskVO upscaleStoryboardImage(StoryboardImageUpscaleRequest request, Long userId);

    /** 无副作用地报价分镜图高清请求。 */
    BillingQuoteVO quoteUpscaleImage(StoryboardImageUpscaleRequest request, Long userId);
}
