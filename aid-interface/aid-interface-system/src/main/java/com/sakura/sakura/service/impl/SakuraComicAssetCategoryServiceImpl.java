package com.sakura.sakura.service.impl;

import com.sakura.sakura.domain.SakuraComicAssetCategory;
import com.sakura.sakura.mapper.SakuraComicAssetCategoryMapper;
import com.sakura.sakura.service.ISakuraComicAssetCategoryService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 官方风格分类关系Service实现。
 *
 * @author 视觉AID
 */
@Service
public class SakuraComicAssetCategoryServiceImpl
        extends ServiceImpl<SakuraComicAssetCategoryMapper, SakuraComicAssetCategory>
        implements ISakuraComicAssetCategoryService {
}
