package com.sakura.voice.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sakura.sakura.domain.SakuraAiVoiceTag;
import com.sakura.sakura.service.ISakuraAiVoiceTagService;
import com.sakura.common.exception.ServiceException;
import com.sakura.common.utils.SecurityUtils;
import com.sakura.voice.constant.VoiceLibraryConstants;
import com.sakura.voice.dto.VoiceTagListRequest;
import com.sakura.voice.dto.VoiceTagUpsertRequest;
import com.sakura.voice.service.IVoiceTagBusinessService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 音色标签字典业务实现
 *
 * @author 视觉AID
 */
@Slf4j
@Service
public class VoiceTagBusinessServiceImpl implements IVoiceTagBusinessService
{
    /** 启用状态 */
    private static final String STATUS_ENABLED = "0";

    /** 停用状态 */
    private static final String STATUS_DISABLED = "1";

    /** 未删除 */
    private static final String DEL_FLAG_NORMAL = "0";

    /** 已删除 */
    private static final String DEL_FLAG_DELETED = "2";

    @Resource
    private ISakuraAiVoiceTagService sakuraAiVoiceTagService;

    @Override
    public List<SakuraAiVoiceTag> listVoiceTags(VoiceTagListRequest request)
    {
        if (Objects.isNull(request))
        {
            request = new VoiceTagListRequest();
        }
        SakuraAiVoiceTag query = new SakuraAiVoiceTag();
        query.setTagType(request.getTagType());
        query.setTagCode(request.getTagCode());
        query.setTagName(request.getTagName());
        query.setStatus(request.getStatus());
        query.setDelFlag(request.getDelFlag());
        return sakuraAiVoiceTagService.selectSakuraAiVoiceTagList(query);
    }

    @Override
    public SakuraAiVoiceTag getVoiceTagDetail(Long id)
    {
        if (Objects.isNull(id))
        {
            log.info("getVoiceTagDetail id 为空");
            throw new ServiceException("参数错误");
        }
        SakuraAiVoiceTag tag = sakuraAiVoiceTagService.selectSakuraAiVoiceTagById(id);
        if (Objects.isNull(tag))
        {
            log.info("getVoiceTagDetail 未命中, id={}", id);
            throw new ServiceException("数据不存在");
        }
        return tag;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createVoiceTag(VoiceTagUpsertRequest request)
    {
        validateUpsert(request, false);

        // 唯一键校验：同 tag_type + tag_code 的未删除记录不能已存在
        ensureTagCodeNotConflict(request.getTagType(), request.getTagCode(), null);

        SakuraAiVoiceTag tag = new SakuraAiVoiceTag();
        tag.setTagType(request.getTagType());
        tag.setTagCode(request.getTagCode());
        tag.setTagName(request.getTagName());
        tag.setSortOrder(Objects.isNull(request.getSortOrder()) ? 0 : request.getSortOrder());
        tag.setStatus(StrUtil.isBlank(request.getStatus()) ? STATUS_ENABLED : request.getStatus());
        tag.setDelFlag(DEL_FLAG_NORMAL);
        tag.setRemark(request.getRemark());
        // 创建审计
        tag.setCreateBy(SecurityUtils.getUsername());
        tag.setCreateTime(new Date());
        tag.setUpdateBy(SecurityUtils.getUsername());
        tag.setUpdateTime(new Date());

        int rows = sakuraAiVoiceTagService.insertSakuraAiVoiceTag(tag);
        if (rows <= 0)
        {
            log.warn("createVoiceTag 数据库保存失败");
            throw new ServiceException("保存失败，请重试");
        }
        return tag.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateVoiceTag(VoiceTagUpsertRequest request)
    {
        if (Objects.isNull(request) || Objects.isNull(request.getId()))
        {
            log.info("updateVoiceTag 缺少 id");
            throw new ServiceException("参数错误");
        }
        validateUpsert(request, true);

        SakuraAiVoiceTag existing = sakuraAiVoiceTagService.selectSakuraAiVoiceTagById(request.getId());
        if (Objects.isNull(existing) || Objects.equals("2", existing.getDelFlag()))
        {
            log.info("updateVoiceTag 未命中, id={}", request.getId());
            throw new ServiceException("数据不存在");
        }
        // 唯一键冲突检查（排除自身）
        ensureTagCodeNotConflict(request.getTagType(), request.getTagCode(), request.getId());

        SakuraAiVoiceTag tag = new SakuraAiVoiceTag();
        tag.setId(request.getId());
        tag.setTagType(request.getTagType());
        tag.setTagCode(request.getTagCode());
        tag.setTagName(request.getTagName());
        tag.setSortOrder(request.getSortOrder());
        if (StrUtil.isNotBlank(request.getStatus()))
        {
            tag.setStatus(request.getStatus());
        }
        tag.setRemark(request.getRemark());
        tag.setUpdateBy(SecurityUtils.getUsername());
        tag.setUpdateTime(new Date());

        int rows = sakuraAiVoiceTagService.updateSakuraAiVoiceTag(tag);
        if (rows <= 0)
        {
            log.warn("updateVoiceTag 数据库保存失败, id={}", request.getId());
            throw new ServiceException("保存失败，请重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteVoiceTags(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            log.info("deleteVoiceTags ids 为空");
            throw new ServiceException("参数错误");
        }
        if (ids.length > VoiceLibraryConstants.DELETE_BATCH_MAX)
        {
            log.info("deleteVoiceTags 超过批量上限, count={}", ids.length);
            throw new ServiceException("数量超出限制");
        }
        for (Long id : ids)
        {
            if (Objects.isNull(id) || id <= 0)
            {
                log.info("deleteVoiceTags 非法 id={}", id);
                throw new ServiceException("参数错误");
            }
        }
        // 批次做软删除，仍走 update 审计
        for (Long id : ids)
        {
            SakuraAiVoiceTag entity = new SakuraAiVoiceTag();
            entity.setId(id);
            entity.setDelFlag(DEL_FLAG_DELETED);
            entity.setUpdateBy(SecurityUtils.getUsername());
            entity.setUpdateTime(new Date());
            sakuraAiVoiceTagService.updateById(entity);
        }
    }

    @Override
    public List<SakuraAiVoiceTag> listActiveTagsByType(String tagType)
    {
        if (StrUtil.isBlank(tagType) || !VoiceLibraryConstants.TAG_TYPES.contains(tagType))
        {
            return new ArrayList<>();
        }
        // 只取必要字段：新增字段需同步 .select(...)
        LambdaQueryWrapper<SakuraAiVoiceTag> wrapper = Wrappers.lambdaQuery();
        wrapper.select(SakuraAiVoiceTag::getId, SakuraAiVoiceTag::getTagType, SakuraAiVoiceTag::getTagCode,
                SakuraAiVoiceTag::getTagName, SakuraAiVoiceTag::getSortOrder,
                SakuraAiVoiceTag::getStatus, SakuraAiVoiceTag::getDelFlag);
        wrapper.eq(SakuraAiVoiceTag::getTagType, tagType);
        wrapper.eq(SakuraAiVoiceTag::getStatus, STATUS_ENABLED);
        wrapper.eq(SakuraAiVoiceTag::getDelFlag, DEL_FLAG_NORMAL);
        wrapper.orderByDesc(SakuraAiVoiceTag::getSortOrder).orderByDesc(SakuraAiVoiceTag::getId);
        return sakuraAiVoiceTagService.list(wrapper);
    }

    @Override
    public List<String> findMissingTagCodes(String tagType, List<String> tagCodes)
    {
        if (CollectionUtil.isEmpty(tagCodes))
        {
            return new ArrayList<>();
        }
        List<SakuraAiVoiceTag> actives = listActiveTagsByType(tagType);
        Set<String> activeCodes = new HashSet<>();
        for (SakuraAiVoiceTag tag : actives)
        {
            activeCodes.add(tag.getTagCode());
        }
        List<String> missing = new ArrayList<>();
        for (String code : tagCodes)
        {
            if (!activeCodes.contains(code))
            {
                missing.add(code);
            }
        }
        return missing;
    }
    private void validateUpsert(VoiceTagUpsertRequest request, boolean isUpdate)
    {
        if (Objects.isNull(request))
        {
            log.info("validateUpsert request 为空");
            throw new ServiceException("参数错误");
        }
        if (StrUtil.isBlank(request.getTagType()))
        {
            log.info("validateUpsert tagType 为空");
            throw new ServiceException("类型不支持");
        }
        if (!VoiceLibraryConstants.TAG_TYPES.contains(request.getTagType()))
        {
            log.info("validateUpsert tagType 非法, tagType={}", request.getTagType());
            throw new ServiceException("类型不支持");
        }
        if (StrUtil.isBlank(request.getTagCode()))
        {
            log.info("validateUpsert tagCode 为空");
            throw new ServiceException("编码格式有误");
        }
        if (StrUtil.isBlank(request.getTagName()))
        {
            log.info("validateUpsert tagName 为空");
            throw new ServiceException("名称格式有误");
        }
    }

    /**
     * 校验 (tag_type, tag_code) 唯一键在未删除记录中不冲突。
     * 仅取必要字段：id / tag_type / tag_code / del_flag。
     *
     * @param tagType  标签类型
     * @param tagCode  标签编码
     * @param selfId   更新时的自身 id（新增传 null）
     */
    private void ensureTagCodeNotConflict(String tagType, String tagCode, Long selfId)
    {
        // 校验专用查询：只取 id、tag_type、tag_code、del_flag；新增返回字段需同步更新
        LambdaQueryWrapper<SakuraAiVoiceTag> wrapper = Wrappers.lambdaQuery();
        wrapper.select(SakuraAiVoiceTag::getId, SakuraAiVoiceTag::getTagType,
                SakuraAiVoiceTag::getTagCode, SakuraAiVoiceTag::getDelFlag);
        wrapper.eq(SakuraAiVoiceTag::getTagType, tagType);
        wrapper.eq(SakuraAiVoiceTag::getTagCode, tagCode);
        wrapper.eq(SakuraAiVoiceTag::getDelFlag, DEL_FLAG_NORMAL);
        wrapper.last("LIMIT 1");
        SakuraAiVoiceTag conflict = sakuraAiVoiceTagService.getOne(wrapper, false);
        if (Objects.nonNull(conflict) && !Objects.equals(conflict.getId(), selfId))
        {
            log.info("ensureTagCodeNotConflict 冲突, tagType={}, tagCode={}, conflictId={}",
                    tagType, tagCode, conflict.getId());
            throw new ServiceException("编码已存在");
        }
    }
}
