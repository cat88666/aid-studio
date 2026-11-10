package com.sakura.sakura.service.impl;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.domain.SakuraScenePlot;
import com.sakura.sakura.mapper.SakuraScenePlotMapper;
import com.sakura.sakura.service.ISakuraScenePlotService;

/**
 * 剧情节拍 Service 实现。
 *
 * @author 视觉AID
 */
@Service
public class SakuraScenePlotServiceImpl extends ServiceImpl<SakuraScenePlotMapper, SakuraScenePlot>
        implements ISakuraScenePlotService
{
}
