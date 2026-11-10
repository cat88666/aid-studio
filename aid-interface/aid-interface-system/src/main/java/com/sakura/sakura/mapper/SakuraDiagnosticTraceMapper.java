package com.sakura.sakura.mapper;
import com.sakura.sakura.domain.SakuraDiagnosticTrace;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
/** 异步诊断提交快照映射。 */
@Mapper
public interface SakuraDiagnosticTraceMapper extends BaseMapper<SakuraDiagnosticTrace> { }
