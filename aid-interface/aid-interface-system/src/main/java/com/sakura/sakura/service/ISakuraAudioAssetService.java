package com.sakura.sakura.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraAudioAsset;

/**
 * 音频资产 Service 接口
 *
 * @author 视觉AID
 */
public interface ISakuraAudioAssetService extends IService<SakuraAudioAsset>
{
    /**
     * 按主键查询音频资产
     *
     * @param id 主键
     * @return 音频资产；不存在返回 null
     */
    SakuraAudioAsset selectSakuraAudioAssetById(Long id);

    /**
     * 按条件查询音频资产列表（后台管理用）
     *
     * @param sakuraAudioAsset 查询条件（等值匹配）
     * @return 资产列表
     */
    List<SakuraAudioAsset> selectSakuraAudioAssetList(SakuraAudioAsset sakuraAudioAsset);

    /**
     * 新增音频资产
     *
     * @param sakuraAudioAsset 资产
     * @return 影响行数
     */
    int insertSakuraAudioAsset(SakuraAudioAsset sakuraAudioAsset);

    /**
     * 更新音频资产
     *
     * @param sakuraAudioAsset 资产
     * @return 影响行数
     */
    int updateSakuraAudioAsset(SakuraAudioAsset sakuraAudioAsset);

    /**
     * 批量软删除音频资产
     *
     * @param ids 主键集合
     * @return 影响行数
     */
    int deleteSakuraAudioAssetByIds(Long[] ids);

    /**
     * 按主键软删除音频资产
     *
     * @param id 主键
     * @return 影响行数
     */
    int deleteSakuraAudioAssetById(Long id);

    /**
     * 按 sakura_audio_record.id 查询资产（唯一键）
     * 事件监听器在 OSS 就绪后会调用本接口做幂等判断：有则跳过写入，无则插入。
     *
     * @param audioRecordId 配音业务记录ID
     * @return 资产；不存在返回 null
     */
    SakuraAudioAsset selectByAudioRecordId(Long audioRecordId);
}
