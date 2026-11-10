package com.sakura.sakura.domain;

import java.io.Serializable;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import com.sakura.common.core.domain.BaseEntity;

/**
 * 图片内容审核日志对象 sakura_image_moderation_log
 *
 * @author 视觉AID
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName(value = "sakura_image_moderation_log")
public class SakuraImageModerationLog extends BaseEntity implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 业务来源 */
    private String bizSource;

    /** 图片地址 */
    private String fileUrl;

    /** 图片MD5 */
    private String fileMd5;

    /** 审核建议（pass/review/block 等） */
    private String suggestion;

    /** 命中标签 */
    private String label;

    /** 命中子标签 */
    private String subLabel;

    /** 置信度评分 */
    private Integer score;

    /** 审核状态 */
    private String status;

    /** 请求ID */
    private String requestId;

    /** 错误信息 */
    private String errorMessage;

    /** 耗时（毫秒） */
    private Long elapsedMs;

}
