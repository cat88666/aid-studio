package com.sakura.sakura.domain;

import java.io.Serializable;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.sakura.common.annotation.Excel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import com.sakura.common.core.domain.BaseEntity;

/**
 * AI模型功能配置对象 sakura_ai_model_func_config
 *
 * @author 视觉AID
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName(value = "sakura_ai_model_func_config")
public class SakuraAiModelFuncConfig extends BaseEntity implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 功能名称 */
    @Excel(name = "功能名称")
    private String funcName;

    /** 功能编码，唯一 */
    @Excel(name = "功能编码，唯一")
    private String funcCode;

    /** 模型大类：text/image/video/audio */
    @Excel(name = "模型大类：text/image/video/audio")
    private String modelType;

    /** 生成模式：如 image_edit/image_upscale/text_to_image */
    @Excel(name = "生成模式：如 image_edit/image_upscale/text_to_image")
    private String generateMode;

    /** 可选模型ID列表JSON数组，如 [1,2,3] */
    @Excel(name = "可选模型ID列表JSON数组，如 [1,2,3]")
    private String modelIds;

    /** 业务允许调用的模型能力；旧客户端省略时保留已有绑定。 */
    @TableField(exist = false)
    private java.util.List<SakuraAiBusinessModelBinding> modelBindings;

    /** 本次移出文本模型时，当前业务引用迁移到的保留模型；不持久化，不影响其他业务。 */
    @TableField(exist = false)
    private String removedModelReplacementCode;

    /** 状态：0启用 1停用 */
    @Excel(name = "状态：0启用 1停用")
    private String status;

    /** 删除标记：0存在 2删除 */
    private String delFlag;

}
