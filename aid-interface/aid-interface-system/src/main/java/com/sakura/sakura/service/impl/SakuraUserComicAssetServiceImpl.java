package com.sakura.sakura.service.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import cn.hutool.core.util.StrUtil;
import com.sakura.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.mapper.SakuraUserComicAssetMapper;
import com.sakura.sakura.domain.SakuraUserComicAsset;
import com.sakura.sakura.service.ISakuraUserComicAssetService;
import com.sakura.sakura.util.HiddenStylePromptJsonUtils;
import com.sakura.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户自定义漫画参考资产Service业务层处理
 *
 * @author 视觉AID
 */
@Service
@Slf4j
public class SakuraUserComicAssetServiceImpl extends ServiceImpl<SakuraUserComicAssetMapper, SakuraUserComicAsset> implements ISakuraUserComicAssetService
{
    private static final String ASSET_TYPE_STYLE = "style";

    @Autowired
    private SakuraUserComicAssetMapper sakuraUserComicAssetMapper;

    /**
     * 查询用户自定义漫画参考资产
     *
     * @param id 用户自定义漫画参考资产主键
     * @return 用户自定义漫画参考资产
     */
    @Override
    public SakuraUserComicAsset selectSakuraUserComicAssetById(Long id)
    {
        return this.getById(id);
    }

    /**
     * 查询用户自定义漫画参考资产列表
     *
     * @param sakuraUserComicAsset 用户自定义漫画参考资产
     * @return 用户自定义漫画参考资产
     */
    @Override
    public List<SakuraUserComicAsset> selectSakuraUserComicAssetList(SakuraUserComicAsset sakuraUserComicAsset)
    {
        LambdaQueryWrapper<SakuraUserComicAsset> wrapper = Wrappers.lambdaQuery();
        // 查询字段精简：后台列表不加载隐藏长模板，详情接口按主键读取完整记录。
        wrapper.select(SakuraUserComicAsset::getId, SakuraUserComicAsset::getUserId,
                SakuraUserComicAsset::getAssetType, SakuraUserComicAsset::getAssetName,
                SakuraUserComicAsset::getPersonalityDesc, SakuraUserComicAsset::getPromptText,
                SakuraUserComicAsset::getImageUrl, SakuraUserComicAsset::getSourceType,
                SakuraUserComicAsset::getSortOrder, SakuraUserComicAsset::getStatus,
                SakuraUserComicAsset::getDelFlag, SakuraUserComicAsset::getCreateTime,
                SakuraUserComicAsset::getCreateBy, SakuraUserComicAsset::getUpdateTime,
                SakuraUserComicAsset::getUpdateBy, SakuraUserComicAsset::getRemark);
        if (sakuraUserComicAsset != null)
        {
            if (sakuraUserComicAsset.getUserId() != null)
            {
                wrapper.eq(SakuraUserComicAsset::getUserId, sakuraUserComicAsset.getUserId());
            }
            if (StrUtil.isNotBlank(sakuraUserComicAsset.getAssetType()))
            {
                wrapper.eq(SakuraUserComicAsset::getAssetType, sakuraUserComicAsset.getAssetType());
            }
            if (StrUtil.isNotBlank(sakuraUserComicAsset.getAssetName()))
            {
                wrapper.like(SakuraUserComicAsset::getAssetName, sakuraUserComicAsset.getAssetName());
            }
            if (StrUtil.isNotBlank(sakuraUserComicAsset.getSourceType()))
            {
                wrapper.eq(SakuraUserComicAsset::getSourceType, sakuraUserComicAsset.getSourceType());
            }
            if (StrUtil.isNotBlank(sakuraUserComicAsset.getStatus()))
            {
                wrapper.eq(SakuraUserComicAsset::getStatus, sakuraUserComicAsset.getStatus());
            }
        }
        wrapper.orderByAsc(SakuraUserComicAsset::getSortOrder).orderByDesc(SakuraUserComicAsset::getId);
        return this.list(wrapper);
    }

    /**
     * 新增用户自定义漫画参考资产
     *
     * @param sakuraUserComicAsset 用户自定义漫画参考资产
     * @return 结果
     */
    @Override
    public int insertSakuraUserComicAsset(SakuraUserComicAsset sakuraUserComicAsset)
    {
        if (Objects.equals(ASSET_TYPE_STYLE, sakuraUserComicAsset.getAssetType()))
        {
            validateStylePrompt(sakuraUserComicAsset.getPromptText(), sakuraUserComicAsset.getId());
            sakuraUserComicAsset.setHiddenStylePromptJson(synchronizeCharacterPrompt(
                    sakuraUserComicAsset.getHiddenStylePromptJson(), sakuraUserComicAsset.getPromptText(),
                    sakuraUserComicAsset.getId()));
        }
        else
        {
            sakuraUserComicAsset.setHiddenStylePromptJson(normalizeHiddenStylePrompt(
                    sakuraUserComicAsset.getHiddenStylePromptJson(), sakuraUserComicAsset.getId()));
        }
        sakuraUserComicAsset.setCreateTime(DateUtils.getNowDate());
        return this.save(sakuraUserComicAsset) ? 1 : 0;
    }

    /**
     * 修改用户自定义漫画参考资产
     *
     * @param sakuraUserComicAsset 用户自定义漫画参考资产
     * @return 结果
     */
    @Override
    public int updateSakuraUserComicAsset(SakuraUserComicAsset sakuraUserComicAsset)
    {
        // 查询字段精简：仅用于判断更新后的类型并同步自定义风格角色模板。
        SakuraUserComicAsset stored = this.getOne(Wrappers.<SakuraUserComicAsset>lambdaQuery()
                .select(SakuraUserComicAsset::getId, SakuraUserComicAsset::getAssetType,
                        SakuraUserComicAsset::getPromptText, SakuraUserComicAsset::getHiddenStylePromptJson)
                .eq(SakuraUserComicAsset::getId, sakuraUserComicAsset.getId())
                .last("LIMIT 1"), false);
        String effectiveType = StrUtil.isNotBlank(sakuraUserComicAsset.getAssetType())
                ? sakuraUserComicAsset.getAssetType() : Objects.nonNull(stored) ? stored.getAssetType() : null;
        if (Objects.equals(ASSET_TYPE_STYLE, effectiveType))
        {
            String effectivePrompt = Objects.nonNull(sakuraUserComicAsset.getPromptText())
                    ? sakuraUserComicAsset.getPromptText() : Objects.nonNull(stored) ? stored.getPromptText() : null;
            validateStylePrompt(effectivePrompt, sakuraUserComicAsset.getId());
            if (Objects.nonNull(sakuraUserComicAsset.getPromptText())
                    || Objects.nonNull(sakuraUserComicAsset.getHiddenStylePromptJson()))
            {
                String sourceJson = Objects.nonNull(sakuraUserComicAsset.getHiddenStylePromptJson())
                        ? sakuraUserComicAsset.getHiddenStylePromptJson()
                        : Objects.nonNull(stored) ? stored.getHiddenStylePromptJson() : null;
                sakuraUserComicAsset.setHiddenStylePromptJson(synchronizeCharacterPrompt(
                        sourceJson, effectivePrompt, sakuraUserComicAsset.getId()));
            }
        }
        else
        {
            sakuraUserComicAsset.setHiddenStylePromptJson(normalizeHiddenStylePrompt(
                    sakuraUserComicAsset.getHiddenStylePromptJson(), sakuraUserComicAsset.getId()));
        }
        sakuraUserComicAsset.setUpdateTime(DateUtils.getNowDate());
        return this.updateById(sakuraUserComicAsset) ? 1 : 0;
    }

    /**
     * 批量删除用户自定义漫画参考资产
     *
     * @param ids 需要删除的用户自定义漫画参考资产主键
     * @return 结果
     */
    @Override
    public int deleteSakuraUserComicAssetByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        return this.removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    /**
     * 删除用户自定义漫画参考资产信息
     *
     * @param id 用户自定义漫画参考资产主键
     * @return 结果
     */
    @Override
    public int deleteSakuraUserComicAssetById(Long id)
    {
        if (id == null)
        {
            return 0;
        }
        return this.removeById(id) ? 1 : 0;
    }

    private String normalizeHiddenStylePrompt(String json, Long id)
    {
        try
        {
            return HiddenStylePromptJsonUtils.normalize(json);
        }
        catch (IllegalArgumentException e)
        {
            log.error("用户风格隐藏模板格式错误: id={}", id, e);
            throw new ServiceException("隐藏风格格式错误");
        }
    }

    private String synchronizeCharacterPrompt(String json, String promptText, Long id)
    {
        try
        {
            return HiddenStylePromptJsonUtils.withCharacterPrompt(json, promptText);
        }
        catch (IllegalArgumentException e)
        {
            log.error("用户风格隐藏模板格式错误: id={}", id, e);
            throw new ServiceException("隐藏风格格式错误");
        }
    }

    private void validateStylePrompt(String promptText, Long id)
    {
        if (StrUtil.isBlank(promptText))
        {
            log.info("用户风格提示词为空: id={}", id);
            throw new ServiceException("提示词不能为空");
        }
    }
}
