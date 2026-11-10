package com.sakura.sakura.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraAiModelFuncConfig;

/**
 * AI模型功能配置Service接口
 *
 * @author 视觉AID
 */
public interface ISakuraAiModelFuncConfigService extends IService<SakuraAiModelFuncConfig>
{
    /**
     * 查询AI模型功能配置
     *
     * @param id AI模型功能配置主键
     * @return AI模型功能配置
     */
    public SakuraAiModelFuncConfig selectSakuraAiModelFuncConfigById(Long id);

    /**
     * 查询AI模型功能配置列表
     *
     * @param sakuraAiModelFuncConfig AI模型功能配置
     * @return AI模型功能配置集合
     */
    public List<SakuraAiModelFuncConfig> selectSakuraAiModelFuncConfigList(SakuraAiModelFuncConfig sakuraAiModelFuncConfig);

    /**
     * 新增AI模型功能配置
     *
     * @param sakuraAiModelFuncConfig AI模型功能配置
     * @return 结果
     */
    public int insertSakuraAiModelFuncConfig(SakuraAiModelFuncConfig sakuraAiModelFuncConfig);

    /**
     * 修改AI模型功能配置
     *
     * @param sakuraAiModelFuncConfig AI模型功能配置
     * @return 结果
     */
    public int updateSakuraAiModelFuncConfig(SakuraAiModelFuncConfig sakuraAiModelFuncConfig);

    /**
     * 批量删除AI模型功能配置
     *
     * @param ids 需要删除的AI模型功能配置主键集合
     * @return 结果
     */
    public int deleteSakuraAiModelFuncConfigByIds(Long[] ids);

    /**
     * 删除AI模型功能配置信息
     *
     * @param id AI模型功能配置主键
     * @return 结果
     */
    public int deleteSakuraAiModelFuncConfigById(Long id);
}
