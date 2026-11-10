package com.sakura.sakura.service.impl;

import com.sakura.sakura.domain.media.SakuraMediaTask;
import com.sakura.sakura.mapper.SakuraMediaTaskMapper;
import com.sakura.sakura.service.ISakuraMediaTaskService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 媒体任务 Service 实现。
 *
 * @author 视觉AID
 */
@Service
public class SakuraMediaTaskServiceImpl extends ServiceImpl<SakuraMediaTaskMapper, SakuraMediaTask>
        implements ISakuraMediaTaskService
{
}
