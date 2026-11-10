package com.sakura.orchestration;

import com.sakura.sakura.domain.SakuraAgent;
import com.sakura.sakura.domain.SakuraAiModel;
import com.sakura.sakura.domain.SakuraAiModelFuncConfig;
import com.sakura.sakura.domain.SakuraGenAgentPool;
import com.sakura.sakura.domain.SakuraProjectGenConfig;
import com.sakura.sakura.mapper.SakuraProjectGenConfigMapper;
import com.sakura.sakura.service.ISakuraAiModelFuncConfigService;
import com.sakura.sakura.service.ISakuraAiModelService;
import com.sakura.sakura.service.ISakuraGenAgentPoolService;
import com.sakura.agent.ISakuraAgentService;
import com.sakura.common.exception.ServiceException;
import com.sakura.model.service.IAiModelBusinessService;
import com.sakura.orchestration.dto.ModelPoolBindingChangeRequest;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 只读预览模型移出业务池的引用及可用替代项。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ModelPoolRemovalPreviewService {
    private final ISakuraAiModelFuncConfigService functions;
    private final ModelPoolReferenceCodes referenceCodes;
    private final ISakuraAgentService agents;
    private final ISakuraGenAgentPoolService matrix;
    private final SakuraProjectGenConfigMapper projects;
    private final IAiModelBusinessService availableModels;

    @Data
    public static class Preview {
        private Long poolId;
        private String poolName;
        private long agentCount;
        private long matrixCount;
        private long projectCount;
        private List<Replacement> replacements = List.of();
    }

    public record Replacement(String value, String label) { }

    public List<Preview> preview(ModelPoolBindingChangeRequest request) {
        if (request == null || request.getModelIds() == null || request.getPoolIds() == null
                || request.getModelIds().isEmpty() || request.getPoolIds().isEmpty()
                || request.getModelIds().size() > 1000 || request.getPoolIds().size() > 100) {
            log.info("模型池移出预览参数无效");
            throw new ServiceException("请选择模型及业务池");
        }
        List<Preview> result = new ArrayList<>();
        for (SakuraAiModelFuncConfig pool : functions.list(Wrappers.<SakuraAiModelFuncConfig>lambdaQuery()
                .select(SakuraAiModelFuncConfig::getId, SakuraAiModelFuncConfig::getFuncCode, SakuraAiModelFuncConfig::getFuncName,
                        SakuraAiModelFuncConfig::getModelType, SakuraAiModelFuncConfig::getModelIds)
                .in(SakuraAiModelFuncConfig::getId, request.getPoolIds()).eq(SakuraAiModelFuncConfig::getDelFlag, "0"))) {
            List<Long> ids = pool.getModelIds() == null ? List.of() : JSON.parseArray(pool.getModelIds(), Long.class);
            List<Long> removed = ids.stream().filter(request.getModelIds()::contains).toList();
            Preview row = new Preview();
            row.setPoolId(pool.getId()); row.setPoolName(pool.getFuncName());
            if (!removed.isEmpty()) {
                List<String> codes = referenceCodes.resolve(removed);
                if (!codes.isEmpty()) {
                    row.setAgentCount(agents.count(Wrappers.<SakuraAgent>lambdaQuery().eq(SakuraAgent::getBizCategoryCode, pool.getFuncCode())
                            .in(SakuraAgent::getModelCode, codes).eq(SakuraAgent::getDelFlag, "0")));
                    row.setMatrixCount(matrix.count(Wrappers.<SakuraGenAgentPool>lambdaQuery().eq(SakuraGenAgentPool::getBizCategoryCode, pool.getFuncCode())
                            .in(SakuraGenAgentPool::getModelCode, codes).eq(SakuraGenAgentPool::getDelFlag, "0")));
                    row.setProjectCount(projects.selectCount(Wrappers.<SakuraProjectGenConfig>lambdaQuery().eq(SakuraProjectGenConfig::getSceneCode, pool.getFuncCode())
                            .in(SakuraProjectGenConfig::getModelCode, codes).eq(SakuraProjectGenConfig::getDelFlag, "0")));
                }
            }
            if ("text".equals(pool.getModelType()) && row.getAgentCount() + row.getMatrixCount() + row.getProjectCount() > 0)
                row.setReplacements(availableModels.listAvailableModelsByFuncCode(pool.getFuncCode()).stream()
                        .filter(m -> !removed.contains(m.getId()))
                        .map(m -> new Replacement(m.getModelCode(), m.getModelName())).toList());
            result.add(row);
        }
        return result;
    }
}
