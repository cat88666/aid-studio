package com.sakura.sakura.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraComicScript;

/**
 * 剧本原文与简化版Service接口
 *
 * @author 视觉AID
 */
public interface ISakuraComicScriptService extends IService<SakuraComicScript>
{
    /**
     * 查询剧本原文与简化版
     *
     * @param id 剧本原文与简化版主键
     * @return 剧本原文与简化版
     */
    public SakuraComicScript selectSakuraComicScriptById(Long id);

    /**
     * 查询剧本原文与简化版列表
     *
     * @param sakuraComicScript 剧本原文与简化版
     * @return 剧本原文与简化版集合
     */
    public List<SakuraComicScript> selectSakuraComicScriptList(SakuraComicScript sakuraComicScript);

    /**
     * 新增剧本原文与简化版
     *
     * @param sakuraComicScript 剧本原文与简化版
     * @return 结果
     */
    public int insertSakuraComicScript(SakuraComicScript sakuraComicScript);

    /**
     * 修改剧本原文与简化版
     *
     * @param sakuraComicScript 剧本原文与简化版
     * @return 结果
     */
    public int updateSakuraComicScript(SakuraComicScript sakuraComicScript);

    /**
     * 批量删除剧本原文与简化版
     *
     * @param ids 需要删除的剧本原文与简化版主键集合
     * @return 结果
     */
    public int deleteSakuraComicScriptByIds(Long[] ids);

    /**
     * 删除剧本原文与简化版信息
     *
     * @param id 剧本原文与简化版主键
     * @return 结果
     */
    public int deleteSakuraComicScriptById(Long id);
}
