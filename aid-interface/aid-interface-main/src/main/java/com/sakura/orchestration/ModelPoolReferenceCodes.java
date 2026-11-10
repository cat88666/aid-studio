package com.sakura.orchestration;

import com.sakura.sakura.domain.SakuraAiModel;
import com.sakura.sakura.domain.SakuraAiModelAlias;
import com.sakura.sakura.mapper.SakuraAiModelAliasMapper;
import com.sakura.sakura.service.ISakuraAiModelService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 汇总模型当前标识和兼容旧标识，供业务引用检查与迁移共用。 */
@Service
@RequiredArgsConstructor
public class ModelPoolReferenceCodes {
    private final ISakuraAiModelService models;
    private final SakuraAiModelAliasMapper aliases;

    public List<String> resolve(Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        var codes = new LinkedHashSet<String>();
        models.list(Wrappers.<SakuraAiModel>lambdaQuery().select(SakuraAiModel::getModelCode)
                .in(SakuraAiModel::getId, ids)).forEach(model -> codes.add(model.getModelCode()));
        aliases.selectList(Wrappers.<SakuraAiModelAlias>lambdaQuery().select(SakuraAiModelAlias::getLegacyModelCode)
                .in(SakuraAiModelAlias::getModelId, ids)).forEach(alias -> codes.add(alias.getLegacyModelCode()));
        return codes.stream().filter(code -> code != null && !code.isBlank()).toList();
    }
}
