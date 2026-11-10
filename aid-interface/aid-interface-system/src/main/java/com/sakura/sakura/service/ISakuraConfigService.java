package com.sakura.sakura.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraConfig;

/**
 * 配置信息Service接口
 *
 * @author 视觉AID
 */
public interface ISakuraConfigService extends IService<SakuraConfig>
{
    /**
     * 查询配置信息
     *
     * @param id 配置信息主键
     * @return 配置信息
     */
    public SakuraConfig selectSakuraConfigById(Long id);

    /**
     * 查询配置信息列表
     *
     * @param sakuraConfig 配置信息
     * @return 配置信息集合
     */
    public List<SakuraConfig> selectSakuraConfigList(SakuraConfig sakuraConfig);

    /**
     * 新增配置信息
     *
     * @param sakuraConfig 配置信息
     * @return 结果
     */
    public int insertSakuraConfig(SakuraConfig sakuraConfig);

    /**
     * 修改配置信息
     *
     * @param sakuraConfig 配置信息
     * @return 结果
     */
    public int updateSakuraConfig(SakuraConfig sakuraConfig);

    /**
     * 批量删除配置信息
     *
     * @param ids 需要删除的配置信息主键集合
     * @return 结果
     */
    public int deleteSakuraConfigByIds(Long[] ids);

    /**
     * 删除配置信息信息
     *
     * @param id 配置信息主键
     * @return 结果
     */
    public int deleteSakuraConfigById(Long id);

    /**
     * 根据分类和配置名获取配置值
     *
     * @param category  配置分类
     * @param configKey 配置名
     * @return 配置值，不存在返回null
     */
    String getConfigValue(String category, String configKey);

    /**
     * 按「分类 + 配置名」精确 upsert 配置值：存在则更新，不存在则新增。
     *
     * @param category    配置分类
     * @param configName  配置名
     * @param configValue 配置值
     */
    void upsertConfigValue(String category, String configName, String configValue);
}
