package com.sakura.sakura.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraPromptLib;
import com.sakura.sakura.domain.dto.SystemPromptUpdateRequest;
import com.sakura.sakura.domain.vo.PromptVersionCheckVO;
import com.sakura.sakura.domain.vo.PromptVersionItemVO;

/**
 * 提示词素材库(官方预设与用户自定义)Service接口
 *
 * @author 视觉AID
 */
public interface ISakuraPromptLibService extends IService<SakuraPromptLib>
{
    /**
     * 查询提示词素材库(官方预设与用户自定义)
     *
     * @param id 提示词素材库(官方预设与用户自定义)主键
     * @return 提示词素材库(官方预设与用户自定义)
     */
    public SakuraPromptLib selectSakuraPromptLibById(Long id);

    /**
     * 查询提示词素材库(官方预设与用户自定义)列表
     *
     * @param sakuraPromptLib 提示词素材库(官方预设与用户自定义)
     * @return 提示词素材库(官方预设与用户自定义)集合
     */
    public List<SakuraPromptLib> selectSakuraPromptLibList(SakuraPromptLib sakuraPromptLib);

    /**
     * 新增提示词素材库(官方预设与用户自定义)
     *
     * @param sakuraPromptLib 提示词素材库(官方预设与用户自定义)
     * @return 结果
     */
    public int insertSakuraPromptLib(SakuraPromptLib sakuraPromptLib);

    /**
     * 修改提示词素材库(官方预设与用户自定义)
     *
     * @param sakuraPromptLib 提示词素材库(官方预设与用户自定义)
     * @return 结果
     */
    public int updateSakuraPromptLib(SakuraPromptLib sakuraPromptLib);

    /**
     * 批量删除提示词素材库(官方预设与用户自定义)
     *
     * @param ids 需要删除的提示词素材库(官方预设与用户自定义)主键集合
     * @return 结果
     */
    public int deleteSakuraPromptLibByIds(Long[] ids);

    /**
     * 删除提示词素材库(官方预设与用户自定义)信息
     *
     * @param id 提示词素材库(官方预设与用户自定义)主键
     * @return 结果
     */
    public int deleteSakuraPromptLibById(Long id);

    /**
     * 查询系统提示词列表（仅 main_business_prompt / main_teacher_prompt）
     *
     * @param promptType 提示词分类（可选，不传查全部两种类型）
     * @return 提示词列表
     */
    List<SakuraPromptLib> selectSystemPromptList(String promptType);

    /**
     * 修改系统提示词（仅允许修改 main_business_prompt / main_teacher_prompt 及版本号）
     *
     * @param request 修改请求
     * @return 修改后的提示词
     */
    SakuraPromptLib updateSystemPrompt(SystemPromptUpdateRequest request);

    /**
     * 检查系统提示词版本更新状态
     *
     * @return 各提示词的版本检查结果
     */
    List<PromptVersionCheckVO> checkSystemPromptUpdate();

    /**
     * 根据文件名称获取提示词的历史版本列表
     *
     * @param remark 文件名称
     * @return 历史版本列表
     */
    List<PromptVersionItemVO> getPromptVersionsByRemark(String remark);

    /**
     * 拉取系统提示词更新（将本地版本更新到远程最新版本）
     *
     * @param remark 文件名称
     * @return 更新后的提示词
     */
    SakuraPromptLib pullSystemPromptUpdate(String remark);
}
