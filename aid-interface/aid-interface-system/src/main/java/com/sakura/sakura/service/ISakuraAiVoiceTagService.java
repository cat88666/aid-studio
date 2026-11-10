package com.sakura.sakura.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraAiVoiceTag;

/**
 * AI音色标签字典 Service 接口
 *
 * @author 视觉AID
 */
public interface ISakuraAiVoiceTagService extends IService<SakuraAiVoiceTag>
{
    /**
     * 查询单条音色标签
     */
    SakuraAiVoiceTag selectSakuraAiVoiceTagById(Long id);

    /**
     * 查询音色标签列表
     */
    List<SakuraAiVoiceTag> selectSakuraAiVoiceTagList(SakuraAiVoiceTag query);

    /**
     * 新增音色标签
     */
    int insertSakuraAiVoiceTag(SakuraAiVoiceTag voiceTag);

    /**
     * 修改音色标签
     */
    int updateSakuraAiVoiceTag(SakuraAiVoiceTag voiceTag);

    /**
     * 批量软删除音色标签
     */
    int deleteSakuraAiVoiceTagByIds(Long[] ids);

    /**
     * 单条软删除音色标签
     */
    int deleteSakuraAiVoiceTagById(Long id);
}
