package com.sakura.sakura.service.impl;

import java.util.List;
import cn.hutool.core.util.StrUtil;
import com.sakura.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.mapper.SakuraAiModelFuncConfigMapper;
import com.sakura.sakura.domain.SakuraAiModelFuncConfig;
import com.sakura.sakura.service.ISakuraAiModelFuncConfigService;

/**
 * AI模型功能配置Service业务层处理
 *
 * @author 视觉AID
 */
@Service
public class SakuraAiModelFuncConfigServiceImpl extends ServiceImpl<SakuraAiModelFuncConfigMapper, SakuraAiModelFuncConfig> implements ISakuraAiModelFuncConfigService
{
    private static final String DEL_FLAG_NORMAL = "0";
    private static final String DEL_FLAG_DELETED = "2";
    private static final String STATUS_DISABLED = "1";

    @Autowired
    private SakuraAiModelFuncConfigMapper sakuraAiModelFuncConfigMapper;

    /**
     * 查询AI模型功能配置
     *
     * @param id AI模型功能配置主键
     * @return AI模型功能配置
     */
    @Override
    public SakuraAiModelFuncConfig selectSakuraAiModelFuncConfigById(Long id)
    {
        return this.getOne(Wrappers.<SakuraAiModelFuncConfig>lambdaQuery()
                .eq(SakuraAiModelFuncConfig::getId, id)
                .eq(SakuraAiModelFuncConfig::getDelFlag, DEL_FLAG_NORMAL)
                .last("limit 1"), false);
    }

    /**
     * 查询AI模型功能配置列表
     *
     * @param sakuraAiModelFuncConfig AI模型功能配置
     * @return AI模型功能配置
     */
    @Override
    public List<SakuraAiModelFuncConfig> selectSakuraAiModelFuncConfigList(SakuraAiModelFuncConfig sakuraAiModelFuncConfig)
    {
        LambdaQueryWrapper<SakuraAiModelFuncConfig> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SakuraAiModelFuncConfig::getDelFlag, DEL_FLAG_NORMAL);
        if (sakuraAiModelFuncConfig != null)
        {
            if (StrUtil.isNotBlank(sakuraAiModelFuncConfig.getFuncName()))
            {
                wrapper.like(SakuraAiModelFuncConfig::getFuncName, sakuraAiModelFuncConfig.getFuncName());
            }
            if (StrUtil.isNotBlank(sakuraAiModelFuncConfig.getFuncCode()))
            {
                wrapper.like(SakuraAiModelFuncConfig::getFuncCode, sakuraAiModelFuncConfig.getFuncCode());
            }
            if (StrUtil.isNotBlank(sakuraAiModelFuncConfig.getModelType()))
            {
                wrapper.eq(SakuraAiModelFuncConfig::getModelType, sakuraAiModelFuncConfig.getModelType());
            }
            if (StrUtil.isNotBlank(sakuraAiModelFuncConfig.getGenerateMode()))
            {
                wrapper.eq(SakuraAiModelFuncConfig::getGenerateMode, sakuraAiModelFuncConfig.getGenerateMode());
            }
            if (StrUtil.isNotBlank(sakuraAiModelFuncConfig.getStatus()))
            {
                wrapper.eq(SakuraAiModelFuncConfig::getStatus, sakuraAiModelFuncConfig.getStatus());
            }
        }
        wrapper.orderByDesc(SakuraAiModelFuncConfig::getId);
        return this.list(wrapper);
    }

    /**
     * 新增AI模型功能配置
     *
     * @param sakuraAiModelFuncConfig AI模型功能配置
     * @return 结果
     */
    @Override
    public int insertSakuraAiModelFuncConfig(SakuraAiModelFuncConfig sakuraAiModelFuncConfig)
    {
        if (StrUtil.isBlank(sakuraAiModelFuncConfig.getStatus()))
        {
            sakuraAiModelFuncConfig.setStatus("0");
        }
        sakuraAiModelFuncConfig.setDelFlag(DEL_FLAG_NORMAL);
        sakuraAiModelFuncConfig.setCreateTime(DateUtils.getNowDate());
        return this.save(sakuraAiModelFuncConfig) ? 1 : 0;
    }

    /**
     * 修改AI模型功能配置
     *
     * @param sakuraAiModelFuncConfig AI模型功能配置
     * @return 结果
     */
    @Override
    public int updateSakuraAiModelFuncConfig(SakuraAiModelFuncConfig sakuraAiModelFuncConfig)
    {
        sakuraAiModelFuncConfig.setUpdateTime(DateUtils.getNowDate());
        return this.updateById(sakuraAiModelFuncConfig) ? 1 : 0;
    }

    /**
     * 批量删除AI模型功能配置
     *
     * @param ids 需要删除的AI模型功能配置主键
     * @return 结果
     */
    @Override
    public int deleteSakuraAiModelFuncConfigByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        return this.update(Wrappers.<SakuraAiModelFuncConfig>lambdaUpdate()
                .in(SakuraAiModelFuncConfig::getId, List.of(ids))
                .eq(SakuraAiModelFuncConfig::getDelFlag, DEL_FLAG_NORMAL)
                .set(SakuraAiModelFuncConfig::getStatus, STATUS_DISABLED)
                .set(SakuraAiModelFuncConfig::getDelFlag, DEL_FLAG_DELETED)
                .set(SakuraAiModelFuncConfig::getUpdateTime, DateUtils.getNowDate())) ? 1 : 0;
    }

    /**
     * 删除AI模型功能配置信息
     *
     * @param id AI模型功能配置主键
     * @return 结果
     */
    @Override
    public int deleteSakuraAiModelFuncConfigById(Long id)
    {
        if (id == null)
        {
            return 0;
        }
        return this.update(Wrappers.<SakuraAiModelFuncConfig>lambdaUpdate()
                .eq(SakuraAiModelFuncConfig::getId, id)
                .eq(SakuraAiModelFuncConfig::getDelFlag, DEL_FLAG_NORMAL)
                .set(SakuraAiModelFuncConfig::getStatus, STATUS_DISABLED)
                .set(SakuraAiModelFuncConfig::getDelFlag, DEL_FLAG_DELETED)
                .set(SakuraAiModelFuncConfig::getUpdateTime, DateUtils.getNowDate())) ? 1 : 0;
    }
}
