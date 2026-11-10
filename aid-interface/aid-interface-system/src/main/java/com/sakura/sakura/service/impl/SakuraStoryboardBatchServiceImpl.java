package com.sakura.sakura.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.domain.SakuraStoryboardBatch;
import com.sakura.sakura.mapper.SakuraStoryboardBatchMapper;
import com.sakura.sakura.service.ISakuraStoryboardBatchService;
import org.springframework.stereotype.Service;

/**
 * 分镜脚本批次 Service 实现
 *
 * @author 视觉AID
 */
@Service
public class SakuraStoryboardBatchServiceImpl extends ServiceImpl<SakuraStoryboardBatchMapper, SakuraStoryboardBatch>
        implements ISakuraStoryboardBatchService
{
}
