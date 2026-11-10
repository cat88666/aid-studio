package com.sakura.model.definition;

import cn.hutool.core.bean.BeanUtil;
import com.sakura.sakura.domain.SakuraAiBusinessModelBinding;
import com.sakura.sakura.domain.SakuraAiModel;
import com.sakura.sakura.domain.SakuraAiModelAlias;
import com.sakura.sakura.domain.SakuraAiModelCapability;
import com.sakura.sakura.domain.SakuraAiModelFuncConfig;
import com.sakura.sakura.domain.SakuraAiModelMigration;
import com.sakura.sakura.domain.SakuraAiModelProtocolBinding;
import com.sakura.sakura.domain.SakuraAiVoiceLibrary;
import com.sakura.sakura.mapper.SakuraAiBusinessModelBindingMapper;
import com.sakura.sakura.mapper.SakuraAiModelAliasMapper;
import com.sakura.sakura.mapper.SakuraAiModelCapabilityMapper;
import com.sakura.sakura.mapper.SakuraAiModelFuncConfigMapper;
import com.sakura.sakura.mapper.SakuraAiModelMapper;
import com.sakura.sakura.mapper.SakuraAiModelMigrationMapper;
import com.sakura.sakura.mapper.SakuraAiModelProtocolBindingMapper;
import com.sakura.sakura.mapper.SakuraAiVoiceLibraryMapper;
import com.sakura.common.exception.ServiceException;
import com.sakura.common.utils.DateUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 事务内记录配置变更；回滚前逐项比较后置状态，不覆盖后续编辑。 */
@Service
@RequiredArgsConstructor
public class ModelMigrationJournal {
    private final SakuraAiModelMigrationMapper journal;
    private final SakuraAiModelMapper models;
    private final SakuraAiModelCapabilityMapper capabilities;
    private final SakuraAiModelProtocolBindingMapper routes;
    private final SakuraAiModelAliasMapper aliases;
    private final SakuraAiBusinessModelBindingMapper businesses;
    private final SakuraAiModelFuncConfigMapper functions;
    private final SakuraAiVoiceLibraryMapper voices;
    private final ModelMigrationReferences references;

    @Data
    public static class Snapshot {
        private List<Long> modelIds;
        private List<SakuraAiModel> models;
        private List<SakuraAiModelCapability> capabilities;
        private List<SakuraAiModelProtocolBinding> routes;
        private List<SakuraAiModelAlias> aliases;
        private List<SakuraAiBusinessModelBinding> businesses;
        private List<SakuraAiModelFuncConfig> functions;
        private List<SakuraAiVoiceLibrary> voices;
        private Map<String, List<Map<String, Object>>> references;
    }

    public SakuraAiModelMigration find(String key) {
        return journal.selectOne(Wrappers.<SakuraAiModelMigration>lambdaQuery().eq(SakuraAiModelMigration::getRequestKey, key).last("FOR UPDATE"));
    }

    public List<SakuraAiModelMigration> recent() {
        return journal.selectList(Wrappers.<SakuraAiModelMigration>lambdaQuery()
                .select(SakuraAiModelMigration::getId, SakuraAiModelMigration::getStatus, SakuraAiModelMigration::getAffectedModels,
                        SakuraAiModelMigration::getCreateTime, SakuraAiModelMigration::getCreateBy,
                        SakuraAiModelMigration::getUpdateTime, SakuraAiModelMigration::getUpdateBy)
                .orderByDesc(SakuraAiModelMigration::getId).last("LIMIT 50"));
    }

    public String capture(List<Long> ids) {
        Snapshot snapshot = new Snapshot();
        snapshot.setModelIds(ids.stream().sorted().toList());
        snapshot.setModels(models.selectList(Wrappers.<SakuraAiModel>lambdaQuery().in(SakuraAiModel::getId, ids).orderByAsc(SakuraAiModel::getId)));
        snapshot.setCapabilities(capabilities.selectList(Wrappers.<SakuraAiModelCapability>lambdaQuery().in(SakuraAiModelCapability::getModelId, ids).orderByAsc(SakuraAiModelCapability::getId)));
        snapshot.setRoutes(routes.selectList(Wrappers.<SakuraAiModelProtocolBinding>lambdaQuery().in(SakuraAiModelProtocolBinding::getModelId, ids).orderByAsc(SakuraAiModelProtocolBinding::getId)));
        snapshot.setAliases(aliases.selectList(Wrappers.<SakuraAiModelAlias>lambdaQuery().in(SakuraAiModelAlias::getModelId, ids).orderByAsc(SakuraAiModelAlias::getId)));
        snapshot.setBusinesses(businesses.selectList(Wrappers.<SakuraAiBusinessModelBinding>lambdaQuery().in(SakuraAiBusinessModelBinding::getModelId, ids).orderByAsc(SakuraAiBusinessModelBinding::getId)));
        snapshot.setFunctions(functions.selectList(Wrappers.<SakuraAiModelFuncConfig>lambdaQuery().orderByAsc(SakuraAiModelFuncConfig::getId).last("FOR UPDATE")).stream()
                .filter(row -> row.getModelIds() != null && JSON.parseArray(row.getModelIds(), Long.class).stream().anyMatch(ids::contains)).toList());
        snapshot.setVoices(voices.selectList(Wrappers.<SakuraAiVoiceLibrary>lambdaQuery().in(SakuraAiVoiceLibrary::getModelId, ids).orderByAsc(SakuraAiVoiceLibrary::getId).last("FOR UPDATE")));
        snapshot.setReferences(references.capture(snapshot.getModels()));
        return JSON.toJSONString(snapshot, JSONWriter.Feature.WriteMapNullValue);
    }

    public void record(String key, String digest, String before, String after, int count, String actor) {
        SakuraAiModelMigration entry = new SakuraAiModelMigration();
        entry.setRequestKey(key); entry.setRequestDigest(digest); entry.setStatus("APPLIED");
        entry.setBeforeJson(before); entry.setAfterJson(after); entry.setAffectedModels(count);
        entry.setCreateBy(actor); entry.setCreateTime(DateUtils.getNowDate()); journal.insert(entry);
    }

    @Transactional(rollbackFor = Exception.class)
    public int rollback(Long id, String actor) {
        SakuraAiModelMigration entry = journal.selectOne(Wrappers.<SakuraAiModelMigration>lambdaQuery().eq(SakuraAiModelMigration::getId, id).last("FOR UPDATE"));
        if (entry == null) throw new ServiceException("迁移记录不存在");
        if ("ROLLED_BACK".equals(entry.getStatus())) return entry.getAffectedModels();
        if (!"APPLIED".equals(entry.getStatus())) throw new ServiceException("迁移状态不支持回滚");
        Snapshot before = JSON.parseObject(entry.getBeforeJson(), Snapshot.class);
        Snapshot after = JSON.parseObject(entry.getAfterJson(), Snapshot.class);
        models.selectList(Wrappers.<SakuraAiModel>lambdaQuery().in(SakuraAiModel::getId, before.getModelIds()).orderByAsc(SakuraAiModel::getId).last("FOR UPDATE"));
        if (!Objects.equals(JSON.parse(entry.getAfterJson()), JSON.parse(capture(before.getModelIds()))))
            throw new ServiceException("迁移后配置已有变化，请先核对差异，不能直接回滚");
        for (SakuraAiModel original : before.getModels()) {
            long currentVersion = after.getModels().stream().filter(row -> Objects.equals(row.getId(), original.getId()))
                    .map(row -> row.getConfigVersion() == null ? 0L : row.getConfigVersion()).findFirst().orElse(0L);
            original.setConfigVersion(currentVersion + 1);
            original.setUpdateBy(actor); original.setUpdateTime(DateUtils.getNowDate());
            restore(models, original, SakuraAiModel.class);
        }
        capabilities.delete(Wrappers.<SakuraAiModelCapability>lambdaQuery().in(SakuraAiModelCapability::getModelId, before.getModelIds()));
        routes.delete(Wrappers.<SakuraAiModelProtocolBinding>lambdaQuery().in(SakuraAiModelProtocolBinding::getModelId, before.getModelIds()));
        aliases.delete(Wrappers.<SakuraAiModelAlias>lambdaQuery().in(SakuraAiModelAlias::getModelId, before.getModelIds()));
        businesses.delete(Wrappers.<SakuraAiBusinessModelBinding>lambdaQuery().in(SakuraAiBusinessModelBinding::getModelId, before.getModelIds()));
        before.getCapabilities().forEach(capabilities::insert);
        before.getRoutes().forEach(routes::insert);
        before.getAliases().forEach(aliases::insert);
        before.getBusinesses().forEach(businesses::insert);
        for (var function : before.getFunctions()) restore(functions, function, SakuraAiModelFuncConfig.class);
        for (var voice : before.getVoices()) restore(voices, voice, SakuraAiVoiceLibrary.class);
        references.restore(before.getReferences());
        entry.setStatus("ROLLED_BACK");
        entry.setUpdateBy(actor); entry.setUpdateTime(DateUtils.getNowDate()); journal.updateById(entry);
        return entry.getAffectedModels();
    }

    private static <T> void restore(BaseMapper<T> mapper, T row, Class<T> type) {
        var table = TableInfoHelper.getTableInfo(type);
        UpdateWrapper<T> update = new UpdateWrapper<>();
        update.eq(table.getKeyColumn(), BeanUtil.getProperty(row, table.getKeyProperty()));
        // 元数据只包含持久化字段；显式恢复 NULL，不能用忽略空值的局部更新。
        table.getFieldList().forEach(field -> update.set(field.getColumn(), BeanUtil.getProperty(row, field.getProperty())));
        mapper.update(null, update);
    }
}
