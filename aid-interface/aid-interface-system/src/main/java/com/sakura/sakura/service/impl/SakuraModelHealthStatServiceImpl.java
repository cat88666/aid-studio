package com.sakura.sakura.service.impl;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.domain.SakuraModelHealthStat;
import com.sakura.sakura.mapper.SakuraModelHealthStatMapper;
import com.sakura.sakura.service.ISakuraModelHealthStatService;

/**
 * AI模型健康统计Service业务层处理
 *
 * @author 视觉AID
 */
@Service
public class SakuraModelHealthStatServiceImpl extends ServiceImpl<SakuraModelHealthStatMapper, SakuraModelHealthStat>
        implements ISakuraModelHealthStatService
{
}
