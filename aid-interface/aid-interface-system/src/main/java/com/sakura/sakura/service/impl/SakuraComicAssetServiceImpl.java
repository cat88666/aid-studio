package com.sakura.sakura.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.sakura.sakura.domain.SakuraComicAssetCategory;
import com.sakura.sakura.enums.StyleCategoryEnum;
import com.sakura.sakura.service.ISakuraComicAssetCategoryService;
import com.sakura.sakura.vo.StyleCategoryVO;
import com.sakura.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.mapper.SakuraComicAssetMapper;
import com.sakura.sakura.domain.SakuraComicAsset;
import com.sakura.sakura.service.ISakuraComicAssetService;
import com.sakura.sakura.util.HiddenStylePromptJsonUtils;
import com.sakura.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;

/**
 * 项目提取资产Service业务层处理
 *
 * @author 视觉AID
 */
@Service
@Slf4j
public class SakuraComicAssetServiceImpl extends ServiceImpl<SakuraComicAssetMapper, SakuraComicAsset> implements ISakuraComicAssetService
{
    private static final String ASSET_TYPE_STYLE = "style";
    private static final int DEFAULT_SORT_ORDER = 1000;
    private static final int MAX_SORT_ORDER = 999999;

    @Autowired
    private ISakuraComicAssetCategoryService sakuraComicAssetCategoryService;

    /**
     * 查询项目提取资产
     *
     * @param id 项目提取资产主键
     * @return 项目提取资产
     */
    @Override
    public SakuraComicAsset selectSakuraComicAssetById(Long id)
    {
        SakuraComicAsset asset = this.getById(id);
        if (Objects.nonNull(asset)) {
            attachStyleCategories(List.of(asset));
        }
        return asset;
    }

    /**
     * 查询项目提取资产列表
     *
     * @param sakuraComicAsset 项目提取资产
     * @return 项目提取资产
     */
    @Override
    public List<SakuraComicAsset> selectSakuraComicAssetList(SakuraComicAsset sakuraComicAsset)
    {
        LambdaQueryWrapper<SakuraComicAsset> lambdaQueryWrapper = Wrappers.lambdaQuery();
        // 查询字段精简：后台列表不加载隐藏长模板，详情接口按主键读取完整记录。
        lambdaQueryWrapper.select(SakuraComicAsset::getId, SakuraComicAsset::getAssetType,
                SakuraComicAsset::getAssetName, SakuraComicAsset::getPersonalityDesc,
                SakuraComicAsset::getPromptText, SakuraComicAsset::getImageUrl,
                SakuraComicAsset::getIsRecommended, SakuraComicAsset::getSortOrder,
                SakuraComicAsset::getDelFlag, SakuraComicAsset::getCreateTime,
                SakuraComicAsset::getCreateBy, SakuraComicAsset::getUpdateTime,
                SakuraComicAsset::getUpdateBy, SakuraComicAsset::getRemark);
        // 动态查询条件
        if (StrUtil.isNotBlank(sakuraComicAsset.getAssetType())) {
            lambdaQueryWrapper.eq(SakuraComicAsset::getAssetType, sakuraComicAsset.getAssetType());
        }
        if (StrUtil.isNotBlank(sakuraComicAsset.getAssetName())) {
            lambdaQueryWrapper.like(SakuraComicAsset::getAssetName, sakuraComicAsset.getAssetName());
        }
        if (Objects.nonNull(sakuraComicAsset.getIsRecommended())) {
            lambdaQueryWrapper.eq(SakuraComicAsset::getIsRecommended, sakuraComicAsset.getIsRecommended());
        }
        String categoryCode = normalizeStyleCategoryFilter(sakuraComicAsset.getCategoryCode());
        applyStyleCategoryFilter(lambdaQueryWrapper, categoryCode);
        if (Objects.equals(ASSET_TYPE_STYLE, sakuraComicAsset.getAssetType()) || StrUtil.isNotBlank(categoryCode)) {
            lambdaQueryWrapper.orderByDesc(SakuraComicAsset::getIsRecommended)
                    .orderByAsc(SakuraComicAsset::getSortOrder)
                    .orderByAsc(SakuraComicAsset::getId);
        } else {
            lambdaQueryWrapper.orderByDesc(SakuraComicAsset::getCreateTime)
                    .orderByDesc(SakuraComicAsset::getId);
        }
        List<SakuraComicAsset> list = this.list(lambdaQueryWrapper);
        attachStyleCategories(list);
        return list;
    }

    /**
     * 新增项目提取资产
     *
     * @param sakuraComicAsset 项目提取资产
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertSakuraComicAsset(SakuraComicAsset sakuraComicAsset)
    {
        normalizeAndValidateStyleSettings(sakuraComicAsset, false);
        sakuraComicAsset.setHiddenStylePromptJson(normalizeHiddenStylePrompt(
                sakuraComicAsset.getHiddenStylePromptJson(), sakuraComicAsset.getId()));
        sakuraComicAsset.setCreateTime(DateUtils.getNowDate());
        boolean saved = this.save(sakuraComicAsset);
        if (saved) {
            replaceCategoryRelations(sakuraComicAsset.getId(), sakuraComicAsset.getCategoryCodes());
        }
        return saved ? 1 : 0;
    }

    /**
     * 修改项目提取资产
     *
     * @param sakuraComicAsset 项目提取资产
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateSakuraComicAsset(SakuraComicAsset sakuraComicAsset)
    {
        normalizeAndValidateStyleSettings(sakuraComicAsset, true);
        sakuraComicAsset.setHiddenStylePromptJson(normalizeHiddenStylePrompt(
                sakuraComicAsset.getHiddenStylePromptJson(), sakuraComicAsset.getId()));
        sakuraComicAsset.setUpdateTime(DateUtils.getNowDate());
        boolean updated = this.updateById(sakuraComicAsset);
        if (updated) {
            replaceCategoryRelations(sakuraComicAsset.getId(), sakuraComicAsset.getCategoryCodes());
        }
        return updated ? 1 : 0;
    }

    /**
     * 批量删除项目提取资产
     *
     * @param ids 需要删除的项目提取资产主键
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteSakuraComicAssetByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        List<Long> idList = Arrays.asList(ids);
        boolean removed = this.removeByIds(idList);
        if (!removed)
        {
            return 0;
        }
        sakuraComicAssetCategoryService.remove(Wrappers.<SakuraComicAssetCategory>lambdaQuery()
                .in(SakuraComicAssetCategory::getAssetId, idList));
        return 1;
    }

    /**
     * 删除项目提取资产信息
     *
     * @param id 项目提取资产主键
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteSakuraComicAssetById(Long id)
    {
        if (id == null)
        {
            return 0;
        }
        boolean removed = this.removeById(id);
        if (!removed)
        {
            return 0;
        }
        sakuraComicAssetCategoryService.remove(Wrappers.<SakuraComicAssetCategory>lambdaQuery()
                .eq(SakuraComicAssetCategory::getAssetId, id));
        return 1;
    }

    @Override
    public String normalizeStyleCategoryFilter(String categoryCode) {
        if (StrUtil.isBlank(categoryCode) || Objects.equals(StyleCategoryEnum.ALL_CODE, categoryCode.trim())) {
            return null;
        }
        String normalized = categoryCode.trim();
        if (Objects.isNull(StyleCategoryEnum.fromCode(normalized))) {
            log.error("风格分类筛选非法: categoryCode={}", categoryCode);
            throw new ServiceException("分类错误");
        }
        return normalized;
    }

    @Override
    public void applyStyleCategoryFilter(LambdaQueryWrapper<SakuraComicAsset> wrapper, String categoryCode) {
        if (StrUtil.isBlank(categoryCode)) {
            return;
        }
        wrapper.eq(SakuraComicAsset::getAssetType, ASSET_TYPE_STYLE);
        wrapper.apply("EXISTS (SELECT 1 FROM sakura_comic_asset_category ac "
                + "WHERE ac.asset_id = sakura_comic_asset.id AND ac.category_code = {0})", categoryCode);
    }

    @Override
    public void attachStyleCategories(List<SakuraComicAsset> assets) {
        if (CollectionUtil.isEmpty(assets)) {
            return;
        }
        List<Long> styleIds = assets.stream()
                .filter(a -> Objects.equals(ASSET_TYPE_STYLE, a.getAssetType()))
                .map(SakuraComicAsset::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, List<StyleCategoryVO>> grouped = new HashMap<>();
        if (CollectionUtil.isNotEmpty(styleIds)) {
            List<SakuraComicAssetCategory> relations = sakuraComicAssetCategoryService.list(
                    Wrappers.<SakuraComicAssetCategory>lambdaQuery()
                            .select(SakuraComicAssetCategory::getAssetId, SakuraComicAssetCategory::getCategoryCode)
                            .in(SakuraComicAssetCategory::getAssetId, styleIds));
            grouped = relations.stream()
                    .filter(r -> Objects.nonNull(StyleCategoryEnum.fromCode(r.getCategoryCode())))
                    .sorted(Comparator.comparingInt(r ->
                            StyleCategoryEnum.fromCode(r.getCategoryCode()).getSortOrder()))
                    .collect(Collectors.groupingBy(SakuraComicAssetCategory::getAssetId,
                            HashMap::new,
                            Collectors.mapping(r -> toCategoryVO(
                                            StyleCategoryEnum.fromCode(r.getCategoryCode())),
                                    Collectors.toList())));
        }
        for (SakuraComicAsset asset : assets) {
            List<StyleCategoryVO> categories = new ArrayList<>(grouped.getOrDefault(asset.getId(), List.of()));
            asset.setCategories(categories);
            asset.setCategoryCodes(categories.stream().map(StyleCategoryVO::getCode).toList());
        }
    }

    @Override
    public List<StyleCategoryVO> listStyleCategoryOptions(boolean includeAll) {
        List<StyleCategoryVO> result = new ArrayList<>();
        if (includeAll) {
            result.add(new StyleCategoryVO(StyleCategoryEnum.ALL_CODE, "全部"));
        }
        result.addAll(StyleCategoryEnum.sortedValues().stream()
                .map(this::toCategoryVO)
                .toList());
        return result;
    }

    private void normalizeAndValidateStyleSettings(SakuraComicAsset asset, boolean editing) {
        if (Objects.isNull(asset)) {
            log.error("官方素材保存失败: 请求为空");
            throw new ServiceException("参数错误");
        }
        SakuraComicAsset current = null;
        if (editing) {
            if (Objects.isNull(asset.getId())) {
                log.error("官方素材修改失败: ID为空");
                throw new ServiceException("参数错误");
            }
            current = this.getById(asset.getId());
            if (Objects.isNull(current)) {
                log.error("官方素材修改失败: id={}", asset.getId());
                throw new ServiceException("素材不存在");
            }
        }
        String assetType = StrUtil.isBlank(asset.getAssetType()) && Objects.nonNull(current)
                ? current.getAssetType() : StrUtil.trim(asset.getAssetType());
        asset.setAssetType(assetType);
        int sortOrder = Objects.isNull(asset.getSortOrder())
                ? (Objects.nonNull(current) && Objects.nonNull(current.getSortOrder())
                        ? current.getSortOrder() : DEFAULT_SORT_ORDER)
                : asset.getSortOrder();
        if (sortOrder < 0 || sortOrder > MAX_SORT_ORDER) {
            log.error("官方素材排序号非法: id={}, sortOrder={}", asset.getId(), sortOrder);
            throw new ServiceException("排序号错误");
        }
        asset.setSortOrder(sortOrder);

        if (!Objects.equals(ASSET_TYPE_STYLE, assetType)) {
            asset.setIsRecommended(false);
            asset.setCategories(List.of());
            asset.setCategoryCodes(List.of());
            return;
        }
        if (Objects.isNull(asset.getIsRecommended()) && Objects.nonNull(current)) {
            asset.setIsRecommended(Boolean.TRUE.equals(current.getIsRecommended()));
        } else {
            asset.setIsRecommended(Boolean.TRUE.equals(asset.getIsRecommended()));
        }
        List<String> categoryCodes = asset.getCategoryCodes();
        if (categoryCodes == null && Objects.nonNull(current)) {
            attachStyleCategories(List.of(current));
            categoryCodes = current.getCategoryCodes();
        }
        if (CollectionUtil.isEmpty(categoryCodes)) {
            log.error("官方风格分类为空: id={}", asset.getId());
            throw new ServiceException("请选择分类");
        }
        Set<String> distinctCodes = new LinkedHashSet<>();
        for (String categoryCode : categoryCodes) {
            String code = StrUtil.trim(categoryCode);
            if (StrUtil.isBlank(code) || Objects.equals(StyleCategoryEnum.ALL_CODE, code)
                    || Objects.isNull(StyleCategoryEnum.fromCode(code))) {
                log.error("官方风格分类非法: id={}, code={}", asset.getId(), code);
                throw new ServiceException("分类错误");
            }
            distinctCodes.add(code);
        }
        asset.setCategoryCodes(distinctCodes.stream()
                .map(StyleCategoryEnum::fromCode)
                .sorted(Comparator.comparingInt(StyleCategoryEnum::getSortOrder))
                .map(StyleCategoryEnum::getCode)
                .toList());
    }

    private void replaceCategoryRelations(Long assetId, List<String> categoryCodes) {
        sakuraComicAssetCategoryService.remove(Wrappers.<SakuraComicAssetCategory>lambdaQuery()
                .eq(SakuraComicAssetCategory::getAssetId, assetId));
        if (CollectionUtil.isEmpty(categoryCodes)) {
            return;
        }
        List<SakuraComicAssetCategory> relations = categoryCodes.stream().map(code -> {
            SakuraComicAssetCategory relation = new SakuraComicAssetCategory();
            relation.setAssetId(assetId);
            relation.setCategoryCode(code);
            return relation;
        }).toList();
        if (!sakuraComicAssetCategoryService.saveBatch(relations)) {
            log.error("官方风格分类关系保存失败: assetId={}", assetId);
            throw new ServiceException("分类保存失败");
        }
    }

    private StyleCategoryVO toCategoryVO(StyleCategoryEnum category) {
        return new StyleCategoryVO(category.getCode(), category.getLabel());
    }

    private String normalizeHiddenStylePrompt(String json, Long id)
    {
        try
        {
            return HiddenStylePromptJsonUtils.normalize(json);
        }
        catch (IllegalArgumentException e)
        {
            log.error("官方风格隐藏模板格式错误: id={}", id, e);
            throw new ServiceException("隐藏风格格式错误");
        }
    }
}
