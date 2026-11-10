package com.sakura.common.sakura.redis;

/**
 * 统一 Redis Key 命名空间常量。
 *
 * @author 视觉AID
 */
public final class RedisKeys
{
    private RedisKeys() {}
    /** 登录失败累计计数：sakura:auth:login:fail:{username} */
    public static String authLoginFail(String username)
    {
        return "sakura:auth:login:fail:" + username;
    }

    /** 登录失败锁定：sakura:auth:login:lock:{username} */
    public static String authLoginLock(String username)
    {
        return "sakura:auth:login:lock:" + username;
    }

    /** 实名认证冷却：sakura:auth:real:cooldown:{userId} */
    public static String authRealCooldown(Long userId)
    {
        return "sakura:auth:real:cooldown:" + userId;
    }
    /** 支付回调幂等锁：sakura:pay:notify:{orderNo} */
    public static String payNotifyLock(String orderNo)
    {
        return "sakura:pay:notify:" + orderNo;
    }
    public static String extractLock(Long projectId, Long episodeId)
    {
        return "sakura:extract:lock:" + projectId + ":" + (episodeId == null ? "0" : episodeId);
    }

    public static String extractCancelFlag(Long taskId)
    {
        return "sakura:extract:cancel:" + taskId;
    }

    public static String extractBillingLock(Long taskId)
    {
        return "sakura:extract:billing:lock:" + taskId;
    }
    public static String mediaBillingLock(Long taskId)
    {
        return "sakura:media:billing:lock:" + taskId;
    }

    public static String mediaConcurrentGlobal()
    {
        return "sakura:media:concurrent:global";
    }

    public static String mediaConcurrentUser(Long userId)
    {
        return "sakura:media:concurrent:user:" + (userId == null ? "anonymous" : userId);
    }
    public static String formGenerateLock(Long assetId)
    {
        return "sakura:form:generate:lock:" + assetId;
    }

    public static String formImageLock(Long formId)
    {
        return "sakura:form:image:lock:" + formId;
    }

    public static String formCardLock(Long imageId)
    {
        return "sakura:form:card:lock:" + imageId;
    }

    public static String formUpscaleLock(Long imageId)
    {
        return "sakura:form:upscale:lock:" + imageId;
    }

    public static String formMultiViewLock(Long formId)
    {
        return "sakura:form:multi-view:lock:" + formId;
    }

    public static String formEditChatLock(Long formId)
    {
        return "sakura:form:edit-chat:lock:" + formId;
    }
}
