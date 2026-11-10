package com.sakura.sakura.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraAiVoiceLibrary;

/**
 * AI音色库 Service 接口（基础 CRUD）
 *
 * @author 视觉AID
 */
public interface ISakuraAiVoiceLibraryService extends IService<SakuraAiVoiceLibrary>
{
    /**
     * 查询单条音色
     */
    SakuraAiVoiceLibrary selectSakuraAiVoiceLibraryById(Long id);

    /**
     * 查询音色列表（后台管理用基础查询，由业务层拼装过滤条件）
     */
    List<SakuraAiVoiceLibrary> selectSakuraAiVoiceLibraryList(SakuraAiVoiceLibrary query);

    /**
     * 新增音色
     */
    int insertSakuraAiVoiceLibrary(SakuraAiVoiceLibrary voiceLibrary);

    /**
     * 修改音色
     */
    int updateSakuraAiVoiceLibrary(SakuraAiVoiceLibrary voiceLibrary);

    /**
     * 批量软删除音色
     */
    int deleteSakuraAiVoiceLibraryByIds(Long[] ids);

    /**
     * 单条软删除音色
     */
    int deleteSakuraAiVoiceLibraryById(Long id);
}
