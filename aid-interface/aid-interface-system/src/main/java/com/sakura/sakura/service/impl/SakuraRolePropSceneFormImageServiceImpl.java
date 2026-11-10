package com.sakura.sakura.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.domain.SakuraRolePropSceneFormImage;
import com.sakura.sakura.mapper.SakuraRolePropSceneFormImageMapper;
import com.sakura.sakura.service.ISakuraRolePropSceneFormImageService;
import org.springframework.stereotype.Service;

/**
 * 角色场景道具形态图片实例 Service 实现（仅承载通用 CRUD，业务编排在 main 模块）。
 *
 * @author 视觉AID
 */
@Service
public class SakuraRolePropSceneFormImageServiceImpl
        extends ServiceImpl<SakuraRolePropSceneFormImageMapper, SakuraRolePropSceneFormImage>
        implements ISakuraRolePropSceneFormImageService
{
}
