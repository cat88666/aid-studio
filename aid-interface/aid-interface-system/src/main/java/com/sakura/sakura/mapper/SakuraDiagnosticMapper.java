package com.sakura.sakura.mapper;
import com.sakura.sakura.domain.SakuraDiagnosticEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
/** 诊断事件映射。 */
@Mapper
public interface SakuraDiagnosticMapper extends BaseMapper<SakuraDiagnosticEvent> { }
