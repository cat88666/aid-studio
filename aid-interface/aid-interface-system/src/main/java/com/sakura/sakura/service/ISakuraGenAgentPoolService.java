package com.sakura.sakura.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraGenAgentPool;

/**
 * 生成链路智能体可选池Service接口
 *
 * @author 视觉AID
 */
public interface ISakuraGenAgentPoolService extends IService<SakuraGenAgentPool>
{
    /**
     * 查询智能体可选池
     *
     * @param id 主键
     * @return 记录
     */
    SakuraGenAgentPool selectSakuraGenAgentPoolById(Long id);

    /**
     * 查询智能体可选池列表
     *
     * @param query 查询条件
     * @return 集合
     */
    List<SakuraGenAgentPool> selectSakuraGenAgentPoolList(SakuraGenAgentPool query);

    /**
     * 新增智能体可选池
     *
     * @param entity 记录
     * @return 结果
     */
    int insertSakuraGenAgentPool(SakuraGenAgentPool entity);

    /**
     * 修改智能体可选池
     *
     * @param entity 记录
     * @return 结果
     */
    int updateSakuraGenAgentPool(SakuraGenAgentPool entity);

    /**
     * 批量删除智能体可选池
     *
     * @param ids 主键集合
     * @return 结果
     */
    int deleteSakuraGenAgentPoolByIds(Long[] ids);
}
