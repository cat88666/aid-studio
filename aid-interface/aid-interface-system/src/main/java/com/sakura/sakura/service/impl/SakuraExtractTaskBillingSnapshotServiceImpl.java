package com.sakura.sakura.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.domain.SakuraExtractTask;
import com.sakura.sakura.domain.SakuraExtractTaskBillingSnapshot;
import com.sakura.sakura.mapper.SakuraExtractTaskBillingSnapshotMapper;
import com.sakura.sakura.service.ISakuraExtractTaskBillingSnapshotService;
import com.sakura.common.exception.ServiceException;
import com.sakura.common.utils.DateUtils;

import cn.hutool.core.util.StrUtil;

/**
 * 提取任务计费快照Service业务层处理
 *
 * @author 视觉AID
 */
@Service
public class SakuraExtractTaskBillingSnapshotServiceImpl
        extends ServiceImpl<SakuraExtractTaskBillingSnapshotMapper, SakuraExtractTaskBillingSnapshot>
        implements ISakuraExtractTaskBillingSnapshotService
{
    private static final String DEL_FLAG_NORMAL = "0";

    @Override
    public void saveOrUpdateSnapshot(SakuraExtractTask task, String snapshotStage, String snapshotJson)
    {
        if (Objects.isNull(task) || Objects.isNull(task.getId()) || StrUtil.isBlank(snapshotStage))
        {
            throw new ServiceException("快照任务异常");
        }
        if (StrUtil.isBlank(snapshotJson))
        {
            deleteSnapshot(task.getId(), snapshotStage);
            return;
        }

        SakuraExtractTaskBillingSnapshot existing = this.getOne(
                Wrappers.<SakuraExtractTaskBillingSnapshot>lambdaQuery()
                        .select(SakuraExtractTaskBillingSnapshot::getId)
                        .eq(SakuraExtractTaskBillingSnapshot::getTaskId, task.getId())
                        .eq(SakuraExtractTaskBillingSnapshot::getSnapshotStage, snapshotStage)
                        .last("limit 1"));

        SakuraExtractTaskBillingSnapshot snapshot = Objects.nonNull(existing)
                ? existing : new SakuraExtractTaskBillingSnapshot();
        snapshot.setTaskId(task.getId());
        snapshot.setProjectId(task.getProjectId());
        snapshot.setEpisodeId(task.getEpisodeId());
        snapshot.setUserId(task.getUserId());
        snapshot.setTaskType(task.getTaskType());
        snapshot.setSnapshotStage(snapshotStage);
        snapshot.setSnapshotJson(snapshotJson);
        snapshot.setSnapshotSize(snapshotJson.length());
        snapshot.setSnapshotHash(sha256(snapshotJson));
        snapshot.setDelFlag(DEL_FLAG_NORMAL);
        snapshot.setUpdateTime(DateUtils.getNowDate());
        snapshot.setUpdateBy(String.valueOf(task.getUserId()));
        if (Objects.isNull(snapshot.getId()))
        {
            snapshot.setCreateTime(DateUtils.getNowDate());
            snapshot.setCreateBy(String.valueOf(task.getUserId()));
            this.save(snapshot);
        }
        else
        {
            this.updateById(snapshot);
        }
    }

    @Override
    public String getSnapshotJson(Long taskId, String snapshotStage)
    {
        if (Objects.isNull(taskId) || StrUtil.isBlank(snapshotStage))
        {
            return null;
        }
        SakuraExtractTaskBillingSnapshot snapshot = this.getOne(
                Wrappers.<SakuraExtractTaskBillingSnapshot>lambdaQuery()
                        .select(SakuraExtractTaskBillingSnapshot::getId,
                                SakuraExtractTaskBillingSnapshot::getSnapshotJson)
                        .eq(SakuraExtractTaskBillingSnapshot::getTaskId, taskId)
                        .eq(SakuraExtractTaskBillingSnapshot::getSnapshotStage, snapshotStage)
                        .eq(SakuraExtractTaskBillingSnapshot::getDelFlag, DEL_FLAG_NORMAL)
                        .last("limit 1"));
        return Objects.isNull(snapshot) ? null : snapshot.getSnapshotJson();
    }

    @Override
    public void deleteSnapshot(Long taskId, String snapshotStage)
    {
        if (Objects.isNull(taskId) || StrUtil.isBlank(snapshotStage))
        {
            return;
        }
        this.remove(Wrappers.<SakuraExtractTaskBillingSnapshot>lambdaQuery()
                .eq(SakuraExtractTaskBillingSnapshot::getTaskId, taskId)
                .eq(SakuraExtractTaskBillingSnapshot::getSnapshotStage, snapshotStage));
    }

    private String sha256(String value)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash)
            {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new ServiceException("快照哈希异常");
        }
    }
}
