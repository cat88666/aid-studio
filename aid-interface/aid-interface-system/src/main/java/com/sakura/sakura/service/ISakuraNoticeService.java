package com.sakura.sakura.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraNotice;

/**
 * C 端公告 Service 接口（后台管理维护用）
 *
 * @author 视觉AID
 */
public interface ISakuraNoticeService extends IService<SakuraNotice>
{
    /**
     * 查询公告
     *
     * @param id 公告主键
     * @return 公告
     */
    SakuraNotice selectSakuraNoticeById(Long id);

    /**
     * 查询公告列表
     *
     * @param sakuraNotice 公告（标题/类型/状态作为检索条件）
     * @return 公告集合
     */
    List<SakuraNotice> selectSakuraNoticeList(SakuraNotice sakuraNotice);

    /**
     * 新增公告
     *
     * @param sakuraNotice 公告
     * @return 结果
     */
    int insertSakuraNotice(SakuraNotice sakuraNotice);

    /**
     * 修改公告
     *
     * @param sakuraNotice 公告
     * @return 结果
     */
    int updateSakuraNotice(SakuraNotice sakuraNotice);

    /**
     * 批量删除公告
     *
     * @param ids 需要删除的公告主键集合
     * @return 结果
     */
    int deleteSakuraNoticeByIds(Long[] ids);

    /**
     * 删除公告信息
     *
     * @param id 公告主键
     * @return 结果
     */
    int deleteSakuraNoticeById(Long id);
}
