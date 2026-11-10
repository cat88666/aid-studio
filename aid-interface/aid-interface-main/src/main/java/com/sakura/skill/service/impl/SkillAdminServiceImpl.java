package com.sakura.skill.service.impl;

import cn.hutool.core.util.StrUtil;
import com.sakura.sakura.domain.media.SakuraMediaTask;
import com.sakura.sakura.mapper.SakuraMediaTaskMapper;
import com.sakura.common.exception.ServiceException;
import com.sakura.skill.domain.SakuraSkill;
import com.sakura.skill.domain.SakuraSkillRelation;
import com.sakura.skill.domain.SakuraSkillRun;
import com.sakura.skill.domain.SakuraSkillRunStep;
import com.sakura.skill.domain.SakuraSkillRunTaskLink;
import com.sakura.skill.dto.SkillAdminRequests;
import com.sakura.skill.mapper.SakuraSkillMapper;
import com.sakura.skill.mapper.SakuraSkillRelationMapper;
import com.sakura.skill.mapper.SakuraSkillRunMapper;
import com.sakura.skill.mapper.SakuraSkillRunStepMapper;
import com.sakura.skill.mapper.SakuraSkillRunTaskLinkMapper;
import com.sakura.skill.service.ISkillAdminService;
import com.sakura.skill.service.SkillRuntimeCapabilities;
import com.sakura.skill.service.SkillModelService;
import com.sakura.skill.vo.SkillAdminVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Administration is independent from the removed legacy chat implementation. */
@Service
@RequiredArgsConstructor
public class SkillAdminServiceImpl implements ISkillAdminService {
    private static final String NORMAL = "0";
    private static final String DELETED = "1";
    private static final String DISABLED = "1";

    private final SakuraSkillMapper skillMapper;
    private final SakuraSkillRelationMapper relationMapper;
    private final SakuraSkillRunMapper runMapper;
    private final SakuraSkillRunStepMapper stepMapper;
    private final SakuraSkillRunTaskLinkMapper taskLinkMapper;
    private final SakuraMediaTaskMapper mediaTaskMapper;
    private final SkillModelService skillModelService;

    @Override
    public SkillAdminVO.PageResult<SkillAdminVO.SkillSummary> pageSkills(
            SkillAdminRequests.PageRequest request) {
        SkillAdminRequests.PageRequest source = Objects.requireNonNullElseGet(
                request, SkillAdminRequests.PageRequest::new);
        Page<SakuraSkill> page = skillMapper.selectPage(
                new Page<>(positive(source.getPageNum(), 1), bounded(source.getPageSize(), 20, 100)),
                new LambdaQueryWrapper<SakuraSkill>()
                        .select(SakuraSkill::getId, SakuraSkill::getSkillCode, SakuraSkill::getName,
                                SakuraSkill::getDescription, SakuraSkill::getCapabilityDescription,
                                SakuraSkill::getIconUrl, SakuraSkill::getOwnerType,
                                SakuraSkill::getVisibility, SakuraSkill::getInvocationScope,
                                SakuraSkill::getCurrentVersionId, SakuraSkill::getStatus, SakuraSkill::getDelFlag,
                                SakuraSkill::getModelCode, SakuraSkill::getReasoningPolicy, SakuraSkill::getUpdateTime)
                        .ne(SakuraSkill::getSkillCode, SkillRuntimeCapabilities.RETIRED_SCREENPLAY_CHAT)
                        .and(StrUtil.isNotBlank(source.getKeyword()), query -> query
                                .like(SakuraSkill::getName, source.getKeyword())
                                .or().like(SakuraSkill::getSkillCode, source.getKeyword()))
                        .eq(StrUtil.isNotBlank(source.getStatus()), SakuraSkill::getStatus, source.getStatus())
                        .orderByDesc(SakuraSkill::getId));
        return new SkillAdminVO.PageResult<>(page.getTotal(), page.getRecords().stream()
                .map(this::toSkillSummary).toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateIdentity(SkillAdminRequests.IdentitySaveRequest request,
                               Long operatorId, String operatorName) {
        SakuraSkill skill = requireActiveSkillForUpdate(request.getId());
        SakuraSkill update = new SakuraSkill();
        update.setId(skill.getId());
        update.setName(StrUtil.trim(request.getName()));
        update.setDescription(StrUtil.trim(request.getDescription()));
        update.setCapabilityDescription(StrUtil.trim(request.getCapabilityDescription()));
        update.setIconUrl(StrUtil.trim(request.getIconUrl()));
        String nextStatus = normalizeStatus(request.getStatus());
        if (NORMAL.equals(skill.getStatus()) && DISABLED.equals(nextStatus)) {
            ensureSkillNotInUse(skill);
        }
        update.setStatus(nextStatus);
        update.setUpdateBy(operator(operatorName, operatorId));
        update.setUpdateTime(new Date());
        ensureChanged(skillMapper.update(update, Wrappers.<SakuraSkill>lambdaUpdate()
                .eq(SakuraSkill::getId, skill.getId()).eq(SakuraSkill::getDelFlag, NORMAL)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(SkillAdminRequests.StatusRequest request,
                             Long operatorId, String operatorName) {
        SakuraSkill skill = requireActiveSkillForUpdate(request.getId());
        String nextStatus = normalizeStatus(request.getStatus());
        if (NORMAL.equals(skill.getStatus()) && DISABLED.equals(nextStatus)) {
            ensureSkillNotInUse(skill);
        }
        ensureChanged(skillMapper.update(null, new LambdaUpdateWrapper<SakuraSkill>()
                .eq(SakuraSkill::getId, skill.getId()).eq(SakuraSkill::getDelFlag, NORMAL)
                .set(SakuraSkill::getStatus, nextStatus)
                .set(SakuraSkill::getUpdateBy, operator(operatorName, operatorId))
                .set(SakuraSkill::getUpdateTime, new Date())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSkill(Long id, Long operatorId, String operatorName) {
        SakuraSkill skill = requireActiveSkillForUpdate(id);
        ensureSkillNotInUse(skill);
        ensureChanged(skillMapper.update(null, new LambdaUpdateWrapper<SakuraSkill>()
                .eq(SakuraSkill::getId, skill.getId()).eq(SakuraSkill::getDelFlag, NORMAL)
                .set(SakuraSkill::getStatus, DISABLED).set(SakuraSkill::getDelFlag, DELETED)
                .set(SakuraSkill::getUpdateBy, operator(operatorName, operatorId))
                .set(SakuraSkill::getUpdateTime, new Date())));
    }

    @Override
    public void restoreSkill(Long id, Long operatorId, String operatorName) {
        SakuraSkill skill = requireSkill(id);
        if (!DELETED.equals(skill.getDelFlag())) {
            throw new ServiceException("Skill is not deleted");
        }
        ensureChanged(skillMapper.update(null, new LambdaUpdateWrapper<SakuraSkill>()
                .eq(SakuraSkill::getId, skill.getId()).eq(SakuraSkill::getDelFlag, DELETED)
                .set(SakuraSkill::getStatus, DISABLED).set(SakuraSkill::getDelFlag, NORMAL)
                .set(SakuraSkill::getUpdateBy, operator(operatorName, operatorId))
                .set(SakuraSkill::getUpdateTime, new Date())));
    }

    @Override
    public List<SkillAdminVO.TextModelOption> listTextModelOptions() {
        return skillModelService.adminOptions();
    }

    @Override
    public SkillAdminVO.PageResult<SkillAdminVO.RunSummary> pageRuns(
            SkillAdminRequests.RunPageRequest request) {
        SkillAdminRequests.RunPageRequest source = Objects.requireNonNullElseGet(
                request, SkillAdminRequests.RunPageRequest::new);
        Page<SakuraSkillRun> page = runMapper.selectPage(
                new Page<>(positive(source.getPageNum(), 1), bounded(source.getPageSize(), 20, 100)),
                Wrappers.<SakuraSkillRun>lambdaQuery()
                        .select(SakuraSkillRun::getId, SakuraSkillRun::getUserId, SakuraSkillRun::getSkillId,
                                SakuraSkillRun::getSkillVersionId, SakuraSkillRun::getProjectId,
                                SakuraSkillRun::getEpisodeId, SakuraSkillRun::getSkillConfigHash,
                                SakuraSkillRun::getModelCode, SakuraSkillRun::getInvokeSource,
                                SakuraSkillRun::getClientRequestId, SakuraSkillRun::getGeneration,
                                SakuraSkillRun::getStatus, SakuraSkillRun::getStage, SakuraSkillRun::getActionMode,
                                SakuraSkillRun::getQualityMode, SakuraSkillRun::getStartedAt,
                                SakuraSkillRun::getFinishedAt)
                        .eq(source.getSkillId() != null, SakuraSkillRun::getSkillId, source.getSkillId())
                        .eq(source.getUserId() != null, SakuraSkillRun::getUserId, source.getUserId())
                        .eq(StrUtil.isNotBlank(source.getStatus()), SakuraSkillRun::getStatus, source.getStatus())
                        .isNotNull(SakuraSkillRun::getSkillVersionId)
                        .eq(SakuraSkillRun::getDelFlag, NORMAL)
                        .orderByDesc(SakuraSkillRun::getId));
        return new SkillAdminVO.PageResult<>(page.getTotal(), page.getRecords().stream()
                .map(this::toRunSummary).toList());
    }

    @Override
    public SkillAdminVO.RunItem getRun(Long runId) {
        SakuraSkillRun run = runMapper.selectOne(Wrappers.<SakuraSkillRun>lambdaQuery()
                .eq(SakuraSkillRun::getId, runId).isNotNull(SakuraSkillRun::getSkillVersionId)
                .eq(SakuraSkillRun::getDelFlag, NORMAL).last("limit 1"));
        if (run == null) {
            throw new ServiceException("Runtime run not found");
        }
        SkillAdminVO.RunItem item = toRunItem(run);
        item.setTasks(loadRunTasks(run.getId()));
        return item;
    }

    private List<SkillAdminVO.RunTaskItem> loadRunTasks(Long runId) {
        List<SakuraSkillRunStep> steps = stepMapper.selectList(Wrappers.<SakuraSkillRunStep>lambdaQuery()
                .eq(SakuraSkillRunStep::getRunId, runId).eq(SakuraSkillRunStep::getDelFlag, NORMAL)
                .orderByAsc(SakuraSkillRunStep::getStepSeq).orderByAsc(SakuraSkillRunStep::getId));
        if (steps.isEmpty()) {
            return List.of();
        }
        List<SakuraSkillRunTaskLink> links = taskLinkMapper.selectList(
                Wrappers.<SakuraSkillRunTaskLink>lambdaQuery().eq(SakuraSkillRunTaskLink::getRunId, runId)
                        .eq(SakuraSkillRunTaskLink::getDelFlag, NORMAL));
        Map<Long, SakuraSkillRunTaskLink> linkByStep = links.stream().collect(Collectors.toMap(
                SakuraSkillRunTaskLink::getStepId, Function.identity(), (left, right) -> left));
        List<Long> taskIds = links.stream().map(SakuraSkillRunTaskLink::getMediaTaskId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, SakuraMediaTask> tasks = taskIds.isEmpty() ? Collections.emptyMap()
                : mediaTaskMapper.selectList(Wrappers.<SakuraMediaTask>lambdaQuery()
                        .select(SakuraMediaTask::getId, SakuraMediaTask::getStatus,
                                SakuraMediaTask::getBillingStatus, SakuraMediaTask::getActualCost)
                        .in(SakuraMediaTask::getId, taskIds)).stream()
                        .collect(Collectors.toMap(SakuraMediaTask::getId, Function.identity()));
        return steps.stream().map(step -> toRunTask(step, linkByStep.get(step.getId()), tasks)).toList();
    }

    private SkillAdminVO.RunTaskItem toRunTask(SakuraSkillRunStep step, SakuraSkillRunTaskLink link,
                                                Map<Long, SakuraMediaTask> tasks) {
        SkillAdminVO.RunTaskItem item = new SkillAdminVO.RunTaskItem();
        item.setStepId(step.getId());
        item.setStepSeq(step.getStepSeq());
        item.setStepKey(step.getStepKey());
        item.setStepExecutionId(step.getStepExecutionId());
        item.setSkillId(step.getSkillId());
        item.setSkillVersionId(step.getSkillVersionId());
        item.setActionMode(step.getActionMode());
        item.setWorkflowAttempt(step.getWorkflowAttempt());
        item.setOrchestrationStatus(step.getOrchestrationStatus());
        if (link != null) {
            item.setMediaTaskId(link.getMediaTaskId());
            SakuraMediaTask task = tasks.get(link.getMediaTaskId());
            if (task != null) {
                item.setMediaStatus(task.getStatus());
                item.setBillingStatus(task.getBillingStatus());
                item.setActualCost(task.getActualCost() == null ? null : task.getActualCost().toPlainString());
            }
        }
        return item;
    }

    private SakuraSkill requireSkill(Long id) {
        if (id == null || id <= 0) {
            throw new ServiceException("Invalid Skill id");
        }
        SakuraSkill skill = skillMapper.selectById(id);
        if (skill == null || SkillRuntimeCapabilities.RETIRED_SCREENPLAY_CHAT.equals(skill.getSkillCode())) {
            throw new ServiceException("Skill not found");
        }
        return skill;
    }

    private SakuraSkill requireActiveSkillForUpdate(Long id) {
        if (id == null || id <= 0) {
            throw new ServiceException("Invalid Skill id");
        }
        SakuraSkill skill = skillMapper.selectOne(Wrappers.<SakuraSkill>lambdaQuery()
                .eq(SakuraSkill::getId, id).last("FOR UPDATE"));
        if (skill == null || SkillRuntimeCapabilities.RETIRED_SCREENPLAY_CHAT.equals(skill.getSkillCode())) {
            throw new ServiceException("Skill not found");
        }
        if (!NORMAL.equals(skill.getDelFlag())) {
            throw new ServiceException("Skill is deleted");
        }
        return skill;
    }

    private SkillAdminVO.SkillSummary toSkillSummary(SakuraSkill skill) {
        SkillAdminVO.SkillSummary item = new SkillAdminVO.SkillSummary();
        item.setId(skill.getId());
        item.setSkillCode(skill.getSkillCode());
        item.setName(SkillRuntimeCapabilities.displayName(skill.getSkillCode(), skill.getName()));
        item.setDescription(SkillRuntimeCapabilities.displayDescription(
                skill.getSkillCode(), skill.getDescription()));
        item.setCapabilityDescription(skill.getCapabilityDescription());
        item.setIconUrl(skill.getIconUrl());
        item.setOwnerType(skill.getOwnerType());
        item.setVisibility(skill.getVisibility());
        item.setInvocationScope(skill.getInvocationScope());
        item.setCurrentVersionId(skill.getCurrentVersionId());
        item.setStatus(skill.getStatus());
        item.setDelFlag(skill.getDelFlag());
        item.setModelCode(skill.getModelCode());
        item.setReasoningPolicy(skill.getReasoningPolicy());
        item.setUpdateTime(skill.getUpdateTime());
        return item;
    }

    private SkillAdminVO.RunSummary toRunSummary(SakuraSkillRun run) {
        SkillAdminVO.RunSummary item = new SkillAdminVO.RunSummary();
        copyRunSummary(run, item);
        return item;
    }

    private SkillAdminVO.RunItem toRunItem(SakuraSkillRun run) {
        SkillAdminVO.RunItem item = new SkillAdminVO.RunItem();
        copyRunSummary(run, item);
        item.setClientRequestDigest(run.getClientRequestDigest());
        item.setExecutionSnapshotDigest(run.getExecutionSnapshotDigest());
        item.setResolvedConfigDigest(run.getResolvedConfigDigest());
        item.setRootRunId(run.getRootRunId());
        item.setParentRunId(run.getParentRunId());
        item.setInputJson(run.getInputJson());
        item.setOutputJson(run.getOutputJson());
        item.setErrorMessage(run.getErrorMessage());
        return item;
    }

    private void copyRunSummary(SakuraSkillRun run, SkillAdminVO.RunSummary item) {
        item.setId(run.getId());
        item.setUserId(run.getUserId());
        item.setSkillId(run.getSkillId());
        item.setSkillVersionId(run.getSkillVersionId());
        item.setProjectId(run.getProjectId());
        item.setEpisodeId(run.getEpisodeId());
        item.setSkillConfigHash(run.getSkillConfigHash());
        item.setModelCode(run.getModelCode());
        item.setInvokeSource(run.getInvokeSource());
        item.setClientRequestId(run.getClientRequestId());
        item.setGeneration(run.getGeneration());
        item.setStatus(run.getStatus());
        item.setStage(run.getStage());
        item.setActionMode(run.getActionMode());
        item.setQualityMode(run.getQualityMode());
        item.setStartedAt(run.getStartedAt());
        item.setFinishedAt(run.getFinishedAt());
        if (run.getStartedAt() != null && run.getFinishedAt() != null) {
            item.setDurationMillis(Math.max(0L,
                    run.getFinishedAt().getTime() - run.getStartedAt().getTime()));
        }
    }

    private static int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private void ensureSkillNotInUse(SakuraSkill skill) {
        List<Long> parentVersionIds = relationMapper.selectList(
                        Wrappers.<SakuraSkillRelation>lambdaQuery()
                                .select(SakuraSkillRelation::getParentVersionId)
                                .eq(SakuraSkillRelation::getChildSkillId, skill.getId())
                                .eq(SakuraSkillRelation::getDelFlag, NORMAL))
                .stream().map(SakuraSkillRelation::getParentVersionId)
                .filter(Objects::nonNull).distinct().toList();
        if (parentVersionIds.isEmpty()) {
            return;
        }
        Long activeEntrypoints = skillMapper.selectCount(Wrappers.<SakuraSkill>lambdaQuery()
                .in(SakuraSkill::getCurrentVersionId, parentVersionIds)
                .eq(SakuraSkill::getInvocationScope, "ENTRYPOINT")
                .eq(SakuraSkill::getStatus, NORMAL)
                .eq(SakuraSkill::getDelFlag, NORMAL));
        Long activeRuns = runMapper.selectCount(Wrappers.<SakuraSkillRun>lambdaQuery()
                .in(SakuraSkillRun::getSkillVersionId, parentVersionIds)
                .notIn(SakuraSkillRun::getStatus, "SUCCEEDED", "FAILED", "CANCELED")
                .eq(SakuraSkillRun::getDelFlag, NORMAL));
        if ((activeEntrypoints != null && activeEntrypoints > 0)
                || (activeRuns != null && activeRuns > 0)) {
            throw new ServiceException("Skill is referenced by an active Runtime package or Run");
        }
    }

    private static int bounded(Integer value, int fallback, int max) {
        return Math.min(positive(value, fallback), max);
    }

    private static String normalizeStatus(String status) {
        if (!NORMAL.equals(status) && !DISABLED.equals(status)) {
            throw new ServiceException("Invalid Skill status");
        }
        return status;
    }

    private static String operator(String name, Long id) {
        return StrUtil.blankToDefault(StrUtil.trim(name), id == null ? "system" : String.valueOf(id));
    }

    private static void ensureChanged(int changed) {
        if (changed != 1) {
            throw new ServiceException("Skill changed concurrently");
        }
    }
}
