package com.sakura.sakura.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraUserAiConfig;

/**
 * 用户自定义AI大模型配置(配置覆盖用)Service接口
 *
 * @author 视觉AID
 */
public interface ISakuraUserAiConfigService extends IService<SakuraUserAiConfig>
{
    /**
     * 查询用户自定义AI大模型配置(配置覆盖用)
     *
     * @param id 用户自定义AI大模型配置(配置覆盖用)主键
     * @return 用户自定义AI大模型配置(配置覆盖用)
     */
    public SakuraUserAiConfig selectSakuraUserAiConfigById(Long id);

    /**
     * 查询用户自定义AI大模型配置(配置覆盖用)列表
     *
     * @param sakuraUserAiConfig 用户自定义AI大模型配置(配置覆盖用)
     * @return 用户自定义AI大模型配置(配置覆盖用)集合
     */
    public List<SakuraUserAiConfig> selectSakuraUserAiConfigList(SakuraUserAiConfig sakuraUserAiConfig);

    /**
     * 新增用户自定义AI大模型配置(配置覆盖用)
     *
     * @param sakuraUserAiConfig 用户自定义AI大模型配置(配置覆盖用)
     * @return 结果
     */
    public int insertSakuraUserAiConfig(SakuraUserAiConfig sakuraUserAiConfig);

    /**
     * 修改用户自定义AI大模型配置(配置覆盖用)
     *
     * @param sakuraUserAiConfig 用户自定义AI大模型配置(配置覆盖用)
     * @return 结果
     */
    public int updateSakuraUserAiConfig(SakuraUserAiConfig sakuraUserAiConfig);

    /**
     * 批量删除用户自定义AI大模型配置(配置覆盖用)
     *
     * @param ids 需要删除的用户自定义AI大模型配置(配置覆盖用)主键集合
     * @return 结果
     */
    public int deleteSakuraUserAiConfigByIds(Long[] ids);

    /**
     * 删除用户自定义AI大模型配置(配置覆盖用)信息
     *
     * @param id 用户自定义AI大模型配置(配置覆盖用)主键
     * @return 结果
     */
    public int deleteSakuraUserAiConfigById(Long id);
}
