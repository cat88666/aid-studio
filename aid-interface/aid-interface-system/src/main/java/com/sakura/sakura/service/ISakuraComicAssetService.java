package com.sakura.sakura.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraComicAsset;
import com.sakura.sakura.vo.StyleCategoryVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * 项目提取资产Service接口
 *
 * @author 视觉AID
 */
public interface ISakuraComicAssetService extends IService<SakuraComicAsset>
{
    /**
     * 查询项目提取资产
     *
     * @param id 项目提取资产主键
     * @return 项目提取资产
     */
    public SakuraComicAsset selectSakuraComicAssetById(Long id);

    /**
     * 查询项目提取资产列表
     *
     * @param sakuraComicAsset 项目提取资产
     * @return 项目提取资产集合
     */
    public List<SakuraComicAsset> selectSakuraComicAssetList(SakuraComicAsset sakuraComicAsset);

    /**
     * 新增项目提取资产
     *
     * @param sakuraComicAsset 项目提取资产
     * @return 结果
     */
    public int insertSakuraComicAsset(SakuraComicAsset sakuraComicAsset);

    /**
     * 修改项目提取资产
     *
     * @param sakuraComicAsset 项目提取资产
     * @return 结果
     */
    public int updateSakuraComicAsset(SakuraComicAsset sakuraComicAsset);

    /**
     * 批量删除项目提取资产
     *
     * @param ids 需要删除的项目提取资产主键集合
     * @return 结果
     */
    public int deleteSakuraComicAssetByIds(Long[] ids);

    /**
     * 删除项目提取资产信息
     *
     * @param id 项目提取资产主键
     * @return 结果
     */
    public int deleteSakuraComicAssetById(Long id);

    /**
     * 校验并归一化风格分类筛选值。
     *
     * @param categoryCode 分类代码，all或空表示全部
     * @return 有效分类代码；全部时返回null
     */
    String normalizeStyleCategoryFilter(String categoryCode);

    /**
     * 为官方素材查询追加风格分类条件。
     *
     * @param wrapper 查询条件
     * @param categoryCode 已归一化的分类代码
     */
    void applyStyleCategoryFilter(LambdaQueryWrapper<SakuraComicAsset> wrapper, String categoryCode);

    /**
     * 批量装配风格分类，避免逐条查询。
     *
     * @param assets 官方素材列表
     */
    void attachStyleCategories(List<SakuraComicAsset> assets);

    /**
     * 查询风格分类选项。
     *
     * @param includeAll 是否包含虚拟“全部”选项
     * @return 分类选项
     */
    List<StyleCategoryVO> listStyleCategoryOptions(boolean includeAll);
}
