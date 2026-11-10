package com.sakura.sakura.service.impl;

import java.util.Arrays;
import java.util.List;
import com.sakura.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.mapper.SakuraComicScriptMapper;
import com.sakura.sakura.domain.SakuraComicScript;
import com.sakura.sakura.service.ISakuraComicScriptService;

/**
 * 剧本原文与简化版Service业务层处理
 *
 * @author 视觉AID
 */
@Service
public class SakuraComicScriptServiceImpl extends ServiceImpl<SakuraComicScriptMapper, SakuraComicScript> implements ISakuraComicScriptService
{
    @Autowired
    private SakuraComicScriptMapper sakuraComicScriptMapper;

    /**
     * 查询剧本原文与简化版
     *
     * @param id 剧本原文与简化版主键
     * @return 剧本原文与简化版
     */
    @Override
    public SakuraComicScript selectSakuraComicScriptById(Long id)
    {
        return this.getById(id);
    }

    /**
     * 查询剧本原文与简化版列表
     *
     * @param sakuraComicScript 剧本原文与简化版
     * @return 剧本原文与简化版
     */
    @Override
    public List<SakuraComicScript> selectSakuraComicScriptList(SakuraComicScript sakuraComicScript)
    {
        LambdaQueryWrapper<SakuraComicScript> wrapper = Wrappers.lambdaQuery();
        if (sakuraComicScript != null)
        {
            if (sakuraComicScript.getProjectId() != null)
            {
                wrapper.eq(SakuraComicScript::getProjectId, sakuraComicScript.getProjectId());
            }
            if (sakuraComicScript.getEpisodeId() != null)
            {
                wrapper.eq(SakuraComicScript::getEpisodeId, sakuraComicScript.getEpisodeId());
            }
            if (sakuraComicScript.getUserId() != null)
            {
                wrapper.eq(SakuraComicScript::getUserId, sakuraComicScript.getUserId());
            }
            if (sakuraComicScript.getIsExtracted() != null)
            {
                wrapper.eq(SakuraComicScript::getIsExtracted, sakuraComicScript.getIsExtracted());
            }
            if (sakuraComicScript.getComicVersion() != null)
            {
                wrapper.eq(SakuraComicScript::getComicVersion, sakuraComicScript.getComicVersion());
            }
            if (sakuraComicScript.getStatus() != null)
            {
                wrapper.eq(SakuraComicScript::getStatus, sakuraComicScript.getStatus());
            }
        }
        wrapper.orderByDesc(SakuraComicScript::getId);
        return this.list(wrapper);
    }

    /**
     * 新增剧本原文与简化版
     *
     * @param sakuraComicScript 剧本原文与简化版
     * @return 结果
     */
    @Override
    public int insertSakuraComicScript(SakuraComicScript sakuraComicScript)
    {
        sakuraComicScript.setCreateTime(DateUtils.getNowDate());
        return this.save(sakuraComicScript) ? 1 : 0;
    }

    /**
     * 修改剧本原文与简化版
     *
     * @param sakuraComicScript 剧本原文与简化版
     * @return 结果
     */
    @Override
    public int updateSakuraComicScript(SakuraComicScript sakuraComicScript)
    {
        sakuraComicScript.setUpdateTime(DateUtils.getNowDate());
        return this.updateById(sakuraComicScript) ? 1 : 0;
    }

    /**
     * 批量删除剧本原文与简化版
     *
     * @param ids 需要删除的剧本原文与简化版主键
     * @return 结果
     */
    @Override
    public int deleteSakuraComicScriptByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        return this.removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    /**
     * 删除剧本原文与简化版信息
     *
     * @param id 剧本原文与简化版主键
     * @return 结果
     */
    @Override
    public int deleteSakuraComicScriptById(Long id)
    {
        if (id == null)
        {
            return 0;
        }
        return this.removeById(id) ? 1 : 0;
    }
}
