package com.sakura.sakura.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraExtractTask;

/**
 * 资产提取任务Service接口
 *
 * @author 视觉AID
 */
public interface ISakuraExtractTaskService extends IService<SakuraExtractTask>
{
    /**
     * 查询资产提取任务
     *
     * @param id 资产提取任务主键
     * @return 资产提取任务
     */
    public SakuraExtractTask selectSakuraExtractTaskById(Long id);

    /**
     * 查询资产提取任务列表
     *
     * @param sakuraExtractTask 资产提取任务
     * @return 资产提取任务集合
     */
    public List<SakuraExtractTask> selectSakuraExtractTaskList(SakuraExtractTask sakuraExtractTask);

    /**
     * 新增资产提取任务
     *
     * @param sakuraExtractTask 资产提取任务
     * @return 结果
     */
    public int insertSakuraExtractTask(SakuraExtractTask sakuraExtractTask);

    /**
     * 修改资产提取任务
     *
     * @param sakuraExtractTask 资产提取任务
     * @return 结果
     */
    public int updateSakuraExtractTask(SakuraExtractTask sakuraExtractTask);

    /**
     * 批量删除资产提取任务
     *
     * @param ids 需要删除的资产提取任务主键集合
     * @return 结果
     */
    public int deleteSakuraExtractTaskByIds(Long[] ids);

    /**
     * 删除资产提取任务信息
     *
     * @param id 资产提取任务主键
     * @return 结果
     */
    public int deleteSakuraExtractTaskById(Long id);
}
