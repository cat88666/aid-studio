package com.sakura.sakura.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 官方风格与分类的多对多关系。
 *
 * @author 视觉AID
 */
@Data
@TableName("sakura_comic_asset_category")
public class SakuraComicAssetCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 官方风格资产ID */
    private Long assetId;

    /** 稳定分类代码 */
    private String categoryCode;
}
