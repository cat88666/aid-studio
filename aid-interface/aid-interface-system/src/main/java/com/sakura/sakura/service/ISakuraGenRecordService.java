package com.sakura.sakura.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraGenRecord;

/**
 * AI生图/生视频抽卡记录Service接口
 *
 * @author 视觉AID
 */
public interface ISakuraGenRecordService extends IService<SakuraGenRecord>
{
    /**
     * 查询AI生图/生视频抽卡记录
     *
     * @param id AI生图/生视频抽卡记录主键
     * @return AI生图/生视频抽卡记录
     */
    public SakuraGenRecord selectSakuraGenRecordById(Long id);

    /**
     * 查询AI生图/生视频抽卡记录列表
     *
     * @param sakuraGenRecord AI生图/生视频抽卡记录
     * @return AI生图/生视频抽卡记录集合
     */
    public List<SakuraGenRecord> selectSakuraGenRecordList(SakuraGenRecord sakuraGenRecord);

    /**
     * 新增AI生图/生视频抽卡记录
     *
     * @param sakuraGenRecord AI生图/生视频抽卡记录
     * @return 结果
     */
    public int insertSakuraGenRecord(SakuraGenRecord sakuraGenRecord);

    /**
     * 修改AI生图/生视频抽卡记录
     *
     * @param sakuraGenRecord AI生图/生视频抽卡记录
     * @return 结果
     */
    public int updateSakuraGenRecord(SakuraGenRecord sakuraGenRecord);

    /**
     * 批量删除AI生图/生视频抽卡记录
     *
     * @param ids 需要删除的AI生图/生视频抽卡记录主键集合
     * @return 结果
     */
    public int deleteSakuraGenRecordByIds(Long[] ids);

    /**
     * 删除AI生图/生视频抽卡记录信息
     *
     * @param id AI生图/生视频抽卡记录主键
     * @return 结果
     */
    public int deleteSakuraGenRecordById(Long id);
}
