package com.sakura.sakura.domain;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;
/** 等待异步任务终态期间的去敏提交快照。 */
@Data
@TableName("sakura_diagnostic_trace")
public class SakuraDiagnosticTrace {
    @TableId private String taskKey;
    private String requestId;
    private String requestJson;
    private Date createTime;
    private String createBy;
}
