package com.sakura.sakura.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraStoryboard;

/**
 * 分镜时间轴主Service接口
 *
 * @author 视觉AID
 */
public interface ISakuraStoryboardService extends IService<SakuraStoryboard>
{
    /**
     * 查询分镜时间轴主
     *
     * @param id 分镜时间轴主主键
     * @return 分镜时间轴主
     */
    public SakuraStoryboard selectSakuraStoryboardById(Long id);

    /**
     * 查询分镜时间轴主列表
     *
     * @param sakuraStoryboard 分镜时间轴主
     * @return 分镜时间轴主集合
     */
    public List<SakuraStoryboard> selectSakuraStoryboardList(SakuraStoryboard sakuraStoryboard);

    /**
     * 新增分镜时间轴主
     *
     * @param sakuraStoryboard 分镜时间轴主
     * @return 结果
     */
    public int insertSakuraStoryboard(SakuraStoryboard sakuraStoryboard);

    /**
     * 修改分镜时间轴主
     *
     * @param sakuraStoryboard 分镜时间轴主
     * @return 结果
     */
    public int updateSakuraStoryboard(SakuraStoryboard sakuraStoryboard);

    /**
     * 批量删除分镜时间轴主
     *
     * @param ids 需要删除的分镜时间轴主主键集合
     * @return 结果
     */
    public int deleteSakuraStoryboardByIds(Long[] ids);

    /**
     * 删除分镜时间轴主信息
     *
     * @param id 分镜时间轴主主键
     * @return 结果
     */
    public int deleteSakuraStoryboardById(Long id);
}
