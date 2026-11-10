package com.sakura.orchestration.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sakura.sakura.domain.SakuraAgent;
import com.sakura.sakura.domain.SakuraAiBusinessModelBinding;
import com.sakura.sakura.domain.SakuraAiModelProtocolBinding;
import com.sakura.sakura.domain.model.ModelProtocolBinding;
import com.sakura.sakura.mapper.SakuraAiBusinessModelBindingMapper;
import com.sakura.sakura.mapper.SakuraAiModelProtocolBindingMapper;
import com.sakura.model.definition.ModelCapabilitySelection;
import com.sakura.orchestration.ModelPoolReferenceCodes;
import org.springframework.transaction.annotation.Propagation;
import com.sakura.sakura.domain.SakuraAiModel;
import com.sakura.sakura.domain.SakuraAiModelCapability;
import com.sakura.sakura.domain.SakuraAiModelFuncConfig;
import com.sakura.sakura.domain.SakuraAiProvider;
import com.sakura.sakura.domain.SakuraAiVoiceLibrary;
import com.sakura.sakura.domain.SakuraGenAgentPool;
import com.sakura.sakura.domain.SakuraProjectGenConfig;
import com.sakura.sakura.domain.model.ModelCapabilityDefinition;
import com.sakura.sakura.mapper.SakuraAiModelCapabilityMapper;
import com.sakura.sakura.mapper.SakuraProjectGenConfigMapper;
import com.sakura.sakura.service.ISakuraAiModelFuncConfigService;
import com.sakura.sakura.service.ISakuraAiModelService;
import com.sakura.sakura.service.ISakuraAiProviderService;
import com.sakura.sakura.service.ISakuraAiVoiceLibraryService;
import com.sakura.sakura.service.ISakuraGenAgentPoolService;
import com.sakura.agent.ISakuraAgentService;
import com.sakura.common.exception.ServiceException;
import com.sakura.common.utils.DateUtils;
import com.sakura.orchestration.IAiOrchestrationService;
import com.sakura.orchestration.dto.ModelPoolBindingChangeRequest;
import com.sakura.orchestration.dto.ModelPoolBindingQueryRequest;
import com.sakura.orchestration.dto.ModelPoolCapabilitySelection;
import com.sakura.orchestration.dto.RetireResourceRequest;
import com.sakura.orchestration.vo.ModelPoolBindingChangeVO;
import com.sakura.orchestration.vo.ModelPoolBindingModelVO;
import com.sakura.orchestration.vo.ModelPoolBindingPoolVO;
import com.sakura.orchestration.vo.ModelPoolBindingSnapshotVO;
import com.sakura.orchestration.vo.ModelPoolCapabilityVO;
import com.sakura.orchestration.vo.OrchestrationImpactItemVO;
import com.sakura.orchestration.vo.OrchestrationImpactVO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.alibaba.fastjson2.JSON;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 业务编排完整性与资源生命周期服务实现。
 *
 * <p>这里只处理仍会影响运行时选择的活动配置。任务、计费和生成历史均保留创建时快照，
 * 不参与级联删除。</p>
 */
@Slf4j
@Service
public class AiOrchestrationServiceImpl implements IAiOrchestrationService
{
    private static final String NORMAL = "0";
    private static final String DISABLED = "1";
    private static final String MODEL_DELETED = "1";
    private static final String CONFIG_DELETED = "2";
    private static final String ROW_DELETED = "1";
    private static final Integer AGENT_ENABLED = 1;
    private static final Integer AGENT_DISABLED = 0;
    private static final String HISTORY_POLICY = "生成任务、计费记录和历史结果不会删除，继续保留创建时的模型与参数快照。";

    @Autowired
    private ISakuraAiModelService modelService;

    @Autowired
    private ISakuraAiProviderService providerService;

    @Autowired
    private ISakuraAiModelFuncConfigService functionConfigService;

    @Autowired
    private ISakuraAgentService agentService;

    @Autowired
    private ISakuraGenAgentPoolService agentPoolService;

    @Autowired
    private SakuraProjectGenConfigMapper projectGenConfigMapper;

    @Autowired
    private SakuraAiModelCapabilityMapper modelCapabilityMapper;

    @Autowired
    private ModelPoolReferenceCodes referenceCodes;

    @Autowired
    private SakuraAiBusinessModelBindingMapper businessBindingMapper;

    @Autowired
    private SakuraAiModelProtocolBindingMapper protocolBindingMapper;

    @Autowired
    private ISakuraAiVoiceLibraryService voiceLibraryService;

    @Autowired
    private com.sakura.model.definition.ModelFunctionBindingReconciler capabilityBindings;

    @Override
    public void validateFunctionConfig(SakuraAiModelFuncConfig config)
    {
        if (Objects.isNull(config))
        {
            throw new ServiceException("模型池配置不能为空");
        }
        config.setFuncCode(StrUtil.trim(config.getFuncCode()));
        config.setFuncName(StrUtil.trim(config.getFuncName()));
        config.setModelType(StrUtil.trim(config.getModelType()));
        config.setGenerateMode(StrUtil.trimToNull(config.getGenerateMode()));
        if (StrUtil.isBlank(config.getStatus()))
        {
            config.setStatus(NORMAL);
        }
        if (StrUtil.isBlank(config.getFuncCode()) || StrUtil.isBlank(config.getFuncName())
                || StrUtil.isBlank(config.getModelType()))
        {
            throw new ServiceException("功能名称、功能编码和模型类型不能为空");
        }
        if (!Objects.equals(NORMAL, config.getStatus()) && !Objects.equals(DISABLED, config.getStatus()))
        {
            throw new ServiceException("模型池状态不合法");
        }

        SakuraAiModelFuncConfig persisted = null;
        if (Objects.nonNull(config.getId()))
        {
            persisted = functionConfigService.getOne(
                    Wrappers.<SakuraAiModelFuncConfig>lambdaQuery()
                            .select(SakuraAiModelFuncConfig::getId, SakuraAiModelFuncConfig::getFuncCode,
                                    SakuraAiModelFuncConfig::getModelIds)
                            .eq(SakuraAiModelFuncConfig::getId, config.getId())
                            .eq(SakuraAiModelFuncConfig::getDelFlag, NORMAL)
                            .last("limit 1"), false);
            if (Objects.isNull(persisted))
            {
                throw new ServiceException("模型池配置不存在");
            }
            if (!Objects.equals(persisted.getFuncCode(), config.getFuncCode()))
            {
                throw new ServiceException("功能编码属于稳定业务标识，创建后不能修改");
            }
        }

        SakuraAiModelFuncConfig duplicate = functionConfigService.getOne(
                Wrappers.<SakuraAiModelFuncConfig>lambdaQuery()
                        .select(SakuraAiModelFuncConfig::getId)
                        .eq(SakuraAiModelFuncConfig::getFuncCode, config.getFuncCode())
                        .eq(SakuraAiModelFuncConfig::getDelFlag, NORMAL)
                        .ne(Objects.nonNull(config.getId()), SakuraAiModelFuncConfig::getId, config.getId())
                        .last("limit 1"), false);
        if (Objects.nonNull(duplicate))
        {
            throw new ServiceException("功能编码已存在");
        }

        List<Long> modelIds = parseModelIdsStrict(config.getModelIds());
        if (Objects.equals(NORMAL, config.getStatus()) && CollectionUtil.isEmpty(modelIds))
        {
            throw new ServiceException("启用的模型池至少需要一个模型");
        }
        if (CollectionUtil.isNotEmpty(modelIds))
        {
            List<SakuraAiModel> models = modelService.list(
                    Wrappers.<SakuraAiModel>lambdaQuery()
                            .select(SakuraAiModel::getId, SakuraAiModel::getModelCode, SakuraAiModel::getModelType)
                            .in(SakuraAiModel::getId, modelIds)
                            .eq(SakuraAiModel::getDelFlag, NORMAL));
            if (models.size() != modelIds.size())
            {
                throw new ServiceException("模型池包含不存在或已下线的模型");
            }
            for (SakuraAiModel model : models)
            {
                if (!Objects.equals(config.getModelType(), model.getModelType()))
                {
                    throw new ServiceException("模型【" + model.getModelCode() + "】与模型池类型不一致");
                }
            }
        }
        config.setModelIds(JSONUtil.toJsonStr(modelIds));

        if (Objects.nonNull(persisted))
        {
            validateRemovedModelsNotInUse(config,
                    parseModelIdsSafe(persisted.getModelIds()), new LinkedHashSet<>(modelIds));
        }
    }

    @Override
    public void validateFunctionConfigsRemovable(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return;
        }
        for (Long id : ids)
        {
            SakuraAiModelFuncConfig config = requireFunctionConfig(id);
            long agentCount = agentService.count(Wrappers.<SakuraAgent>lambdaQuery()
                    .eq(SakuraAgent::getBizCategoryCode, config.getFuncCode())
                    .eq(SakuraAgent::getDelFlag, NORMAL));
            long matrixCount = agentPoolService.count(Wrappers.<SakuraGenAgentPool>lambdaQuery()
                    .eq(SakuraGenAgentPool::getBizCategoryCode, config.getFuncCode())
                    .eq(SakuraGenAgentPool::getDelFlag, NORMAL));
            long projectCount = projectGenConfigMapper.selectCount(Wrappers.<SakuraProjectGenConfig>lambdaQuery()
                    .eq(SakuraProjectGenConfig::getSceneCode, config.getFuncCode())
                    .eq(SakuraProjectGenConfig::getDelFlag, NORMAL));
            if (agentCount + matrixCount + projectCount > 0)
            {
                throw new ServiceException("模型池【" + config.getFuncName()
                        + "】仍被智能体、策略矩阵或项目配置引用，请先处理引用");
            }
        }
    }

    @Override
    public ModelPoolBindingSnapshotVO getModelPoolBindings(ModelPoolBindingQueryRequest request)
    {
        List<Long> requestedIds = normalizeIds(Objects.isNull(request) ? null : request.getModelIds());
        List<SakuraAiModel> allModels = modelService.list(Wrappers.<SakuraAiModel>lambdaQuery()
                .select(SakuraAiModel::getId, SakuraAiModel::getModelCode, SakuraAiModel::getModelName,
                        SakuraAiModel::getModelType, SakuraAiModel::getGenerateMode, SakuraAiModel::getStatus)
                .eq(SakuraAiModel::getDelFlag, NORMAL)
                .orderByDesc(SakuraAiModel::getPriority)
                .orderByAsc(SakuraAiModel::getId));
        Set<Long> requestedIdSet = new LinkedHashSet<>(requestedIds);
        List<SakuraAiModel> models = CollectionUtil.isEmpty(requestedIds) ? allModels
                : allModels.stream().filter(model -> requestedIdSet.contains(model.getId()))
                        .collect(Collectors.toList());
        Set<Long> existingModelIds = models.stream().map(SakuraAiModel::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, SakuraAiModel> allModelMap = allModels.stream()
                .collect(Collectors.toMap(SakuraAiModel::getId, model -> model));
        Map<Long, List<ModelPoolCapabilityVO>> modelCapabilities = loadPoolCapabilities(existingModelIds);
        Map<Long, List<Long>> memberships = new LinkedHashMap<>();
        for (Long modelId : existingModelIds)
        {
            memberships.put(modelId, new ArrayList<>());
        }

        List<SakuraAiModelFuncConfig> pools = functionConfigService.list(
                Wrappers.<SakuraAiModelFuncConfig>lambdaQuery()
                        .select(SakuraAiModelFuncConfig::getId, SakuraAiModelFuncConfig::getFuncName,
                                SakuraAiModelFuncConfig::getFuncCode, SakuraAiModelFuncConfig::getModelType,
                                SakuraAiModelFuncConfig::getGenerateMode, SakuraAiModelFuncConfig::getStatus,
                                SakuraAiModelFuncConfig::getModelIds)
                        .eq(SakuraAiModelFuncConfig::getDelFlag, NORMAL)
                        .orderByDesc(SakuraAiModelFuncConfig::getId));
        List<ModelPoolBindingPoolVO> poolViews = new ArrayList<>();
        for (SakuraAiModelFuncConfig pool : pools)
        {
            List<Long> poolModelIds;
            boolean valid = true;
            int staleModelCount = 0;
            try
            {
                List<Long> configuredIds = parseModelIdsStrict(pool.getModelIds());
                poolModelIds = configuredIds.stream().filter(modelId -> {
                    SakuraAiModel model = allModelMap.get(modelId);
                    return Objects.nonNull(model) && Objects.equals(pool.getModelType(), model.getModelType());
                }).collect(Collectors.toList());
                staleModelCount = configuredIds.size() - poolModelIds.size();
                valid = configuredIds.stream().filter(allModelMap::containsKey).allMatch(modelId ->
                        Objects.equals(pool.getModelType(), allModelMap.get(modelId).getModelType()));
                if (staleModelCount > 0)
                {
                    log.warn("模型池关系快照已忽略失效模型引用: poolId={}, staleCount={}",
                            pool.getId(), staleModelCount);
                }
            }
            catch (ServiceException e)
            {
                log.warn("模型池关系快照发现无效配置: poolId={}", pool.getId());
                poolModelIds = Collections.emptyList();
                valid = false;
            }
            for (Long modelId : poolModelIds)
            {
                List<Long> poolIds = memberships.get(modelId);
                if (Objects.nonNull(poolIds))
                {
                    poolIds.add(pool.getId());
                }
            }
            poolViews.add(ModelPoolBindingPoolVO.builder()
                    .id(pool.getId()).funcName(pool.getFuncName()).funcCode(pool.getFuncCode())
                    .modelType(pool.getModelType()).generateMode(pool.getGenerateMode()).status(pool.getStatus())
                    .configurationValid(valid).staleModelCount(staleModelCount).modelIds(poolModelIds).build());
        }
        List<ModelPoolBindingModelVO> modelViews = models.stream()
                .map(model -> ModelPoolBindingModelVO.builder()
                        .id(model.getId()).modelCode(model.getModelCode()).modelName(model.getModelName())
                        .modelType(model.getModelType()).generateMode(model.getGenerateMode()).status(model.getStatus())
                        .poolIds(memberships.getOrDefault(model.getId(), Collections.emptyList()))
                        .capabilities(modelCapabilities.getOrDefault(model.getId(), Collections.emptyList())).build())
                .collect(Collectors.toList());
        return ModelPoolBindingSnapshotVO.builder().pools(poolViews).models(modelViews).build();
    }

    private Map<Long, List<ModelPoolCapabilityVO>> loadPoolCapabilities(Set<Long> modelIds)
    {
        if (CollectionUtil.isEmpty(modelIds))
        {
            return Collections.emptyMap();
        }
        Map<Long, List<ModelPoolCapabilityVO>> result = new LinkedHashMap<>();
        List<SakuraAiModelCapability> rows = modelCapabilityMapper.selectList(
                Wrappers.<SakuraAiModelCapability>lambdaQuery()
                        .in(SakuraAiModelCapability::getModelId, modelIds)
                        .orderByAsc(SakuraAiModelCapability::getModelId)
                        .orderByAsc(SakuraAiModelCapability::getSortOrder));
        for (SakuraAiModelCapability row : rows)
        {
            try
            {
                ModelCapabilityDefinition definition = JSON.parseObject(
                        row.getDefinitionJson(), ModelCapabilityDefinition.class);
                result.computeIfAbsent(row.getModelId(), ignored -> new ArrayList<>()).add(
                        ModelPoolCapabilityVO.builder()
                                .code(definition.getCode()).label(definition.getLabel())
                                .generateMode(definition.getGenerateMode())
                                .enabled(Boolean.TRUE.equals(definition.getEnabled()))
                                .defaultCapability(Boolean.TRUE.equals(definition.getDefaultCapability()))
                                .build());
            }
            catch (RuntimeException e)
            {
                log.warn("模型池关系快照忽略无法解析的能力: modelId={}, capabilityCode={}",
                        row.getModelId(), row.getCapabilityCode());
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelPoolBindingChangeVO bindModelsToPools(ModelPoolBindingChangeRequest request, String operator)
    {
        return changeModelPoolBindings(request, operator, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelPoolBindingChangeVO unbindModelsFromPools(ModelPoolBindingChangeRequest request, String operator)
    {
        return changeModelPoolBindings(request, operator, false);
    }

    private ModelPoolBindingChangeVO changeModelPoolBindings(ModelPoolBindingChangeRequest request,
            String operator, boolean bind)
    {
        List<Long> modelIds = normalizeIds(Objects.isNull(request) ? null : request.getModelIds());
        List<Long> poolIds = normalizeIds(Objects.isNull(request) ? null : request.getPoolIds());
        if (CollectionUtil.isEmpty(modelIds))
        {
            throw new ServiceException("请选择模型");
        }
        if (CollectionUtil.isEmpty(poolIds))
        {
            throw new ServiceException("请选择模型池");
        }
        List<SakuraAiModel> models = modelService.list(Wrappers.<SakuraAiModel>lambdaQuery()
                .select(SakuraAiModel::getId, SakuraAiModel::getModelCode, SakuraAiModel::getModelName, SakuraAiModel::getModelType,
                        SakuraAiModel::getGenerateMode, SakuraAiModel::getStatus)
                .in(SakuraAiModel::getId, modelIds)
                .eq(SakuraAiModel::getDelFlag, NORMAL));
        if (models.size() != modelIds.size())
        {
            log.error("批量维护模型池失败：部分模型不存在, modelIds={}", modelIds);
            throw new ServiceException("部分模型不存在");
        }
        List<SakuraAiModelFuncConfig> pools = functionConfigService.list(
                Wrappers.<SakuraAiModelFuncConfig>lambdaQuery()
                        .in(SakuraAiModelFuncConfig::getId, poolIds)
                        .eq(SakuraAiModelFuncConfig::getDelFlag, NORMAL)
                        .orderByAsc(SakuraAiModelFuncConfig::getId)
                        .last("for update"));
        if (pools.size() != poolIds.size())
        {
            log.error("批量维护模型池失败：部分模型池不存在, poolIds={}", poolIds);
            throw new ServiceException("部分模型池不存在");
        }

        Map<Long, Map<Long, ModelPoolCapabilitySelection>> requestedSelections = bind
                ? normalizeCapabilitySelections(request, modelIds, poolIds) : Collections.emptyMap();
        Map<Long, List<Long>> configuredIdsByPool = new LinkedHashMap<>();
        Set<Long> referencedModelIds = new LinkedHashSet<>(modelIds);
        for (SakuraAiModelFuncConfig pool : pools)
        {
            try
            {
                List<Long> configuredIds = parseModelIdsStrict(pool.getModelIds());
                configuredIdsByPool.put(pool.getId(), configuredIds);
                referencedModelIds.addAll(configuredIds);
            }
            catch (ServiceException e)
            {
                log.error("批量维护模型池失败：模型池配置异常, poolId={}", pool.getId());
                throw new ServiceException("模型池【" + poolLabel(pool) + "】配置异常");
            }
        }
        Map<Long, SakuraAiModel> activeModels = CollectionUtil.isEmpty(referencedModelIds)
                ? Collections.emptyMap()
                : modelService.list(Wrappers.<SakuraAiModel>lambdaQuery()
                        .select(SakuraAiModel::getId, SakuraAiModel::getModelCode, SakuraAiModel::getModelName,
                                SakuraAiModel::getModelType, SakuraAiModel::getStatus)
                        .in(SakuraAiModel::getId, referencedModelIds)
                        .eq(SakuraAiModel::getDelFlag, NORMAL)).stream()
                        .collect(Collectors.toMap(SakuraAiModel::getId, model -> model));

        int changedPoolCount = 0;
        int changedRelationCount = 0;
        int unchangedRelationCount = 0;
        for (SakuraAiModelFuncConfig pool : pools)
        {
            if (bind)
            {
                for (SakuraAiModel model : models)
                {
                    if (!Objects.equals(pool.getModelType(), model.getModelType()))
                    {
                        log.error("批量绑定模型池失败：类型不匹配, modelId={}, poolId={}",
                                model.getId(), pool.getId());
                        throw new ServiceException("模型【" + modelLabel(model) + "】与模型池【"
                                + poolLabel(pool) + "】类型不匹配");
                    }
                }
            }
            List<Long> configuredIds = configuredIdsByPool.getOrDefault(pool.getId(), Collections.emptyList());
            LinkedHashSet<Long> currentIds = new LinkedHashSet<>();
            int staleModelCount = 0;
            for (Long currentId : configuredIds)
            {
                SakuraAiModel currentModel = activeModels.get(currentId);
                if (currentModel == null)
                {
                    staleModelCount++;
                    continue;
                }
                if (!Objects.equals(pool.getModelType(), currentModel.getModelType()))
                {
                    throw new ServiceException("模型【" + modelLabel(currentModel) + "】与模型池【"
                            + poolLabel(pool) + "】类型不匹配");
                }
                currentIds.add(currentId);
            }
            if (staleModelCount > 0)
            {
                log.warn("批量维护模型池时清理失效模型引用: poolId={}, staleCount={}",
                        pool.getId(), staleModelCount);
            }
            LinkedHashSet<Long> nextIds = new LinkedHashSet<>(currentIds);
            int changedForPool = 0;
            for (Long modelId : modelIds)
            {
                boolean changed = bind ? nextIds.add(modelId) : nextIds.remove(modelId);
                if (changed)
                {
                    changedForPool++;
                }
                else
                {
                    unchangedRelationCount++;
                }
            }
            if (!bind)
            {
                // 旧数据可能只有能力绑定而没有池内 ID；幂等解绑也必须清掉这层关系。
                capabilityBindings.removeForModels(pool.getFuncCode(), modelIds);
            }
            if (changedForPool == 0 && staleModelCount == 0)
            {
                continue;
            }
            SakuraAiModelFuncConfig next = new SakuraAiModelFuncConfig();
            next.setId(pool.getId());
            next.setFuncName(pool.getFuncName());
            next.setFuncCode(pool.getFuncCode());
            next.setModelType(pool.getModelType());
            next.setGenerateMode(pool.getGenerateMode());
            next.setStatus(pool.getStatus());
            next.setModelIds(JSONUtil.toJsonStr(new ArrayList<>(nextIds)));
            if (!bind && request.getPoolReplacementCodes() != null)
                next.setRemovedModelReplacementCode(request.getPoolReplacementCodes().get(pool.getId()));
            prepareFunctionUpdate(next, operator);
            Map<Long, ModelPoolCapabilitySelection> selectionsForPool = requestedSelections
                    .getOrDefault(pool.getId(), Collections.emptyMap()).entrySet().stream()
                    .filter(entry -> !currentIds.contains(entry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                            (left, right) -> left, LinkedHashMap::new));
            if (bind)
            {
                for (Long newlyBoundId : nextIds)
                {
                    if (currentIds.contains(newlyBoundId)) continue;
                    boolean structured = modelCapabilityMapper.selectCount(
                            Wrappers.<SakuraAiModelCapability>lambdaQuery()
                                    .eq(SakuraAiModelCapability::getModelId, newlyBoundId)) > 0;
                    if (structured && !selectionsForPool.containsKey(newlyBoundId))
                    {
                        throw new ServiceException("重新绑定模型池时请重新选择模型能力");
                    }
                }
            }
            capabilityBindings.reconcile(next, operator, selectionsForPool);
            SakuraAiModelFuncConfig update = new SakuraAiModelFuncConfig();
            update.setId(pool.getId());
            update.setModelIds(next.getModelIds());
            update.setUpdateBy(operator);
            update.setUpdateTime(DateUtils.getNowDate());
            if (!functionConfigService.updateById(update))
            {
                log.error("批量维护模型池失败：保存失败, poolId={}", pool.getId());
                throw new ServiceException("模型池保存失败");
            }
            changedPoolCount++;
            changedRelationCount += changedForPool;
        }
        log.info("批量{}模型池完成: modelCount={}, poolCount={}, changedPoolCount={}, changedRelationCount={}, operator={}",
                bind ? "绑定" : "移出", modelIds.size(), poolIds.size(), changedPoolCount,
                changedRelationCount, operator);
        return ModelPoolBindingChangeVO.builder()
                .requestedModelCount(modelIds.size()).requestedPoolCount(poolIds.size())
                .changedPoolCount(changedPoolCount).changedRelationCount(changedRelationCount)
                .unchangedRelationCount(unchangedRelationCount)
                .snapshot(getModelPoolBindings(null)).build();
    }

    private List<Long> normalizeIds(List<Long> ids)
    {
        if (CollectionUtil.isEmpty(ids))
        {
            return Collections.emptyList();
        }
        return new ArrayList<>(ids.stream().filter(Objects::nonNull).filter(id -> id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    private Map<Long, Map<Long, ModelPoolCapabilitySelection>> normalizeCapabilitySelections(
            ModelPoolBindingChangeRequest request, List<Long> modelIds, List<Long> poolIds)
    {
        if (request == null || CollectionUtil.isEmpty(request.getCapabilitySelections()))
        {
            return Collections.emptyMap();
        }
        Set<Long> allowedModels = new LinkedHashSet<>(modelIds);
        Set<Long> allowedPools = new LinkedHashSet<>(poolIds);
        Map<Long, Map<Long, ModelPoolCapabilitySelection>> result = new LinkedHashMap<>();
        for (ModelPoolCapabilitySelection selection : request.getCapabilitySelections())
        {
            if (selection == null || !allowedModels.contains(selection.getModelId())
                    || !allowedPools.contains(selection.getPoolId()))
            {
                throw new ServiceException("模型池能力选择不属于本次操作");
            }
            ModelPoolCapabilitySelection previous = result
                    .computeIfAbsent(selection.getPoolId(), ignored -> new LinkedHashMap<>())
                    .putIfAbsent(selection.getModelId(), selection);
            if (previous != null)
            {
                throw new ServiceException("模型池能力选择重复");
            }
        }
        return result;
    }

    private String modelLabel(SakuraAiModel model)
    {
        if (StrUtil.isNotBlank(model.getModelName())) return model.getModelName();
        if (StrUtil.isNotBlank(model.getModelCode())) return model.getModelCode();
        return "#" + model.getId();
    }

    private String poolLabel(SakuraAiModelFuncConfig pool)
    {
        if (StrUtil.isNotBlank(pool.getFuncName())) return pool.getFuncName();
        if (StrUtil.isNotBlank(pool.getFuncCode())) return pool.getFuncCode();
        return "#" + pool.getId();
    }

    @Override
    public OrchestrationImpactVO previewModelRetirement(Long modelId)
    {
        SakuraAiModel model = requireModel(modelId, false);
        List<SakuraAiModelFuncConfig> pools = findModelPools(modelId);
        long agentCount = countAgentsByModel(model.getModelCode());
        long matrixCount = countMatrixByModel(model.getModelCode());
        long projectCount = countProjectsByModel(model.getModelCode());
        long voiceCount = countVoicesByModel(modelId);
        List<OrchestrationImpactItemVO> refs = new ArrayList<>();
        refs.add(item("model_pool", "模型池", pools.size(), "替换模型ID，或从非空模型池移除"));
        refs.add(item("agent", "智能体默认模型", agentCount, "替换默认模型，或清空后由业务兜底"));
        refs.add(item("matrix", "策略矩阵", matrixCount, "替换矩阵模型，或清空后由智能体兜底"));
        refs.add(item("project_config", "项目级个性化配置", projectCount, "替换模型，或停用该条个性化配置"));
        refs.add(item("voice", "关联音色", voiceCount, "随模型停用，保留音色归属和历史数据"));
        long total = pools.size() + agentCount + matrixCount + projectCount + voiceCount;
        return OrchestrationImpactVO.builder()
                .resourceType("model")
                .resourceId(model.getId())
                .resourceCode(model.getModelCode())
                .resourceName(model.getModelName())
                .activeReferenceCount(total)
                .canRetireDirectly(total == 0)
                .replacementSupported(true)
                .references(refs)
                .historyPolicy(HISTORY_POLICY)
                .build();
    }

    @Override
    public OrchestrationImpactVO previewAgentRetirement(Long agentId)
    {
        SakuraAgent agent = requireAgent(agentId, false);
        long matrixCount = countMatrixByAgent(agent.getAgentCode());
        long projectCount = countProjectsByAgent(agent.getAgentCode());
        List<OrchestrationImpactItemVO> refs = new ArrayList<>();
        refs.add(item("matrix", "策略矩阵", matrixCount, "替换智能体，或移除相关候选/默认项"));
        refs.add(item("project_config", "项目级个性化配置", projectCount, "替换智能体，或停用该条个性化配置"));
        long total = matrixCount + projectCount;
        return OrchestrationImpactVO.builder()
                .resourceType("agent")
                .resourceId(agent.getId())
                .resourceCode(agent.getAgentCode())
                .resourceName(agent.getName())
                .bizCategoryCode(agent.getBizCategoryCode())
                .activeReferenceCount(total)
                .canRetireDirectly(total == 0)
                .replacementSupported(true)
                .references(refs)
                .historyPolicy(HISTORY_POLICY)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteModelsIfUnreferenced(Long[] modelIds, String operator)
    {
        if (modelIds == null || modelIds.length == 0)
        {
            return 0;
        }
        for (Long modelId : modelIds)
        {
            OrchestrationImpactVO impact = previewModelRetirement(modelId);
            if (!impact.isCanRetireDirectly())
            {
                throw new ServiceException("模型【" + impact.getResourceName() + "】仍有活动引用，请使用“影响预览”执行受控下线");
            }
        }
        int affected = 0;
        for (Long modelId : modelIds)
        {
            affected += softDeleteModel(modelId, operator);
        }
        return affected;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteAgentIfUnreferenced(Long agentId, String operator)
    {
        OrchestrationImpactVO impact = previewAgentRetirement(agentId);
        if (!impact.isCanRetireDirectly())
        {
            throw new ServiceException("智能体【" + impact.getResourceName() + "】仍有活动引用，请使用“影响预览”执行受控下线");
        }
        return softDeleteAgent(agentId, operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retireModel(Long modelId, RetireResourceRequest request, String operator)
    {
        requireConfirmed(request);
        SakuraAiModel target = requireModel(modelId, true);
        String replacementCode = StrUtil.trimToNull(request.getReplacementCode());
        SakuraAiModel replacement = null;
        if (StrUtil.isNotBlank(replacementCode))
        {
            replacement = requireReplacementModel(target, replacementCode);
        }

        List<SakuraAiModelFuncConfig> pools = findModelPools(modelId);
        for (SakuraAiModelFuncConfig pool : pools)
        {
            List<Long> currentIds = parseModelIdsStrict(pool.getModelIds());
            List<Long> nextIds = replaceModelId(currentIds, modelId,
                    Objects.isNull(replacement) ? null : replacement.getId());
            if (CollectionUtil.isEmpty(nextIds))
            {
                throw new ServiceException("模型池【" + pool.getFuncName() + "】仅剩当前模型，请先指定替代模型");
            }
            if (Objects.nonNull(replacement) && !Objects.equals(pool.getModelType(), replacement.getModelType()))
            {
                throw new ServiceException("替代模型与模型池【" + pool.getFuncName() + "】类型不一致");
            }
            pool.setModelIds(JSONUtil.toJsonStr(nextIds));
            capabilityBindings.reconcile(pool, operator);
            SakuraAiModelFuncConfig update = new SakuraAiModelFuncConfig();
            update.setId(pool.getId());
            update.setModelIds(pool.getModelIds());
            update.setUpdateBy(operator);
            update.setUpdateTime(DateUtils.getNowDate());
            functionConfigService.updateById(update);
        }

        String nextModelCode = Objects.isNull(replacement) ? null : replacement.getModelCode();
        updateAgentModelReferences(target.getModelCode(), nextModelCode, operator);
        updateMatrixModelReferences(target.getModelCode(), nextModelCode, operator);
        updateProjectModelReferences(target.getModelCode(), nextModelCode, operator);
        disableModelVoices(target.getId(), operator);
        softDeleteModel(target.getId(), operator);
        log.info("受控下线模型完成: modelId={}, modelCode={}, replacementCode={}, operator={}",
                target.getId(), target.getModelCode(), nextModelCode, operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retireAgent(Long agentId, RetireResourceRequest request, String operator)
    {
        requireConfirmed(request);
        SakuraAgent target = requireAgent(agentId, true);
        String replacementCode = StrUtil.trimToNull(request.getReplacementCode());
        SakuraAgent replacement = null;
        if (StrUtil.isNotBlank(replacementCode))
        {
            replacement = agentService.getOne(Wrappers.<SakuraAgent>lambdaQuery()
                    .select(SakuraAgent::getId, SakuraAgent::getAgentCode, SakuraAgent::getBizCategoryCode,
                            SakuraAgent::getStatus, SakuraAgent::getDelFlag)
                    .eq(SakuraAgent::getAgentCode, replacementCode)
                    .eq(SakuraAgent::getDelFlag, NORMAL)
                    .last("limit 1"), false);
            if (Objects.isNull(replacement) || !Objects.equals(AGENT_ENABLED, replacement.getStatus()))
            {
                throw new ServiceException("替代智能体不存在或未启用");
            }
            if (Objects.equals(target.getAgentCode(), replacement.getAgentCode()))
            {
                throw new ServiceException("替代智能体不能与待下线智能体相同");
            }
            if (!Objects.equals(target.getBizCategoryCode(), replacement.getBizCategoryCode()))
            {
                throw new ServiceException("替代智能体必须属于同一业务分类");
            }
        }

        String nextAgentCode = Objects.isNull(replacement) ? null : replacement.getAgentCode();
        updateMatrixAgentReferences(target.getAgentCode(), nextAgentCode, operator);
        updateProjectAgentReferences(target.getAgentCode(), nextAgentCode, operator);
        softDeleteAgent(target.getId(), operator);
        log.info("受控下线智能体完成: agentId={}, agentCode={}, replacementCode={}, operator={}",
                target.getId(), target.getAgentCode(), nextAgentCode, operator);
    }

    private void validateRemovedModelsNotInUse(SakuraAiModelFuncConfig config, Set<Long> beforeIds, Set<Long> nextIds)
    {
        String funcCode = config.getFuncCode();
        Set<Long> removed = new LinkedHashSet<>(beforeIds);
        removed.removeAll(nextIds);
        if (CollectionUtil.isEmpty(removed))
        {
            return;
        }
        List<String> removedCodes = referenceCodes.resolve(removed);
        if (CollectionUtil.isEmpty(removedCodes))
        {
            return;
        }
        long agentCount = agentService.count(Wrappers.<SakuraAgent>lambdaQuery()
                .eq(SakuraAgent::getBizCategoryCode, funcCode)
                .in(SakuraAgent::getModelCode, removedCodes)
                .eq(SakuraAgent::getDelFlag, NORMAL));
        long matrixCount = agentPoolService.count(Wrappers.<SakuraGenAgentPool>lambdaQuery()
                .eq(SakuraGenAgentPool::getBizCategoryCode, funcCode)
                .in(SakuraGenAgentPool::getModelCode, removedCodes)
                .eq(SakuraGenAgentPool::getDelFlag, NORMAL));
        long projectCount = projectGenConfigMapper.selectCount(Wrappers.<SakuraProjectGenConfig>lambdaQuery()
                .eq(SakuraProjectGenConfig::getSceneCode, funcCode)
                .in(SakuraProjectGenConfig::getModelCode, removedCodes)
                .eq(SakuraProjectGenConfig::getDelFlag, NORMAL));
        if (agentCount + matrixCount + projectCount > 0)
        {
            if (!"text".equals(config.getModelType()) || StrUtil.isBlank(config.getRemovedModelReplacementCode()))
            {
                log.info("移出模型仍有业务引用: funcCode={}, agents={}, matrix={}, projects={}", funcCode, agentCount, matrixCount, projectCount);
                throw new ServiceException("请先选择业务替代模型");
            }
            requirePoolReplacement(config, nextIds);
        }
    }

    private SakuraAiModel requirePoolReplacement(SakuraAiModelFuncConfig config, Set<Long> nextIds)
    {
        if (!"text".equals(config.getModelType()) || nextIds.isEmpty())
            throw replacementError("请选择可用替代文本模型");
        SakuraAiModel replacement = modelService.getOne(Wrappers.<SakuraAiModel>lambdaQuery()
                .in(SakuraAiModel::getId, nextIds)
                .eq(SakuraAiModel::getModelCode, config.getRemovedModelReplacementCode())
                .eq(SakuraAiModel::getModelType, "text").eq(SakuraAiModel::getStatus, NORMAL)
                .eq(SakuraAiModel::getDelFlag, NORMAL));
        if (replacement == null || providerService.count(Wrappers.<SakuraAiProvider>lambdaQuery()
                .eq(SakuraAiProvider::getId, replacement.getProviderId()).eq(SakuraAiProvider::getStatus, NORMAL)
                .eq(SakuraAiProvider::getDelFlag, NORMAL)) != 1)
            throw replacementError("替代模型不可用");
        SakuraAiModelFuncConfig previous = functionConfigService.getById(config.getId());
        if (previous == null || !parseModelIdsSafe(previous.getModelIds()).contains(replacement.getId()))
            throw replacementError("替代模型须已在池内");
        List<ModelCapabilityDefinition> definitions = modelCapabilityMapper.selectList(Wrappers.<SakuraAiModelCapability>lambdaQuery()
                .eq(SakuraAiModelCapability::getModelId, replacement.getId())).stream()
                .map(row -> JSON.parseObject(row.getDefinitionJson(), ModelCapabilityDefinition.class)).toList();
        if (!definitions.isEmpty()) {
            var selections = (config.getModelBindings() == null
                    ? businessBindingMapper.selectList(Wrappers.<SakuraAiBusinessModelBinding>lambdaQuery()
                            .eq(SakuraAiBusinessModelBinding::getFuncCode, config.getFuncCode()))
                    : config.getModelBindings()).stream()
                    .filter(row -> Objects.equals(row.getModelId(), replacement.getId()) && Boolean.TRUE.equals(row.getDefaultCapability())).toList();
            if (selections.size() != 1) throw replacementError("替代模型默认能力无效");
            var selected = ModelCapabilitySelection.candidates(definitions, selections.get(0).getCapabilityCode());
            if (selected.size() != 1) throw replacementError("替代模型能力不可用");
            long routes = protocolBindingMapper.selectList(Wrappers.<SakuraAiModelProtocolBinding>lambdaQuery()
                    .eq(SakuraAiModelProtocolBinding::getModelId, replacement.getId())
                    .eq(SakuraAiModelProtocolBinding::getCapabilityCode, selected.get(0).getCode())).stream()
                    .map(row -> JSON.parseObject(row.getDefinitionJson(), ModelProtocolBinding.class))
                    .filter(route -> Boolean.TRUE.equals(route.getEnabled()) && Boolean.TRUE.equals(route.getDefaultBinding())).count();
            if (routes != 1) throw replacementError("替代模型协议不可用");
        }
        return replacement;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void prepareFunctionUpdate(SakuraAiModelFuncConfig config, String operator)
    {
        validateFunctionConfig(config);
        if (config.getId() == null || StrUtil.isBlank(config.getRemovedModelReplacementCode())) return;
        SakuraAiModelFuncConfig before = functionConfigService.getById(config.getId());
        if (before == null) return;
        Set<Long> retained = parseModelIdsSafe(config.getModelIds());
        Set<Long> removed = new LinkedHashSet<>(parseModelIdsSafe(before.getModelIds()));
        removed.removeAll(retained);
        if (removed.isEmpty()) return;
        SakuraAiModel replacement = requirePoolReplacement(config, retained);
        List<String> codes = referenceCodes.resolve(removed);
        if (codes.isEmpty()) return;
        String nextCode = replacement.getModelCode();
        String biz = config.getFuncCode();
        boolean agentsChanged = agentService.update(Wrappers.<SakuraAgent>lambdaUpdate()
                .eq(SakuraAgent::getBizCategoryCode, biz).in(SakuraAgent::getModelCode, codes).eq(SakuraAgent::getDelFlag, NORMAL)
                .set(SakuraAgent::getModelCode, nextCode).set(SakuraAgent::getUpdateBy, operator)
                .set(SakuraAgent::getUpdateTime, DateUtils.getNowDate()));
        agentPoolService.update(Wrappers.<SakuraGenAgentPool>lambdaUpdate()
                .eq(SakuraGenAgentPool::getBizCategoryCode, biz).in(SakuraGenAgentPool::getModelCode, codes).eq(SakuraGenAgentPool::getDelFlag, NORMAL)
                .set(SakuraGenAgentPool::getModelCode, nextCode).set(SakuraGenAgentPool::getResolution, null)
                .set(SakuraGenAgentPool::getAspectRatio, null).set(SakuraGenAgentPool::getUpdateBy, operator)
                .set(SakuraGenAgentPool::getUpdateTime, DateUtils.getNowDate()));
        projectGenConfigMapper.update(null, Wrappers.<SakuraProjectGenConfig>lambdaUpdate()
                .eq(SakuraProjectGenConfig::getSceneCode, biz).in(SakuraProjectGenConfig::getModelCode, codes).eq(SakuraProjectGenConfig::getDelFlag, NORMAL)
                .set(SakuraProjectGenConfig::getModelCode, nextCode).set(SakuraProjectGenConfig::getResolution, null)
                .set(SakuraProjectGenConfig::getAspectRatio, null).set(SakuraProjectGenConfig::getUpdateBy, operator)
                .set(SakuraProjectGenConfig::getUpdateTime, DateUtils.getNowDate()));
        log.info("文本模型移出当前业务池并迁移引用: funcCode={}, removedIds={}, replacementId={}, agentsChanged={}, operator={}",
                biz, removed, replacement.getId(), agentsChanged, operator);
    }

    private ServiceException replacementError(String message)
    {
        log.info("业务池替代模型校验失败: {}", message);
        return new ServiceException(message);
    }

    private SakuraAiModelFuncConfig requireFunctionConfig(Long id)
    {
        SakuraAiModelFuncConfig config = functionConfigService.getOne(
                Wrappers.<SakuraAiModelFuncConfig>lambdaQuery()
                        .select(SakuraAiModelFuncConfig::getId, SakuraAiModelFuncConfig::getFuncCode,
                                SakuraAiModelFuncConfig::getFuncName)
                        .eq(SakuraAiModelFuncConfig::getId, id)
                        .eq(SakuraAiModelFuncConfig::getDelFlag, NORMAL)
                        .last("limit 1"), false);
        if (Objects.isNull(config))
        {
            throw new ServiceException("模型池配置不存在");
        }
        return config;
    }

    private SakuraAiModel requireModel(Long id, boolean forUpdate)
    {
        if (Objects.isNull(id))
        {
            throw new ServiceException("模型ID不能为空");
        }
        var wrapper = Wrappers.<SakuraAiModel>lambdaQuery()
                .select(SakuraAiModel::getId, SakuraAiModel::getModelCode, SakuraAiModel::getModelName,
                        SakuraAiModel::getProviderId, SakuraAiModel::getModelType, SakuraAiModel::getGenerateMode,
                        SakuraAiModel::getStatus, SakuraAiModel::getDelFlag)
                .eq(SakuraAiModel::getId, id)
                .eq(SakuraAiModel::getDelFlag, NORMAL)
                .last(forUpdate ? "limit 1 for update" : "limit 1");
        SakuraAiModel model = modelService.getOne(wrapper, false);
        if (Objects.isNull(model))
        {
            throw new ServiceException("模型不存在或已下线");
        }
        return model;
    }

    private SakuraAgent requireAgent(Long id, boolean forUpdate)
    {
        if (Objects.isNull(id))
        {
            throw new ServiceException("智能体ID不能为空");
        }
        var wrapper = Wrappers.<SakuraAgent>lambdaQuery()
                .select(SakuraAgent::getId, SakuraAgent::getAgentCode, SakuraAgent::getName,
                        SakuraAgent::getBizCategoryCode, SakuraAgent::getStatus, SakuraAgent::getDelFlag)
                .eq(SakuraAgent::getId, id)
                .eq(SakuraAgent::getDelFlag, NORMAL)
                .last(forUpdate ? "limit 1 for update" : "limit 1");
        SakuraAgent agent = agentService.getOne(wrapper, false);
        if (Objects.isNull(agent))
        {
            throw new ServiceException("智能体不存在或已下线");
        }
        return agent;
    }

    private SakuraAiModel requireReplacementModel(SakuraAiModel target, String replacementCode)
    {
        SakuraAiModel replacement = modelService.getOne(Wrappers.<SakuraAiModel>lambdaQuery()
                .select(SakuraAiModel::getId, SakuraAiModel::getModelCode, SakuraAiModel::getModelName,
                        SakuraAiModel::getProviderId, SakuraAiModel::getModelType, SakuraAiModel::getGenerateMode,
                        SakuraAiModel::getStatus, SakuraAiModel::getDelFlag)
                .eq(SakuraAiModel::getModelCode, replacementCode)
                .eq(SakuraAiModel::getDelFlag, NORMAL)
                .last("limit 1"), false);
        if (Objects.isNull(replacement) || !Objects.equals(NORMAL, replacement.getStatus()))
        {
            throw new ServiceException("替代模型不存在或未启用");
        }
        if (Objects.equals(target.getId(), replacement.getId()))
        {
            throw new ServiceException("替代模型不能与待下线模型相同");
        }
        if (!Objects.equals(target.getModelType(), replacement.getModelType()))
        {
            throw new ServiceException("替代模型必须与待下线模型类型一致");
        }
        SakuraAiProvider provider = providerService.getOne(Wrappers.<SakuraAiProvider>lambdaQuery()
                .select(SakuraAiProvider::getId, SakuraAiProvider::getStatus, SakuraAiProvider::getDelFlag)
                .eq(SakuraAiProvider::getId, replacement.getProviderId())
                .eq(SakuraAiProvider::getStatus, NORMAL)
                .eq(SakuraAiProvider::getDelFlag, NORMAL)
                .last("limit 1"), false);
        if (Objects.isNull(provider))
        {
            throw new ServiceException("替代模型所属服务商未启用");
        }
        return replacement;
    }

    private List<SakuraAiModelFuncConfig> findModelPools(Long modelId)
    {
        List<SakuraAiModelFuncConfig> all = functionConfigService.list(
                Wrappers.<SakuraAiModelFuncConfig>lambdaQuery()
                        .select(SakuraAiModelFuncConfig::getId, SakuraAiModelFuncConfig::getFuncCode,
                                SakuraAiModelFuncConfig::getFuncName, SakuraAiModelFuncConfig::getModelType,
                                SakuraAiModelFuncConfig::getGenerateMode, SakuraAiModelFuncConfig::getModelIds,
                                SakuraAiModelFuncConfig::getStatus)
                        .eq(SakuraAiModelFuncConfig::getDelFlag, NORMAL));
        if (CollectionUtil.isEmpty(all))
        {
            return Collections.emptyList();
        }
        return all.stream().filter(config -> parseModelIdsSafe(config.getModelIds()).contains(modelId))
                .collect(Collectors.toList());
    }

    private long countAgentsByModel(String modelCode)
    {
        return agentService.count(Wrappers.<SakuraAgent>lambdaQuery()
                .eq(SakuraAgent::getModelCode, modelCode).eq(SakuraAgent::getDelFlag, NORMAL));
    }

    private long countMatrixByModel(String modelCode)
    {
        return agentPoolService.count(Wrappers.<SakuraGenAgentPool>lambdaQuery()
                .eq(SakuraGenAgentPool::getModelCode, modelCode).eq(SakuraGenAgentPool::getDelFlag, NORMAL));
    }

    private long countProjectsByModel(String modelCode)
    {
        return projectGenConfigMapper.selectCount(Wrappers.<SakuraProjectGenConfig>lambdaQuery()
                .eq(SakuraProjectGenConfig::getModelCode, modelCode).eq(SakuraProjectGenConfig::getDelFlag, NORMAL));
    }

    private long countVoicesByModel(Long modelId)
    {
        return voiceLibraryService.count(Wrappers.<SakuraAiVoiceLibrary>lambdaQuery()
                .eq(SakuraAiVoiceLibrary::getModelId, modelId)
                .eq(SakuraAiVoiceLibrary::getStatus, NORMAL)
                .eq(SakuraAiVoiceLibrary::getDelFlag, NORMAL));
    }

    private long countMatrixByAgent(String agentCode)
    {
        return agentPoolService.count(Wrappers.<SakuraGenAgentPool>lambdaQuery()
                .eq(SakuraGenAgentPool::getAgentCode, agentCode).eq(SakuraGenAgentPool::getDelFlag, NORMAL));
    }

    private long countProjectsByAgent(String agentCode)
    {
        return projectGenConfigMapper.selectCount(Wrappers.<SakuraProjectGenConfig>lambdaQuery()
                .eq(SakuraProjectGenConfig::getAgentCode, agentCode).eq(SakuraProjectGenConfig::getDelFlag, NORMAL));
    }

    private void updateAgentModelReferences(String oldCode, String nextCode, String operator)
    {
        LambdaUpdateWrapper<SakuraAgent> update = Wrappers.<SakuraAgent>lambdaUpdate()
                .eq(SakuraAgent::getModelCode, oldCode)
                .eq(SakuraAgent::getDelFlag, NORMAL)
                .set(SakuraAgent::getModelCode, nextCode)
                .set(SakuraAgent::getUpdateBy, operator)
                .set(SakuraAgent::getUpdateTime, DateUtils.getNowDate());
        agentService.update(update);
    }

    private void updateMatrixModelReferences(String oldCode, String nextCode, String operator)
    {
        LambdaUpdateWrapper<SakuraGenAgentPool> update = Wrappers.<SakuraGenAgentPool>lambdaUpdate()
                .eq(SakuraGenAgentPool::getModelCode, oldCode)
                .eq(SakuraGenAgentPool::getDelFlag, NORMAL)
                .set(SakuraGenAgentPool::getModelCode, nextCode)
                .set(SakuraGenAgentPool::getUpdateBy, operator)
                .set(SakuraGenAgentPool::getUpdateTime, DateUtils.getNowDate());
        agentPoolService.update(update);
    }

    private void updateProjectModelReferences(String oldCode, String nextCode, String operator)
    {
        LambdaUpdateWrapper<SakuraProjectGenConfig> update = Wrappers.<SakuraProjectGenConfig>lambdaUpdate()
                .eq(SakuraProjectGenConfig::getModelCode, oldCode)
                .eq(SakuraProjectGenConfig::getDelFlag, NORMAL)
                .set(Objects.nonNull(nextCode), SakuraProjectGenConfig::getModelCode, nextCode)
                .set(Objects.isNull(nextCode), SakuraProjectGenConfig::getDelFlag, ROW_DELETED)
                .set(SakuraProjectGenConfig::getUpdateBy, operator)
                .set(SakuraProjectGenConfig::getUpdateTime, DateUtils.getNowDate());
        projectGenConfigMapper.update(null, update);
    }

    private void disableModelVoices(Long modelId, String operator)
    {
        voiceLibraryService.update(Wrappers.<SakuraAiVoiceLibrary>lambdaUpdate()
                .eq(SakuraAiVoiceLibrary::getModelId, modelId)
                .eq(SakuraAiVoiceLibrary::getStatus, NORMAL)
                .eq(SakuraAiVoiceLibrary::getDelFlag, NORMAL)
                .set(SakuraAiVoiceLibrary::getStatus, DISABLED)
                .set(SakuraAiVoiceLibrary::getOfflineTime, new Date())
                .set(SakuraAiVoiceLibrary::getUpdateBy, operator)
                .set(SakuraAiVoiceLibrary::getUpdateTime, DateUtils.getNowDate()));
    }

    private void updateMatrixAgentReferences(String oldCode, String nextCode, String operator)
    {
        LambdaUpdateWrapper<SakuraGenAgentPool> update = Wrappers.<SakuraGenAgentPool>lambdaUpdate()
                .eq(SakuraGenAgentPool::getAgentCode, oldCode)
                .eq(SakuraGenAgentPool::getDelFlag, NORMAL)
                .set(Objects.nonNull(nextCode), SakuraGenAgentPool::getAgentCode, nextCode)
                .set(Objects.isNull(nextCode), SakuraGenAgentPool::getDelFlag, ROW_DELETED)
                .set(SakuraGenAgentPool::getUpdateBy, operator)
                .set(SakuraGenAgentPool::getUpdateTime, DateUtils.getNowDate());
        agentPoolService.update(update);
    }

    private void updateProjectAgentReferences(String oldCode, String nextCode, String operator)
    {
        LambdaUpdateWrapper<SakuraProjectGenConfig> update = Wrappers.<SakuraProjectGenConfig>lambdaUpdate()
                .eq(SakuraProjectGenConfig::getAgentCode, oldCode)
                .eq(SakuraProjectGenConfig::getDelFlag, NORMAL)
                .set(Objects.nonNull(nextCode), SakuraProjectGenConfig::getAgentCode, nextCode)
                .set(Objects.isNull(nextCode), SakuraProjectGenConfig::getDelFlag, ROW_DELETED)
                .set(SakuraProjectGenConfig::getUpdateBy, operator)
                .set(SakuraProjectGenConfig::getUpdateTime, DateUtils.getNowDate());
        projectGenConfigMapper.update(null, update);
    }

    private int softDeleteModel(Long modelId, String operator)
    {
        return modelService.update(Wrappers.<SakuraAiModel>lambdaUpdate()
                .eq(SakuraAiModel::getId, modelId)
                .eq(SakuraAiModel::getDelFlag, NORMAL)
                .set(SakuraAiModel::getStatus, DISABLED)
                .set(SakuraAiModel::getDelFlag, MODEL_DELETED)
                .set(SakuraAiModel::getUpdateBy, operator)
                .set(SakuraAiModel::getUpdateTime, DateUtils.getNowDate())) ? 1 : 0;
    }

    private int softDeleteAgent(Long agentId, String operator)
    {
        return agentService.update(Wrappers.<SakuraAgent>lambdaUpdate()
                .eq(SakuraAgent::getId, agentId)
                .eq(SakuraAgent::getDelFlag, NORMAL)
                .set(SakuraAgent::getStatus, AGENT_DISABLED)
                .set(SakuraAgent::getDelFlag, CONFIG_DELETED)
                .set(SakuraAgent::getUpdateBy, operator)
                .set(SakuraAgent::getUpdateTime, DateUtils.getNowDate())) ? 1 : 0;
    }

    private List<Long> replaceModelId(List<Long> ids, Long targetId, Long replacementId)
    {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (Long id : ids)
        {
            if (Objects.equals(id, targetId))
            {
                if (Objects.nonNull(replacementId))
                {
                    result.add(replacementId);
                }
            }
            else if (Objects.nonNull(id))
            {
                result.add(id);
            }
        }
        return new ArrayList<>(result);
    }

    private List<Long> parseModelIdsStrict(String json)
    {
        if (StrUtil.isBlank(json))
        {
            return new ArrayList<>();
        }
        try
        {
            List<Long> parsed = JSONUtil.toList(json, Long.class);
            LinkedHashSet<Long> normalized = parsed.stream().filter(Objects::nonNull)
                    .filter(id -> id > 0).collect(Collectors.toCollection(LinkedHashSet::new));
            return new ArrayList<>(normalized);
        }
        catch (Exception e)
        {
            throw new ServiceException("模型ID列表必须是合法的JSON数组");
        }
    }

    private Set<Long> parseModelIdsSafe(String json)
    {
        try
        {
            return new LinkedHashSet<>(parseModelIdsStrict(json));
        }
        catch (Exception e)
        {
            log.warn("模型池 model_ids 解析失败，按空集合处理: {}", json);
            return Collections.emptySet();
        }
    }

    private void requireConfirmed(RetireResourceRequest request)
    {
        if (Objects.isNull(request) || !Boolean.TRUE.equals(request.getConfirmed()))
        {
            throw new ServiceException("请先查看并确认影响范围");
        }
    }

    private OrchestrationImpactItemVO item(String type, String label, long count, String action)
    {
        return new OrchestrationImpactItemVO(type, label, count, action);
    }
}
