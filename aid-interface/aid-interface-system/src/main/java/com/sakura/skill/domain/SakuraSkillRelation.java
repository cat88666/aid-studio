package com.sakura.skill.domain;

import com.sakura.common.core.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/** Skill 版本之间的固定子能力/依赖关系。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sakura_skill_relation", excludeProperty = "remark")
public class SakuraSkillRelation extends BaseEntity implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long parentVersionId;
    private Long childSkillId;
    private Long childVersionId;
    private String relationType;
    private String relationKey;
    private Boolean requiredFlag;
    private String delFlag;
}
