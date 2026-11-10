package com.sakura.sakura.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraUserComicAsset;

/**
 * 用户自定义漫画参考资产Service接口
 *
 * @author 视觉AID
 */
public interface ISakuraUserComicAssetService extends IService<SakuraUserComicAsset>
{
    /**
     * 查询用户自定义漫画参考资产
     *
     * @param id 用户自定义漫画参考资产主键
     * @return 用户自定义漫画参考资产
     */
    public SakuraUserComicAsset selectSakuraUserComicAssetById(Long id);

    /**
     * 查询用户自定义漫画参考资产列表
     *
     * @param sakuraUserComicAsset 用户自定义漫画参考资产
     * @return 用户自定义漫画参考资产集合
     */
    public List<SakuraUserComicAsset> selectSakuraUserComicAssetList(SakuraUserComicAsset sakuraUserComicAsset);

    /**
     * 新增用户自定义漫画参考资产
     *
     * @param sakuraUserComicAsset 用户自定义漫画参考资产
     * @return 结果
     */
    public int insertSakuraUserComicAsset(SakuraUserComicAsset sakuraUserComicAsset);

    /**
     * 修改用户自定义漫画参考资产
     *
     * @param sakuraUserComicAsset 用户自定义漫画参考资产
     * @return 结果
     */
    public int updateSakuraUserComicAsset(SakuraUserComicAsset sakuraUserComicAsset);

    /**
     * 批量删除用户自定义漫画参考资产
     *
     * @param ids 需要删除的用户自定义漫画参考资产主键集合
     * @return 结果
     */
    public int deleteSakuraUserComicAssetByIds(Long[] ids);

    /**
     * 删除用户自定义漫画参考资产信息
     *
     * @param id 用户自定义漫画参考资产主键
     * @return 结果
     */
    public int deleteSakuraUserComicAssetById(Long id);
}
