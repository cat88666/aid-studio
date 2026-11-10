package com.sakura.common.error.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakura.sakura.domain.SakuraErrorLog;
import com.sakura.sakura.mapper.SakuraErrorLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 错误样本日志查询 Service（管理后台用）。
 *
 * @author 视觉AID
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SakuraErrorLogQueryService {

    private final SakuraErrorLogMapper errorLogMapper;

    /**
     * 列表查询。
     *
     * @param providerCode 厂商编码
     * @param onlyUnmatched 仅查未识别样本（matched_rule_id IS NULL）
     */
    public List<SakuraErrorLog> list(String providerCode, Boolean onlyUnmatched) {
        LambdaQueryWrapper<SakuraErrorLog> qw = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(providerCode)) {
            qw.eq(SakuraErrorLog::getProviderCode, providerCode);
        }
        if (Boolean.TRUE.equals(onlyUnmatched)) {
            qw.isNull(SakuraErrorLog::getMatchedRuleId);
        }
        qw.orderByDesc(SakuraErrorLog::getOccurrenceCount, SakuraErrorLog::getLastSeen);
        return errorLogMapper.selectList(qw);
    }

    public SakuraErrorLog get(Long id) {
        return errorLogMapper.selectById(id);
    }
}
