package com.sakura.sakura.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.sakura.sakura.support.ModelInputRequirementResolver;
import com.sakura.sakura.support.ModelImageUrlProxyTemplate;
import com.sakura.common.exception.ServiceException;
import com.sakura.common.utils.DateUtils;
import com.sakura.common.utils.SecurityUtils;
import com.sakura.common.utils.ProviderEndpointUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import com.sakura.sakura.service.support.ModelBillingActivationValidator;
import com.sakura.sakura.service.support.ModelConfigurationMerge;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.mapper.SakuraAiModelMapper;
import com.sakura.sakura.domain.SakuraAiModel;
import com.sakura.sakura.domain.SakuraAiModelFuncConfig;
import com.sakura.sakura.domain.SakuraAiProvider;
import com.sakura.sakura.domain.vo.SakuraRealModelGroupVo;
import com.sakura.sakura.domain.vo.SakuraRealModelItemVo;
import com.sakura.sakura.service.ISakuraAiModelFuncConfigService;
import com.sakura.sakura.service.ISakuraAiModelService;
import com.sakura.sakura.service.ISakuraAiProviderService;

/**
 * AI底层模型配置与算力计费Service业务层处理
 *
 * @author 视觉AID
 */
@Slf4j
@Service
public class SakuraAiModelServiceImpl extends ServiceImpl<SakuraAiModelMapper, SakuraAiModel> implements ISakuraAiModelService
{
    /** 删除标志：未删除 */
    private static final String DEL_FLAG_NORMAL = "0";

    /** 状态：正常（启用） */
    private static final String STATUS_NORMAL = "0";

    /** 删除标志：已删除 */
    private static final String DEL_FLAG_DELETED = "1";

    /** 状态：停用 */
    private static final String STATUS_DISABLED = "1";

    @Autowired
    private SakuraAiModelMapper sakuraAiModelMapper;

    /** 服务商查询：真实模型总览带出服务商名称 */
    @Autowired
    private ISakuraAiProviderService sakuraAiProviderService;

    /** 功能模型池配置：删除模型前校验池内引用 */
    @Autowired
    private ISakuraAiModelFuncConfigService sakuraAiModelFuncConfigService;

    /**
     * 查询AI底层模型配置与算力计费
     *
     * @param id AI底层模型配置与算力计费主键
     * @return AI底层模型配置与算力计费
     */
    @Override
    public SakuraAiModel selectSakuraAiModelById(Long id)
    {
        SakuraAiModel model = this.getOne(Wrappers.<SakuraAiModel>lambdaQuery()
                .eq(SakuraAiModel::getId, id)
                .eq(SakuraAiModel::getDelFlag, DEL_FLAG_NORMAL)
                .last("limit 1"), false);
        if (Objects.nonNull(model)) {
            // 详情同样带上派生的输入要求标签，供后台编辑弹窗展示
            model.setInputRequirement(ModelInputRequirementResolver.resolve(
                    model.getModelType(), model.getGenerateMode(),
                    model.getCapabilityJson(), model.getSupportsImageInput()));
        }
        return model;
    }

    /**
     * 查询AI底层模型配置与算力计费列表
     *
     * @param sakuraAiModel AI底层模型配置与算力计费
     * @return AI底层模型配置与算力计费
     */
    @Override
    public List<SakuraAiModel> selectSakuraAiModelList(SakuraAiModel sakuraAiModel)
    {
        LambdaQueryWrapper<SakuraAiModel> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SakuraAiModel::getDelFlag, DEL_FLAG_NORMAL);
        // 按服务商ID过滤
        if (sakuraAiModel.getProviderId() != null) {
            wrapper.eq(SakuraAiModel::getProviderId, sakuraAiModel.getProviderId());
        }
        // 按模型展示码模糊搜索
        if (StrUtil.isNotBlank(sakuraAiModel.getModelCode())) {
            wrapper.like(SakuraAiModel::getModelCode, sakuraAiModel.getModelCode());
        }
        // 按真实上游模型名模糊搜索
        if (StrUtil.isNotBlank(sakuraAiModel.getRealModelCode())) {
            wrapper.like(SakuraAiModel::getRealModelCode, sakuraAiModel.getRealModelCode());
        }
        // 按模型名称模糊搜索
        if (StrUtil.isNotBlank(sakuraAiModel.getModelName())) {
            wrapper.like(SakuraAiModel::getModelName, sakuraAiModel.getModelName());
        }
        // 按模型分类过滤
        if (StrUtil.isNotBlank(sakuraAiModel.getModelType())) {
            wrapper.eq(SakuraAiModel::getModelType, sakuraAiModel.getModelType());
        }
        // 按生成模式过滤（对 model_type 大类做进一步细分）
        if (StrUtil.isNotBlank(sakuraAiModel.getGenerateMode())) {
            wrapper.apply("(EXISTS (SELECT 1 FROM sakura_ai_model_capability c WHERE c.model_id = sakura_ai_model.id AND c.generate_mode = {0}) "
                    + "OR (NOT EXISTS (SELECT 1 FROM sakura_ai_model_capability c WHERE c.model_id = sakura_ai_model.id) AND generate_mode = {0}))", sakuraAiModel.getGenerateMode());
        }
        // 按状态过滤
        if (StrUtil.isNotBlank(sakuraAiModel.getStatus())) {
            wrapper.eq(SakuraAiModel::getStatus, sakuraAiModel.getStatus());
        }
        // 按优先级降序
        wrapper.orderByDesc(SakuraAiModel::getPriority);
        List<SakuraAiModel> list = this.list(wrapper);
        // 统一推导输入要求标签（text_only/image_optional/image_required/video_required），
        // 供后台管理列表展示与模型池选择器按输入要求筛选
        for (SakuraAiModel model : list) {
            model.setInputRequirement(ModelInputRequirementResolver.resolve(
                    model.getModelType(), model.getGenerateMode(),
                    model.getCapabilityJson(), model.getSupportsImageInput()));
        }
        // inputRequirement 是派生标签而非库表字段，按查询入参在内存过滤
        if (StrUtil.isNotBlank(sakuraAiModel.getInputRequirement())) {
            String target = sakuraAiModel.getInputRequirement();
            list = list.stream()
                    .filter(m -> Objects.equals(target, m.getInputRequirement()))
                    .collect(Collectors.toList());
        }
        return list;
    }

    /**
     * 新增AI底层模型配置与算力计费
     *
     * @param sakuraAiModel AI底层模型配置与算力计费
     * @return 结果
     */
    @Override
    public int insertSakuraAiModel(SakuraAiModel sakuraAiModel)
    {
        ModelConfigurationMerge.validate(sakuraAiModel.getCapabilityJson());
        ModelConfigurationMerge.validate(sakuraAiModel.getBillingRuleJson());
        sakuraAiModel.setConfigVersion(0L);
        if (sakuraAiModel.getImageUrlProxyEnabled() == null) {
            sakuraAiModel.setImageUrlProxyEnabled(Boolean.FALSE);
        }
        sakuraAiModel.setImageUrlProxyTemplate(ModelImageUrlProxyTemplate.normalizeAndValidate(
                sakuraAiModel.getImageUrlProxyEnabled(), sakuraAiModel.getImageUrlProxyTemplate()));
        validateModelCodeNotBlank(sakuraAiModel.getModelCode());
        validateAndNormalizeApiSuffix(sakuraAiModel);
        ModelBillingActivationValidator.validateIfRequiredAndEnabled(sakuraAiModel);
        // 模型代码用于稳定引用；真实模型身份及能力由模型定义服务统一校验。
        validateModelCodeUnique(sakuraAiModel.getModelCode(), null);
        sakuraAiModel.setCreateTime(DateUtils.getNowDate());
        sakuraAiModel.setCreateBy(currentUsername());
        try {
            return this.save(sakuraAiModel) ? 1 : 0;
        } catch (DuplicateKeyException e) {
            log.error("SakuraAiModel 新增触发唯一键冲突, modelCode={}", sakuraAiModel.getModelCode(), e);
            throw new ServiceException("编码已存在");
        }
    }

    /**
     * 修改AI底层模型配置与算力计费
     *
     * @param sakuraAiModel AI底层模型配置与算力计费
     * @return 结果
     */
    @Override
    public int updateSakuraAiModel(SakuraAiModel sakuraAiModel)
    {
        validateModelCodeNotBlank(sakuraAiModel.getModelCode());
        validateAndNormalizeApiSuffix(sakuraAiModel);
        validateModelCodeUnique(sakuraAiModel.getModelCode(), sakuraAiModel.getId());
        // 校验性查询：只取主键，确认记录存在
        SakuraAiModel before = this.getOne(
                Wrappers.<SakuraAiModel>lambdaQuery()
                        .select(SakuraAiModel::getId, SakuraAiModel::getStatus, SakuraAiModel::getBillingMode,
                                SakuraAiModel::getBillingRuleJson, SakuraAiModel::getCostCredits,
                                SakuraAiModel::getCapabilityJson, SakuraAiModel::getConfigVersion,
                                SakuraAiModel::getImageUrlProxyEnabled, SakuraAiModel::getImageUrlProxyTemplate)
                        .eq(SakuraAiModel::getId, sakuraAiModel.getId())
                        .last("limit 1"),
                false);
        if (Objects.isNull(before)) {
            log.error("SakuraAiModel 更新失败：记录不存在, id={}", sakuraAiModel.getId());
            throw new ServiceException("模型不存在");
        }
        long expectedVersion = before.getConfigVersion() == null ? 0L : before.getConfigVersion();
        if (sakuraAiModel.getConfigVersion() != null && sakuraAiModel.getConfigVersion() != expectedVersion) {
            log.info("模型配置版本已变化: id={}", sakuraAiModel.getId());
            throw new ServiceException("配置已更新请刷新");
        }
        if (sakuraAiModel.getCapabilityJson() != null) {
            sakuraAiModel.setCapabilityJson(ModelConfigurationMerge.merge(
                    before.getCapabilityJson(), sakuraAiModel.getCapabilityJson()));
        }
        if (sakuraAiModel.getBillingRuleJson() != null) {
            sakuraAiModel.setBillingRuleJson(ModelConfigurationMerge.merge(
                    before.getBillingRuleJson(), sakuraAiModel.getBillingRuleJson()));
        }
        Boolean effectiveProxyEnabled = sakuraAiModel.getImageUrlProxyEnabled() == null
                ? before.getImageUrlProxyEnabled() : sakuraAiModel.getImageUrlProxyEnabled();
        String effectiveProxyTemplate = sakuraAiModel.getImageUrlProxyTemplate() == null
                ? before.getImageUrlProxyTemplate() : sakuraAiModel.getImageUrlProxyTemplate();
        String normalizedProxyTemplate = ModelImageUrlProxyTemplate.normalizeAndValidate(
                effectiveProxyEnabled, effectiveProxyTemplate);
        if (sakuraAiModel.getImageUrlProxyTemplate() != null) {
            sakuraAiModel.setImageUrlProxyTemplate(normalizedProxyTemplate);
        }
        SakuraAiModel effective = mergeBillingFields(before, sakuraAiModel);
        // 明确声明严格计费的模型，只要保存后的有效配置仍为启用态，就持续校验；因此已启用后清空
        // 价格或禁用全部 SKU 也会被拒绝。普通历史模型不受此新增闸门影响。
        ModelBillingActivationValidator.validateUpdate(before, effective);
        sakuraAiModel.setUpdateTime(DateUtils.getNowDate());
        sakuraAiModel.setUpdateBy(currentUsername());
        try {
            sakuraAiModel.setConfigVersion(Math.addExact(expectedVersion, 1L));
            boolean updated = this.update(sakuraAiModel, Wrappers.<SakuraAiModel>lambdaUpdate()
                    .eq(SakuraAiModel::getId, sakuraAiModel.getId())
                    .eq(SakuraAiModel::getConfigVersion, expectedVersion));
            if (!updated) {
                log.info("模型配置并发保存冲突: id={}", sakuraAiModel.getId());
                throw new ServiceException("配置已更新请刷新");
            }
            return 1;
        } catch (DuplicateKeyException e) {
            log.error("SakuraAiModel 更新触发唯一键冲突, id={}, modelCode={}",
                    sakuraAiModel.getId(), sakuraAiModel.getModelCode(), e);
            throw new ServiceException("编码已存在");
        }
    }

    /** 校验并规范化模型提交相对路径。 */
    private void validateAndNormalizeApiSuffix(SakuraAiModel sakuraAiModel)
    {
        if (Objects.isNull(sakuraAiModel) || StrUtil.isBlank(sakuraAiModel.getApiSuffix()))
        {
            return;
        }
        try
        {
            sakuraAiModel.setApiSuffix(ProviderEndpointUtils.normalizeSubmitPath(sakuraAiModel.getApiSuffix()));
        }
        catch (IllegalArgumentException ex)
        {
            log.error("SakuraAiModel 保存失败：接口路径无效, modelCode={}, reason={}",
                    sakuraAiModel.getModelCode(), ex.getMessage());
            throw new ServiceException("接口路径无效");
        }
    }

    /**
     * 批量删除AI底层模型配置与算力计费
     *
     * @param ids 需要删除的AI底层模型配置与算力计费主键
     * @return 结果
     */
    @Override
    public int deleteSakuraAiModelByIds(Long[] ids)
    {
        validateModelNotReferenced(Arrays.asList(ids));
        return this.update(Wrappers.<SakuraAiModel>lambdaUpdate()
                .in(SakuraAiModel::getId, Arrays.asList(ids))
                .eq(SakuraAiModel::getDelFlag, DEL_FLAG_NORMAL)
                .set(SakuraAiModel::getStatus, STATUS_DISABLED)
                .set(SakuraAiModel::getDelFlag, DEL_FLAG_DELETED)
                .set(SakuraAiModel::getUpdateBy, currentUsername())
                .set(SakuraAiModel::getUpdateTime, DateUtils.getNowDate())) ? 1 : 0;
    }

    /**
     * 删除AI底层模型配置与算力计费信息
     *
     * @param id AI底层模型配置与算力计费主键
     * @return 结果
     */
    @Override
    public int deleteSakuraAiModelById(Long id)
    {
        validateModelNotReferenced(Arrays.asList(id));
        return this.update(Wrappers.<SakuraAiModel>lambdaUpdate()
                .eq(SakuraAiModel::getId, id)
                .eq(SakuraAiModel::getDelFlag, DEL_FLAG_NORMAL)
                .set(SakuraAiModel::getStatus, STATUS_DISABLED)
                .set(SakuraAiModel::getDelFlag, DEL_FLAG_DELETED)
                .set(SakuraAiModel::getUpdateBy, currentUsername())
                .set(SakuraAiModel::getUpdateTime, DateUtils.getNowDate())) ? 1 : 0;
    }

    /**
     * 真实模型总览：按真实上游模型名聚合全部未删除模型，同真实模型的多个展示模型归入同组
     *
     * @param keyword 搜索关键字（匹配真实模型名/展示码/展示名称，可空）
     * @return 真实模型分组列表
     */
    @Override
    public List<SakuraRealModelGroupVo> selectRealModelOverview(String keyword)
    {
        // 总览查询：只取分组展示所需字段
        List<SakuraAiModel> models = this.list(
                Wrappers.<SakuraAiModel>lambdaQuery()
                        .select(SakuraAiModel::getId, SakuraAiModel::getModelCode, SakuraAiModel::getModelName,
                                SakuraAiModel::getRealModelCode, SakuraAiModel::getModelType,
                                SakuraAiModel::getGenerateMode, SakuraAiModel::getProviderId,
                                SakuraAiModel::getStatus, SakuraAiModel::getPriority)
                        .eq(SakuraAiModel::getDelFlag, DEL_FLAG_NORMAL)
                        .orderByDesc(SakuraAiModel::getPriority));
        if (CollectionUtil.isEmpty(models)) {
            return new ArrayList<>();
        }
        // 服务商名称映射（含停用服务商，保证总览可见）
        Map<Long, String> providerNames = loadProviderNames();
        // 按真实上游模型名聚合（空 real_model_code 回退 model_code）
        Map<String, List<SakuraAiModel>> grouped = new LinkedHashMap<>();
        for (SakuraAiModel model : models) {
            String effectiveCode = resolveEffectiveRealCode(model.getRealModelCode(), model.getModelCode());
            grouped.computeIfAbsent(effectiveCode, k -> new ArrayList<>()).add(model);
        }
        String searchKey = StrUtil.isBlank(keyword) ? null : keyword.trim().toLowerCase();
        List<SakuraRealModelGroupVo> groups = new ArrayList<>();
        for (Map.Entry<String, List<SakuraAiModel>> entry : grouped.entrySet()) {
            // 关键字过滤：真实模型名、组内任一展示码或展示名称命中即保留
            if (Objects.nonNull(searchKey) && !matchesKeyword(entry.getKey(), entry.getValue(), searchKey)) {
                continue;
            }
            groups.add(buildRealModelGroup(entry.getKey(), entry.getValue(), providerNames));
        }
        // 多模型组置顶（同真实模型下多个模型代码便于集中管理），其余按真实模型名排序
        groups.sort((a, b) -> {
            int multi = Integer.compare(b.getTotalCount(), a.getTotalCount());
            if (multi != 0) {
                return multi;
            }
            return a.getRealModelCode().compareToIgnoreCase(b.getRealModelCode());
        });
        return groups;
    }

    /**
     * 校验展示码非空。
     *
     * @param modelCode 模型展示/选择码
     */
    private void validateModelCodeNotBlank(String modelCode)
    {
        if (StrUtil.isBlank(modelCode)) {
            log.error("SakuraAiModel 保存失败：model_code 为空");
            throw new ServiceException("编码不能空");
        }
    }

    /**
     * 校验展示码全表唯一（仅必要字段：id + model_code，排除自身）。
     *
     * @param modelCode 模型展示/选择码
     * @param selfId    更新时为自身主键，新增时为 null
     */
    private void validateModelCodeUnique(String modelCode, Long selfId)
    {
        SakuraAiModel exist = this.getOne(
                Wrappers.<SakuraAiModel>lambdaQuery()
                        // 校验性查询：只取主键与展示码，减少列返回
                        .select(SakuraAiModel::getId, SakuraAiModel::getModelCode)
                        .eq(SakuraAiModel::getModelCode, modelCode)
                        .eq(SakuraAiModel::getDelFlag, DEL_FLAG_NORMAL)
                        .last("limit 1"),
                false);
        if (Objects.nonNull(exist) && !Objects.equals(exist.getId(), selfId)) {
            log.error("SakuraAiModel 保存失败：model_code 重复, modelCode={}, existId={}, selfId={}",
                    modelCode, exist.getId(), selfId);
            throw new ServiceException("编码已存在");
        }
    }

    /**
     * 判断模型状态是否为启用。
     *
     * @param status 状态值
     */
    private boolean isEnabled(String status)
    {
        return Objects.equals(STATUS_NORMAL, status);
    }

    /** 合并 partial update 与库中旧值，避免未回传字段的 null 被误判为清空。 */
    private SakuraAiModel mergeBillingFields(SakuraAiModel before, SakuraAiModel update)
    {
        SakuraAiModel effective = new SakuraAiModel();
        effective.setStatus(update.getStatus() == null ? before.getStatus() : update.getStatus());
        effective.setBillingMode(update.getBillingMode() == null ? before.getBillingMode() : update.getBillingMode());
        effective.setBillingRuleJson(update.getBillingRuleJson() == null
                ? before.getBillingRuleJson() : update.getBillingRuleJson());
        effective.setCostCredits(update.getCostCredits() == null ? before.getCostCredits() : update.getCostCredits());
        effective.setCapabilityJson(update.getCapabilityJson() == null
                ? before.getCapabilityJson() : update.getCapabilityJson());
        return effective;
    }

    /**
     * 解析生效的真实模型名：real_model_code 为空时回退 model_code。
     *
     * @param realModelCode 真实上游模型名
     * @param modelCode     模型展示/选择码
     */
    private String resolveEffectiveRealCode(String realModelCode, String modelCode)
    {
        return StrUtil.isBlank(realModelCode) ? StrUtil.trim(modelCode) : StrUtil.trim(realModelCode);
    }

    /**
     * 校验待删除模型未被功能模型池引用，被引用时拒绝删除。
     *
     * @param ids 待删除模型主键集合
     */
    private void validateModelNotReferenced(List<Long> ids)
    {
        if (CollectionUtil.isEmpty(ids)) {
            return;
        }
        // 引用校验查询：只取功能名与模型池 ID 列表（含停用配置，防止恢复后引用悬空）
        List<SakuraAiModelFuncConfig> configs = sakuraAiModelFuncConfigService.list(
                Wrappers.<SakuraAiModelFuncConfig>lambdaQuery()
                        .select(SakuraAiModelFuncConfig::getId, SakuraAiModelFuncConfig::getFuncName,
                                SakuraAiModelFuncConfig::getModelIds)
                        .eq(SakuraAiModelFuncConfig::getDelFlag, DEL_FLAG_NORMAL));
        if (CollectionUtil.isEmpty(configs)) {
            return;
        }
        for (SakuraAiModelFuncConfig config : configs) {
            Set<Long> poolIds = parseModelIds(config.getModelIds());
            for (Long id : ids) {
                if (poolIds.contains(id)) {
                    log.error("SakuraAiModel 删除失败：模型仍被功能模型池引用, modelId={}, funcName={}, configId={}",
                            id, config.getFuncName(), config.getId());
                    throw new ServiceException("模型被模型池引用");
                }
            }
        }
    }

    /**
     * 解析模型池 ID JSON 数组，格式异常时返回空集合。
     *
     * @param modelIdsJson 模型池 ID JSON 数组字符串
     */
    private Set<Long> parseModelIds(String modelIdsJson)
    {
        if (StrUtil.isBlank(modelIdsJson)) {
            return Collections.emptySet();
        }
        try {
            List<Long> parsed = JSONUtil.toList(modelIdsJson, Long.class);
            return parsed.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("模型池 model_ids 解析失败，按空处理: {}", modelIdsJson, e);
            return Collections.emptySet();
        }
    }

    /**
     * 查询全部未删除服务商的名称映射。
     */
    private Map<Long, String> loadProviderNames()
    {
        // 名称映射查询：只取主键与名称
        List<SakuraAiProvider> providers = sakuraAiProviderService.list(
                Wrappers.<SakuraAiProvider>lambdaQuery()
                        .select(SakuraAiProvider::getId, SakuraAiProvider::getProviderName)
                        .eq(SakuraAiProvider::getDelFlag, DEL_FLAG_NORMAL));
        Map<Long, String> names = new LinkedHashMap<>();
        for (SakuraAiProvider provider : providers) {
            names.put(provider.getId(), provider.getProviderName());
        }
        return names;
    }

    /**
     * 判断分组是否命中搜索关键字（真实模型名/组内展示码/组内展示名称，不区分大小写）。
     */
    private boolean matchesKeyword(String effectiveCode, List<SakuraAiModel> members, String searchKey)
    {
        if (StrUtil.isNotBlank(effectiveCode) && effectiveCode.toLowerCase().contains(searchKey)) {
            return true;
        }
        for (SakuraAiModel member : members) {
            if (StrUtil.isNotBlank(member.getModelCode()) && member.getModelCode().toLowerCase().contains(searchKey)) {
                return true;
            }
            if (StrUtil.isNotBlank(member.getModelName()) && member.getModelName().toLowerCase().contains(searchKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 组装单个真实模型分组VO。
     */
    private SakuraRealModelGroupVo buildRealModelGroup(String effectiveCode, List<SakuraAiModel> members,
            Map<Long, String> providerNames)
    {
        SakuraRealModelGroupVo group = new SakuraRealModelGroupVo();
        group.setRealModelCode(effectiveCode);
        group.setModelType(members.get(0).getModelType());
        group.setTotalCount(members.size());
        int activeCount = 0;
        List<SakuraRealModelItemVo> items = new ArrayList<>();
        for (SakuraAiModel member : members) {
            if (isEnabled(member.getStatus())) {
                activeCount++;
            }
            SakuraRealModelItemVo item = new SakuraRealModelItemVo();
            item.setId(member.getId());
            item.setModelCode(member.getModelCode());
            item.setModelName(member.getModelName());
            item.setRealModelCode(member.getRealModelCode());
            item.setModelType(member.getModelType());
            item.setGenerateMode(member.getGenerateMode());
            item.setProviderId(member.getProviderId());
            item.setProviderName(providerNames.get(member.getProviderId()));
            item.setStatus(member.getStatus());
            item.setPriority(member.getPriority());
            items.add(item);
        }
        group.setActiveCount(activeCount);
        group.setModels(items);
        return group;
    }

    /**
     * 获取当前登录用户名（用于审计字段），未登录或异常返回 null。
     */
    private String currentUsername()
    {
        try {
            // SecurityUtils.getUsername 内部已做异常兜底
            return SecurityUtils.getUsername();
        } catch (Exception e) {
            log.warn("获取当前登录用户失败，审计字段置空: {}", e.getMessage());
            return null;
        }
    }
}
