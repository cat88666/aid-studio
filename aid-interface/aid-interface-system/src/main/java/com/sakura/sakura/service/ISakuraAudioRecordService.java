package com.sakura.sakura.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraAudioRecord;

/**
 * 分镜配音业务记录 Service 接口
 *
 * @author 视觉AID
 */
public interface ISakuraAudioRecordService extends IService<SakuraAudioRecord>
{
    /**
     * 查询配音业务记录
     *
     * @param id 主键
     * @return 配音业务记录
     */
    SakuraAudioRecord selectSakuraAudioRecordById(Long id);

    /**
     * 查询配音业务记录列表
     *
     * @param sakuraAudioRecord 查询条件
     * @return 配音业务记录集合
     */
    List<SakuraAudioRecord> selectSakuraAudioRecordList(SakuraAudioRecord sakuraAudioRecord);

    /**
     * 新增配音业务记录
     *
     * @param sakuraAudioRecord 配音业务记录
     * @return 影响行数
     */
    int insertSakuraAudioRecord(SakuraAudioRecord sakuraAudioRecord);

    /**
     * 修改配音业务记录
     *
     * @param sakuraAudioRecord 配音业务记录
     * @return 影响行数
     */
    int updateSakuraAudioRecord(SakuraAudioRecord sakuraAudioRecord);

    /**
     * 批量删除配音业务记录
     *
     * @param ids 主键集合
     * @return 影响行数
     */
    int deleteSakuraAudioRecordByIds(Long[] ids);

    /**
     * 删除配音业务记录
     *
     * @param id 主键
     * @return 影响行数
     */
    int deleteSakuraAudioRecordById(Long id);
}
