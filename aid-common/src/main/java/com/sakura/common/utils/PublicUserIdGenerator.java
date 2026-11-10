package com.sakura.common.utils;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.sakura.common.core.domain.entity.SysUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Public identifiers are independent of relational user IDs and are always exposed as strings. */
@Component
public class PublicUserIdGenerator {
    private final Snowflake snowflake;

    public PublicUserIdGenerator(
            @Value("${sakura.user.public-id.worker-id:0}") long workerId,
            @Value("${sakura.user.public-id.datacenter-id:0}") long datacenterId) {
        if (workerId < 0 || workerId > 31 || datacenterId < 0 || datacenterId > 31) {
            throw new IllegalArgumentException("Public user ID worker/datacenter IDs must be between 0 and 31");
        }
        if (workerId == 31 && datacenterId == 31) {
            throw new IllegalArgumentException("Public user ID node 31/31 is reserved for data migration");
        }
        // Hutool caches this generator in its static singleton registry, preserving its sequence per node pair.
        this.snowflake = IdUtil.getSnowflake(workerId, datacenterId);
    }

    /** Called only for inserts; an externally supplied public ID must never choose a user's identity. */
    public void initializeNewUser(SysUser user) {
        String publicId = snowflake.nextIdStr();
        user.setPublicId(publicId);
        if (StrUtil.isBlank(user.getNickName())) {
            user.setNickName("Sakura_" + publicId);
        }
    }
}
