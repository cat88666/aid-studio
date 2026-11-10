package com.sakura.sakura.service.impl;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.domain.SakuraStoryboardShotGroupPlan;
import com.sakura.sakura.mapper.SakuraStoryboardShotGroupPlanMapper;
import com.sakura.sakura.service.ISakuraStoryboardShotGroupPlanService;

/**
 * 分镜镜头组拆分计划 Service 实现
 *
 * @author 视觉AID
 */
@Service
public class SakuraStoryboardShotGroupPlanServiceImpl
        extends ServiceImpl<SakuraStoryboardShotGroupPlanMapper, SakuraStoryboardShotGroupPlan>
        implements ISakuraStoryboardShotGroupPlanService
{
}
