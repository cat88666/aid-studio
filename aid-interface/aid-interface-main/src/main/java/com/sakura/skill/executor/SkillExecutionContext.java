package com.sakura.skill.executor;

import com.sakura.media.dto.MediaTextGenerateRequest;
import com.sakura.skill.domain.SakuraSkill;
import com.sakura.skill.domain.SakuraSkillRun;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Skill单次执行上下文；配置读取自当前 Skill。 */
@Data
@Builder
public class SkillExecutionContext {
    private SakuraSkillRun run;
    private SakuraSkill skill;
    private List<MediaTextGenerateRequest.TextMessageItem> messages;
    private List<SkillInputPart> inputParts;
    private String responseMode;
    private Long projectId;
    private Long episodeId;
    private String logicalCallKey;
    private String callIdentity;
    private String bizTaskType;
}
