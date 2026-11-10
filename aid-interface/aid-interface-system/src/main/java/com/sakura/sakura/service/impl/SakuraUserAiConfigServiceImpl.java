package com.sakura.sakura.service.impl;

import java.util.Arrays;
import java.util.List;
import cn.hutool.core.util.StrUtil;
import com.sakura.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.mapper.SakuraUserAiConfigMapper;
import com.sakura.sakura.domain.SakuraUserAiConfig;
import com.sakura.sakura.service.ISakuraUserAiConfigService;

/**
 * 用户自定义AI大模型配置(配置覆盖用)Service业务层处理
 *
 * @author 视觉AID
 */
@Service
public class SakuraUserAiConfigServiceImpl extends ServiceImpl<SakuraUserAiConfigMapper, SakuraUserAiConfig> implements ISakuraUserAiConfigService
{
    @Autowired
    private SakuraUserAiConfigMapper sakuraUserAiConfigMapper;

    /**
     * 查询用户自定义AI大模型配置(配置覆盖用)
     *
     * @param id 用户自定义AI大模型配置(配置覆盖用)主键
     * @return 用户自定义AI大模型配置(配置覆盖用)
     */
    @Override
    public SakuraUserAiConfig selectSakuraUserAiConfigById(Long id)
    {
        return this.getById(id);
    }

    /**
     * 查询用户自定义AI大模型配置(配置覆盖用)列表
     *
     * @param sakuraUserAiConfig 用户自定义AI大模型配置(配置覆盖用)
     * @return 用户自定义AI大模型配置(配置覆盖用)
     */
    @Override
    public List<SakuraUserAiConfig> selectSakuraUserAiConfigList(SakuraUserAiConfig sakuraUserAiConfig)
    {
        LambdaQueryWrapper<SakuraUserAiConfig> wrapper = Wrappers.lambdaQuery();
        if (sakuraUserAiConfig != null)
        {
            if (sakuraUserAiConfig.getUserId() != null)
            {
                wrapper.eq(SakuraUserAiConfig::getUserId, sakuraUserAiConfig.getUserId());
            }
            if (sakuraUserAiConfig.getProviderId() != null)
            {
                wrapper.eq(SakuraUserAiConfig::getProviderId, sakuraUserAiConfig.getProviderId());
            }
            if (StrUtil.isNotBlank(sakuraUserAiConfig.getIsEnable()))
            {
                wrapper.eq(SakuraUserAiConfig::getIsEnable, sakuraUserAiConfig.getIsEnable());
            }
        }
        wrapper.orderByDesc(SakuraUserAiConfig::getId);
        return this.list(wrapper);
    }

    /**
     * 新增用户自定义AI大模型配置(配置覆盖用)
     *
     * @param sakuraUserAiConfig 用户自定义AI大模型配置(配置覆盖用)
     * @return 结果
     */
    @Override
    public int insertSakuraUserAiConfig(SakuraUserAiConfig sakuraUserAiConfig)
    {
        sakuraUserAiConfig.setCreateTime(DateUtils.getNowDate());
        return this.save(sakuraUserAiConfig) ? 1 : 0;
    }

    /**
     * 修改用户自定义AI大模型配置(配置覆盖用)
     *
     * @param sakuraUserAiConfig 用户自定义AI大模型配置(配置覆盖用)
     * @return 结果
     */
    @Override
    public int updateSakuraUserAiConfig(SakuraUserAiConfig sakuraUserAiConfig)
    {
        sakuraUserAiConfig.setUpdateTime(DateUtils.getNowDate());
        return this.updateById(sakuraUserAiConfig) ? 1 : 0;
    }

    /**
     * 批量删除用户自定义AI大模型配置(配置覆盖用)
     *
     * @param ids 需要删除的用户自定义AI大模型配置(配置覆盖用)主键
     * @return 结果
     */
    @Override
    public int deleteSakuraUserAiConfigByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        return this.removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    /**
     * 删除用户自定义AI大模型配置(配置覆盖用)信息
     *
     * @param id 用户自定义AI大模型配置(配置覆盖用)主键
     * @return 结果
     */
    @Override
    public int deleteSakuraUserAiConfigById(Long id)
    {
        if (id == null)
        {
            return 0;
        }
        return this.removeById(id) ? 1 : 0;
    }
}
