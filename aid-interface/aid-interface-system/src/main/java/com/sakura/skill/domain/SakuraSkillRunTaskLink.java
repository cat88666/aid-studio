package com.sakura.skill.domain;

import com.sakura.common.core.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/** Run/Step 到现有 sakura_media_task 的纯关联。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sakura_skill_run_task_link", excludeProperty = "remark")
public class SakuraSkillRunTaskLink extends BaseEntity implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long runId;
    private Long stepId;
    private String stepExecutionId;
    private Integer workflowAttempt;
    private String logicalCallKey;
    private Long mediaTaskId;
    private String delFlag;
}
