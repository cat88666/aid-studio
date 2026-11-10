package com.sakura.asset.service;

import java.util.List;
import com.sakura.sakura.domain.SakuraComicAsset;
import com.sakura.asset.dto.OfficialAssetQueryRequest;
import com.sakura.sakura.vo.StyleCategoryVO;

/**
 * 用户资产业务Service接口
 *
 * @author 视觉AID
 */
public interface IUserAssetBusinessService
{
    /**
     * 查询官方素材列表，从 sakura_comic_asset 表查询可复用素材，
     * 不用于查询角色/场景/道具主资产
     *
     * @param request 查询条件
     * @return 素材列表
     */
    List<SakuraComicAsset> queryOfficialAssetList(OfficialAssetQueryRequest request);

    /**
     * 查询C端风格分类选项，包含虚拟“全部”。
     *
     * @return 分类选项
     */
    List<StyleCategoryVO> listStyleCategoryOptions();
}
