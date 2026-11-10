package com.sakura.rps.service.impl;

import com.sakura.media.util.MediaTaskPayloadSanitizer;
import com.sakura.common.error.ErrorNormalizer;
import com.sakura.common.error.TaskErrorSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.sakura.sakura.domain.SakuraAiModel;
import com.sakura.sakura.domain.SakuraAiModelFuncConfig;
import com.sakura.sakura.domain.SakuraComicProject;
import com.sakura.sakura.domain.SakuraExtractTask;
import com.sakura.sakura.domain.SakuraRolePropScene;
import com.sakura.sakura.domain.SakuraRolePropSceneForm;
import com.sakura.sakura.domain.SakuraRolePropSceneFormImage;
import com.sakura.sakura.service.ISakuraAiModelFuncConfigService;
import com.sakura.sakura.service.ISakuraAiModelService;
import com.sakura.sakura.service.ISakuraComicProjectService;
import com.sakura.sakura.service.ISakuraExtractTaskService;
import com.sakura.sakura.service.ISakuraRolePropSceneFormImageService;
import com.sakura.sakura.service.ISakuraRolePropSceneFormService;
import com.sakura.sakura.service.ISakuraRolePropSceneService;
import com.sakura.common.core.redis.RedisCache;
import com.sakura.common.utils.DateUtils;
import com.sakura.common.utils.image.ImageUrlValidator;
import com.sakura.domain.vo.AiModelConfigVo;
import com.sakura.media.dto.MediaImageGenerateRequest;
import com.sakura.media.dto.MediaTaskResponse;
import com.sakura.media.service.IMediaGenerationService;
import com.sakura.media.service.MediaBillingQuoteService;
import com.sakura.billing.vo.BillingQuoteVO;
import com.sakura.media.util.ModelCapabilityResolver;
import com.sakura.rps.dto.AssetExtractTaskVO;
import com.sakura.rps.dto.FormMultiViewImageGenerateRequest;
import com.sakura.rps.helper.AssetExtractHelper;
import com.sakura.rps.service.IAssetExtractService;
import com.sakura.rps.service.IFormImageSelectionService;
import com.sakura.rps.service.IFormMultiViewImageService;
import com.sakura.rps.sse.AssetExtractSseManager;
import com.sakura.service.IAiModelConfigService;
import com.sakura.agent.AgentDefaultParamsApplier;
import com.sakura.agent.AgentModelDefault;
import com.sakura.agent.ISakuraAgentService;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 多机位形态生图 Service 实现。
 *
 * @author 视觉AID
 */
@Slf4j
@Service
public class FormMultiViewImageServiceImpl implements IFormMultiViewImageService
{
    /** 删除标记：正常 */
    private static final String DEL_FLAG_NORMAL = "0";

    /** 状态：启用 */
    private static final String STATUS_NORMAL = "0";

    /** 任务状态流 */
    private static final String TASK_STATUS_PENDING = "PENDING";
    private static final String TASK_STATUS_PROCESSING = "PROCESSING";
    private static final String TASK_STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String TASK_STATUS_FAILED = "FAILED";
    /** 用户主动取消（与 AssetExtractServiceImpl 保持一致） */
    private static final String TASK_STATUS_CANCELLED = "CANCELLED";

    /** 任务类型：多机位形态生图（与 AssetExtractServiceImpl 常量保持一致） */
    private static final String TASK_TYPE_FORM_MULTI_VIEW = "form_multi_view";

    /** 多机位功能编码：必须匹配 {@code sakura_ai_model_func_config.func_code} */
    private static final String FUNC_CODE_IMAGE_MULTI_VIEW = "image_multi_view";

    /** 多机位智能体编码（维护在 sakura_agent.agent_code）。
     *  prompt_content 含 {angle_prompt} / {aspect_ratio} 占位符；
     *  biz_category_code 必须为 image_multi_view，与 sakura_ai_model_func_config.func_code 联动。 */
    private static final String AGENT_CODE_MULTI_CAMERA = "sakura_multi_camera";

    /** 图片大类：仅允许 image 类模型进入多机位池 */
    private static final String MODEL_TYPE_IMAGE = "image";

    /** 图片中间态白名单（轮询用，与现有形态生图保持一致）：含回调优先模式的 WAIT_CALLBACK */
    private static final Set<String> IMAGE_IN_PROGRESS_STATUSES = Set.of(
            "INIT", "PENDING", "QUEUED", "PROCESSING", "WAIT_POLL", "WAIT_CALLBACK");

    /** form image.source_type：多机位独立来源 */
    private static final String FORM_IMAGE_SOURCE_TYPE_MULTI_VIEW = "ai_multi_view";

    /** Redis 防重锁 Key 前缀：与 {@code AssetExtractServiceImpl.FORM_LOCK_PREFIX} 同一命名空间 */
    private static final String FORM_LOCK_PREFIX = "asset:form:lock:";

    /** 图片轮询最大等待时间（秒） */
    private static final long IMAGE_POLL_TIMEOUT_SECONDS = 180L;
    /** 图片轮询间隔（秒） */
    private static final long IMAGE_POLL_INTERVAL_SECONDS = 5L;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 异步线程池：与形态图生图保持相同的执行语义（单条提交走本地线程池，不走 MQ） */
    private final ExecutorService multiViewExecutor = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "form-multi-view-worker");
        t.setDaemon(true);
        return t;
    });
    @Autowired
    private RedisCache redisCache;

    @Autowired
    private ISakuraExtractTaskService extractTaskService;

    @Autowired
    private ISakuraComicProjectService projectService;

    @Autowired
    private ISakuraRolePropSceneService rpsService;

    @Autowired
    private ISakuraRolePropSceneFormService rpsFormService;

    @Autowired
    private ISakuraRolePropSceneFormImageService rpsFormImageService;

    /** 生成图片默认使用状态统一规则。 */
    @Autowired
    private IFormImageSelectionService formImageSelectionService;

    @Autowired
    private ISakuraAiModelService sakuraAiModelService;

    @Autowired
    private ISakuraAiModelFuncConfigService sakuraAiModelFuncConfigService;

    @Autowired
    private IAiModelConfigService aiModelConfigService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.sakura.model.definition.BusinessModelResolver businessModelResolver;

    @Autowired
    private IMediaGenerationService mediaGenerationService;

    @Autowired
    private MediaBillingQuoteService mediaBillingQuoteService;

    /** 媒体URL统一解析器：本站校验 + 相对路径拼完整URL */
    @Autowired
    private com.sakura.common.sakura.oss.util.MediaUrlResolver mediaUrlResolver;

    /** 复用形态生图的提示词加载器：仅用其 substituteVariables；
     *  prompt 文本改从 sakura_agent.prompt_content 加载，不再走 sakura_prompt_lib */
    @Autowired
    private AssetExtractHelper helper;

    /** 智能体加载器：从 sakura_agent 取多机位 prompt_content 与默认模型 */
    @Autowired
    private ISakuraAgentService sakuraAgentService;

    /** 复用 /form/generate-image 的"用户优先、默认兜底"参数注入器，保证多机位与形态生图行为一致 */
    @Autowired
    private AgentDefaultParamsApplier agentDefaultParamsApplier;

    /** 复用任务系统的取消语义：读 Redis cancel flag，与 AssetExtractServiceImpl 使用同一套 key */
    @Autowired
    private IAssetExtractService assetExtractService;

    /** 任务排队 / 多维并发调度服务 */
    @Autowired
    private com.sakura.rps.queue.TaskQueueService taskQueueService;

    /** 复用统一 SSE 管理器：终态事件 complete / error / cancelled 推送 + 终态快照写 Redis */
    @Autowired
    private AssetExtractSseManager sseManager;

    @PreDestroy
    public void shutdown()
    {
        log.info("关闭多机位形态生图线程池...");
        multiViewExecutor.shutdown();
        try
        {
            if (!multiViewExecutor.awaitTermination(30, TimeUnit.SECONDS))
            {
                multiViewExecutor.shutdownNow();
                log.warn("多机位线程池强制关闭");
            }
        }
        catch (InterruptedException e)
        {
            multiViewExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    @Override
    public AssetExtractTaskVO generateMultiViewImage(FormMultiViewImageGenerateRequest request, Long userId)
    {
        validateBasicRequest(request, userId, true);

        MultiViewContext ctx = loadAndValidateOwnership(request.getFormId(), userId);

        SakuraAiModel model = validateMultiViewModel(request.getModelCode());
        AiModelConfigVo modelConfig = aiModelConfigService.selectForBusiness(model.getModelCode(), FUNC_CODE_IMAGE_MULTI_VIEW, null);
        if (Objects.isNull(modelConfig))
        {
            log.error("多机位生图模型配置缺失: modelCode={}", model.getModelCode());
            throw new RuntimeException("模型无效");
        }

        // 比例按模型 capability_json 白名单解析：用户传值未命中直接抛错，未传值走模型默认档
        String aspectRatio = ModelCapabilityResolver.resolveAspectRatio(modelConfig, request.getAspectRatio());
        String finalPrompt = buildMultiViewPrompt(request.getAnglePrompt(), aspectRatio);
        if (StrUtil.isBlank(finalPrompt))
        {
            log.error("多机位生图模板为空: formId={}, modelCode={}", request.getFormId(), request.getModelCode());
            throw new RuntimeException("模板异常");
        }

        String lockKey = FORM_LOCK_PREFIX + TASK_TYPE_FORM_MULTI_VIEW + ":" + request.getFormId();
        Boolean locked = redisCache.redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", 180, TimeUnit.SECONDS);
        if (locked == null || !locked)
        {
            throw new RuntimeException("任务处理中");
        }

        try
        {
            return submitTask(request, userId, ctx, model, finalPrompt, aspectRatio, lockKey);
        }
        catch (RuntimeException e)
        {
            redisCache.deleteObject(lockKey);
            throw e;
        }
    }

    @Override
    public BillingQuoteVO quoteMultiViewImage(FormMultiViewImageGenerateRequest request, Long userId)
    {
        validateBasicRequest(request, userId, false);
        MultiViewContext ctx = loadAndValidateOwnership(request.getFormId(), userId);
        SakuraAiModel model = validateMultiViewModel(request.getModelCode());
        AiModelConfigVo modelConfig = aiModelConfigService.selectForBusiness(model.getModelCode(), FUNC_CODE_IMAGE_MULTI_VIEW, null);
        if (modelConfig == null)
        {
            throw new RuntimeException("模型无效");
        }
        String aspectRatio = ModelCapabilityResolver.resolveAspectRatio(
                modelConfig, request.getAspectRatio());
        String finalPrompt = buildMultiViewPrompt(request.getAnglePrompt(), aspectRatio);
        MediaImageGenerateRequest mediaRequest = buildMediaRequest(userId, ctx.form,
                model.getModelCode(), finalPrompt,
                mediaUrlResolver.toFullUrl(request.getImageUrl()), aspectRatio,
                request.getAnglePrompt(), null);
        return mediaBillingQuoteService.quoteImage(
                "FORM_MULTI_VIEW_IMAGE", mediaRequest, 1);
    }

    /**
     * 多机位任务上下文快照：校验通过后的数据在此打包传给异步线程，避免重复读库。
     */
    private static class MultiViewContext
    {
        SakuraRolePropSceneForm form;
        SakuraRolePropScene asset;
        SakuraComicProject project;
    }
    /**
     * 基础入参校验：非空 + URL 合法性。
     */
    private void validateBasicRequest(FormMultiViewImageGenerateRequest request,
            Long userId, boolean validateRemoteImage)
    {
        if (Objects.isNull(request))
        {
            log.info("多机位生图失败，请求为空");
            throw new RuntimeException("参数异常");
        }
        if (Objects.isNull(request.getFormId()))
        {
            log.info("多机位生图失败，formId为空");
            throw new RuntimeException("形态不存在");
        }
        if (StrUtil.isBlank(request.getImageUrl()))
        {
            log.info("多机位生图失败，参考图URL为空: formId={}", request.getFormId());
            throw new RuntimeException("参考图缺失");
        }
        String url = request.getImageUrl().trim();
        // 仅允许本站资源（相对路径或本站域名完整URL），拒绝站外外链
        if (!mediaUrlResolver.isSiteImageUrl(url))
        {
            log.info("多机位生图失败，参考图非本站资源: formId={}, url={}", request.getFormId(), url);
            throw new RuntimeException("图片无效");
        }
        // 相对路径拼成完整URL后再做远程可达性 + Content-Type 校验
        String fullUrl = mediaUrlResolver.toFullUrl(url);
        // 远程图片 URL 实际可达性 + Content-Type 校验：
        // 非法 / 打不开 / 非图片 → 直接拦在任务创建前，不进任务系统 / 不进计费 / 不调媒体主链路
        if (validateRemoteImage && !ImageUrlValidator.isValidRemoteImageUrl(fullUrl))
        {
            log.info("多机位参考图校验失败: scene=form_multi_view, formId={}, userId={}, imageUrl={}",
                    request.getFormId(), userId, fullUrl);
            throw new RuntimeException("图片无效");
        }
        if (StrUtil.isBlank(request.getAnglePrompt()))
        {
            log.info("多机位生图失败，机位提示词为空: formId={}", request.getFormId());
            throw new RuntimeException("机位缺失");
        }
        if (StrUtil.isBlank(request.getModelCode()))
        {
            log.info("多机位生图失败，modelCode为空: formId={}", request.getFormId());
            throw new RuntimeException("模型不能空");
        }
    }

    /**
     * form 归属校验 + 加载主资产 / 项目。
     * 校验项：form 存在 / 未删除 / 属于当前用户；
     * 主资产存在；项目存在（风格字段用于兜底，缺失不在此阶段拦截，让任务执行阶段决定）。
     */
    private MultiViewContext loadAndValidateOwnership(Long formId, Long userId)
    {
        // 仅查必要字段（新增字段时请同步扩展 select 列表，避免后续业务拿不到值）
        LambdaQueryWrapper<SakuraRolePropSceneForm> formQuery = Wrappers.lambdaQuery();
        formQuery.select(SakuraRolePropSceneForm::getId, SakuraRolePropSceneForm::getAssetId,
                SakuraRolePropSceneForm::getProjectId, SakuraRolePropSceneForm::getEpisodeId,
                SakuraRolePropSceneForm::getUserId, SakuraRolePropSceneForm::getName,
                SakuraRolePropSceneForm::getPromptText, SakuraRolePropSceneForm::getDelFlag);
        formQuery.eq(SakuraRolePropSceneForm::getId, formId);
        SakuraRolePropSceneForm form = rpsFormService.getOne(formQuery, false);
        if (Objects.isNull(form) || !Objects.equals(DEL_FLAG_NORMAL, form.getDelFlag()))
        {
            log.info("多机位生图失败，形态不存在: formId={}", formId);
            throw new RuntimeException("形态不存在");
        }
        if (!Objects.equals(userId, form.getUserId()))
        {
            log.info("多机位生图失败，形态不属于当前用户: formId={}, userId={}", formId, userId);
            throw new RuntimeException("形态不存在");
        }

        SakuraRolePropScene asset = rpsService.getById(form.getAssetId());
        if (Objects.isNull(asset))
        {
            log.info("多机位生图失败，主资产不存在: assetId={}", form.getAssetId());
            throw new RuntimeException("资产不存在");
        }

        SakuraComicProject project = projectService.selectSakuraComicProjectById(asset.getProjectId());
        if (Objects.isNull(project))
        {
            log.info("多机位生图失败，项目不存在: projectId={}", asset.getProjectId());
            throw new RuntimeException("项目不存在");
        }

        MultiViewContext ctx = new MultiViewContext();
        ctx.form = form;
        ctx.asset = asset;
        ctx.project = project;
        return ctx;
    }

    /**
     * 模型可用范围校验：
     *
     *   - {@code sakura_ai_model_func_config} 中存在 {@code func_code=image_multi_view} 且状态正常的配置
     *   - modelCode 对应的 {@code sakura_ai_model} 存在、启用、未删除，并且 {@code model_type=image}
     *   - 该 model id 必须出现在功能配置的 {@code model_ids} 列表里
     *
     */
    private SakuraAiModel validateMultiViewModel(String modelCode)
    {
        return businessModelResolver.resolve(FUNC_CODE_IMAGE_MULTI_VIEW, modelCode, "image");
    }

    /**
     * 解析 {@code sakura_ai_model_func_config.model_ids} JSON 数组字符串。
     * 非法元素（null / 非数字 / 负数 / 0）全部跳过，保证配置脏数据不会拖垮主流程。
     */

    /**
     * 从 sakura_agent 读 sakura_multi_camera 智能体的 prompt_content，注入 {angle_prompt} / {aspect_ratio}。
     * prompt 文本维护在 sakura_agent.prompt_content（agent_code = sakura_multi_camera，
     * biz_category_code = image_multi_view）；后续运营调整"主体一致性规则"等只需在管理端改智能体记录，
     * 不需要发版。sakura_prompt_lib.remark = sakura_multi_camera 已废弃。
     */
    private String buildMultiViewPrompt(String anglePrompt, String aspectRatio)
    {
        // 强校验智能体存在 + biz_category_code = image_multi_view + 启用
        sakuraAgentService.getByAgentCodeAndAssertBizCategory(
                AGENT_CODE_MULTI_CAMERA, FUNC_CODE_IMAGE_MULTI_VIEW);
        // 提示词正文走统一的「文件优先 → 回源 sakura_agent → 回写文件」机制，与角色/场景/道具提取一致
        String template = helper.loadPromptByName(AGENT_CODE_MULTI_CAMERA);
        if (StrUtil.isBlank(template))
        {
            log.error("多机位智能体 prompt_content 为空: agentCode={}", AGENT_CODE_MULTI_CAMERA);
            return null;
        }
        Map<String, String> vars = new HashMap<>();
        vars.put("angle_prompt", StrUtil.blankToDefault(anglePrompt, ""));
        vars.put("aspect_ratio", StrUtil.blankToDefault(aspectRatio, ""));
        return helper.substituteVariables(template, vars);
    }

    /**
     * 多机位任务存档摘要（sakura_media_task.prompt 列内容）。
     * 仅打包动态入参，跳过 sakura_multi_camera 智能体 prompt_content 正文。
     */
    private String buildMultiViewTaskDigest(String anglePrompt, String aspectRatio)
    {
        return new StringBuilder()
                .append("[angle_prompt]\n").append(StrUtil.blankToDefault(anglePrompt, ""))
                .append("\n[aspect_ratio]\n").append(StrUtil.blankToDefault(aspectRatio, ""))
                .toString();
    }
    /**
     * 写任务记录 + 本地线程池异步执行。
     * 锁由调用方获取，异步结束在 {@code finally} 释放；任务入库失败由调用方 catch 释放锁。
     */
    private AssetExtractTaskVO submitTask(FormMultiViewImageGenerateRequest request, Long userId,
                                          MultiViewContext ctx, SakuraAiModel model,
                                          String finalPrompt, String aspectRatio, String lockKey)
    {
        SakuraRolePropSceneForm form = ctx.form;
        SakuraRolePropScene asset = ctx.asset;
        String modelCode = model.getModelCode();
        // DB/前端可能传相对路径，下游 provider 需完整可访问 URL
        String referenceImageUrl = mediaUrlResolver.toFullUrl(request.getImageUrl().trim());
        String anglePrompt = request.getAnglePrompt();

        SakuraExtractTask task = new SakuraExtractTask();
        task.setProjectId(form.getProjectId());
        task.setEpisodeId(form.getEpisodeId());
        task.setUserId(userId);
        task.setTaskType(TASK_TYPE_FORM_MULTI_VIEW);
        task.setModelCode(modelCode);
        // inputSnapshot 留痕：formId / assetId / 参考图 / 机位提示词 / 比例 / 最终提示词摘要
        try
        {
            Map<String, Object> inputMap = new LinkedHashMap<>();
            inputMap.put("formId", form.getId());
            inputMap.put("assetId", asset.getId());
            inputMap.put("assetType", asset.getAssetType());
            inputMap.put("modelCode", modelCode);
            inputMap.put("imageUrl", referenceImageUrl);
            inputMap.put("anglePrompt", anglePrompt);
            inputMap.put("aspectRatio", aspectRatio);
            inputMap.put("agentCode", AGENT_CODE_MULTI_CAMERA);
            inputMap.put("finalPromptSummary", StrUtil.sub(finalPrompt, 0, 200));
            inputMap.put("finalPromptLen", finalPrompt.length());
            task.setInputSnapshot(OBJECT_MAPPER.writeValueAsString(inputMap));
        }
        catch (Exception e)
        {
            task.setInputSnapshot("{\"formId\":" + form.getId() + "}");
        }
        task.setStatus(TASK_STATUS_PENDING);
        task.setTotalCount(0);
        task.setDelFlag(DEL_FLAG_NORMAL);
        task.setCreateTime(DateUtils.getNowDate());
        task.setCreateBy(String.valueOf(userId));
        extractTaskService.save(task);
        Long taskId = task.getId();

        // 入队 + 多维并发调度（LOCAL 派发），名额放行后由本地派发执行器执行此 job。
        Runnable multiViewJob = () ->
        {
            String dispatchToken = taskQueueService.currentLocalDispatchToken(taskId);
            try
            {
                // 取消检查点①：异步启动后先看 Redis cancel flag；用户在 PENDING 阶段取消时会更新任务状态 + 写 flag，
                // 这里再兜一层，避免极端时序下我们 CAS 抢到 PROCESSING 后继续跑。
                if (assetExtractService.isTaskCancelled(taskId))
                {
                    log.info("多机位任务启动前检测到取消, 跳过执行: taskId={}, formId={}", taskId, form.getId());
                    // 严格按 CAS 结果决定是否发终态事件：抢到 CANCELLED 才推 cancelled
                    if (updateTaskCancelled(taskId))
                    {
                        sseManager.sendCancelled(taskId, "用户取消");
                    }
                    return;
                }

                if (!updateTaskStatus(taskId, TASK_STATUS_PROCESSING, null, TASK_STATUS_PENDING))
                {
                    log.warn("多机位任务已被其他线程处理, 跳过: taskId={}", taskId);
                    return;
                }
                // 登记执行租约（重启自愈据租约判活）
                assetExtractService.markTaskProcessing(taskId);

                // 取消检查点②：CAS 进 PROCESSING 后、调生成前再查一次，尽量不触发计费
                if (assetExtractService.isTaskCancelled(taskId))
                {
                    log.info("多机位任务进入PROCESSING后检测到取消, 跳过生成: taskId={}, formId={}", taskId, form.getId());
                    if (updateTaskCancelled(taskId))
                    {
                        sseManager.sendCancelled(taskId, "用户取消");
                    }
                    return;
                }

                String imageUrl = callImageGeneration(taskId, userId, form, asset, modelCode, finalPrompt,
                        referenceImageUrl, aspectRatio, request.getAnglePrompt());
                if (StrUtil.isBlank(imageUrl))
                {
                    log.error("多机位生图失败，图片URL为空: taskId={}, formId={}", taskId, form.getId());
                    throw new RuntimeException("图片生成失败");
                }

                // 到这一步：统一媒体主链路已经成功返回最终图片 URL，计费 / 结算也已进入主链路流程。
                // 不能再把结果直接丢弃 —— 对齐 image_upscale 的"执行后取消仍保留结果"语义：
                //   ① 继续把新图落进 sakura_role_prop_scene_form_image（与正常成功路径完全一致）
                //   ② 根据取消标记决定终态：CANCELLED + resultData 写回  或  SUCCEEDED + resultData 写回
                //   ③ 终态事件 SSE 保持一致：已取消推 cancelled、未取消推 complete，不互相覆盖
                Long imageId = persistMultiViewFormImage(form, asset, imageUrl, finalPrompt,
                        referenceImageUrl, anglePrompt, taskId, userId);

                Map<String, Object> resultMap = new LinkedHashMap<>();
                resultMap.put("formId", form.getId());
                resultMap.put("imageId", imageId);
                resultMap.put("imageUrl", imageUrl);
                resultMap.put("anglePrompt", anglePrompt);
                resultMap.put("aspectRatio", aspectRatio);
                String resultJson = OBJECT_MAPPER.writeValueAsString(resultMap);

                // 取消检查点③（保留结果型）：已落库再判取消 —— 用户已经承担了一次图片生成的计费，必须让结果可查
                if (assetExtractService.isTaskCancelled(taskId))
                {
                    // CAS 抢到 CANCELLED + resultData 才推 cancelled；抢不到说明自己已先写了 SUCCEEDED，留给正常路径处理
                    if (updateTaskCancelledWithResult(taskId, resultJson))
                    {
                        sseManager.sendCancelled(taskId, "用户取消");
                        log.info("多机位生成完成但检测到取消(resultData已保留): taskId={}, formId={}, imageId={}, imageUrl={}",
                                taskId, form.getId(), imageId, imageUrl);
                    }
                    else
                    {
                        log.info("多机位任务已取消分支 CAS 未命中, 放弃发送 cancelled: taskId={}, formId={}", taskId, form.getId());
                    }
                    return;
                }

                // 正常成功路径：CAS PROCESSING → SUCCEEDED + 写 resultData，只有抢到终态才推 complete
                if (updateTaskSuccess(taskId, 1, resultJson))
                {
                    sseManager.sendComplete(taskId, resultMap);
                    log.info("多机位生图完成: taskId={}, formId={}, imageId={}, imageUrl={}",
                            taskId, form.getId(), imageId, imageUrl);
                }
                else
                {
                    log.info("多机位任务成功分支 CAS 未命中, 放弃发送 complete: taskId={}, formId={}", taskId, form.getId());
                }
            }
            catch (TaskCancelledException e)
            {
                // 轮询期间探测到取消：图片尚未真正产出 ossUrl，按 CANCELLED 收尾即可（不丢结果也不伪造结果）
                log.info("多机位任务执行中被取消: taskId={}, formId={}", taskId, form.getId());
                if (updateTaskCancelled(taskId))
                {
                    sseManager.sendCancelled(taskId, "用户取消");
                }
            }
            catch (Exception e)
            {
                log.error("多机位生图任务失败: taskId={}, formId={}", taskId, form.getId(), e);
                // updateTaskFailed 内部 CAS 仅命中 PENDING/PROCESSING，不会覆盖 CANCELLED / SUCCEEDED；
                // 只有 CAS 真正写入 FAILED 时才推 error，避免给已是其他终态的任务再发错终态事件
                com.sakura.common.error.TaskErrorResult errorResult = com.sakura.common.error.ErrorNormalizer.normalize(e);
                if (updateTaskFailed(taskId, errorResult))
                {
                    sseManager.sendError(taskId, errorResult);
                }
            }
            finally
            {
                redisCache.deleteObject(lockKey);
                // 清掉 Redis cancel flag，避免同一 taskId 的标记遗留
                try
                {
                    assetExtractService.clearCancelFlag(taskId, dispatchToken);
                }
                catch (Exception ignore)
                {
                    // 清标记失败仅告警，不影响主流程
                }
                // 释放多维并发名额 + 执行租约（幂等）
                try
                {
                    assetExtractService.releaseTaskSlots(taskId, dispatchToken);
                }
                catch (Exception ignore)
                {
                    // ignore
                }
            }
        };
        // 入队（LOCAL 派发）；CAS 未命中（极少见）则回滚任务 + 释放锁
        boolean enqueued = taskQueueService.submitLocalTask(taskId, form.getProjectId(),
                form.getEpisodeId(), userId, modelCode, TASK_TYPE_FORM_MULTI_VIEW, multiViewJob);
        if (!enqueued)
        {
            log.error("多机位任务入队失败: taskId={}, formId={}", taskId, form.getId());
            updateTaskFailed(taskId, "提交失败");
            redisCache.deleteObject(lockKey);
            throw new RuntimeException("提交失败");
        }

        return AssetExtractTaskVO.builder()
                .taskId(taskId)
                .status(TASK_STATUS_PENDING)
                .build();
    }

    /**
     * 调用统一图片生成主链路（计费 / 预冻结 / 结算 / ossUrl 持久化 由媒体主链路内部处理）。
     */
    private String callImageGeneration(Long taskId, Long userId, SakuraRolePropSceneForm form,
                                        SakuraRolePropScene asset, String modelCode, String finalPrompt,
                                        String referenceImageUrl, String aspectRatio,
                                        String anglePrompt)
    {
        MediaImageGenerateRequest imageRequest = buildMediaRequest(userId, form, modelCode,
                finalPrompt, referenceImageUrl, aspectRatio, anglePrompt, taskId);
        MediaTaskResponse imageResponse = mediaGenerationService.generateImage(imageRequest);
        return resolveImageUrl(taskId, imageResponse);
    }

    private MediaImageGenerateRequest buildMediaRequest(Long userId,
            SakuraRolePropSceneForm form, String modelCode, String finalPrompt,
            String referenceImageUrl, String aspectRatio, String anglePrompt,
            Long bizTaskId)
    {
        MediaImageGenerateRequest imageRequest = new MediaImageGenerateRequest();
        imageRequest.setModelName(modelCode);
        imageRequest.setBusinessFuncCode(FUNC_CODE_IMAGE_MULTI_VIEW);
        // 异步线程里 SecurityContext 已丢失，必须显式带出 userId 触发预冻结 / 结算
        imageRequest.setUserId(userId);
        imageRequest.setPrompt(finalPrompt);
        // sakura_media_task.prompt 列只存动态入参摘要,不存 multi_camera 模板正文
        imageRequest.setTaskPromptDigest(buildMultiViewTaskDigest(anglePrompt, aspectRatio));
        imageRequest.setProjectId(form.getProjectId());
        imageRequest.setEpisodeId(form.getEpisodeId());
        // 多机位单一参考图：直接挂 referenceImageUrl，下游按"图生图"处理
        imageRequest.setReferenceImageUrl(referenceImageUrl);

        Map<String, Object> options = new HashMap<>();
        // 强制单图输出：与形态图生图保持一致，避免下游默认多图放大预扣
        options.put("force_single", true);
        if (StrUtil.isNotBlank(aspectRatio))
        {
            options.put("aspect_ratio", aspectRatio);
        }
        imageRequest.setOptions(options);
        imageRequest.setExpectedImageCount(1);
        // 业务任务ID透传，破除媒体层幂等复用
        imageRequest.setBizTaskId(bizTaskId);
        imageRequest.setBizTaskType(TASK_TYPE_FORM_MULTI_VIEW);

        // 模型默认参数兜底（仅在用户未在 request options 里显式写过时生效）
        AiModelConfigVo defaultModelConfig = aiModelConfigService.selectForBusiness(modelCode, FUNC_CODE_IMAGE_MULTI_VIEW, null);
        if (Objects.isNull(defaultModelConfig))
        {
            log.error("多机位生图模型配置缺失: modelCode={}", modelCode);
            throw new RuntimeException("模型无效");
        }
        // 清晰度档位取模型 capability_json 默认档，不写死档位；
        // 默认档为显式像素时与比例互斥，联合解析后按比例出图，避免默认档挤掉调用方选的比例
        ModelCapabilityResolver.ImageSizeSpec sizeSpec =
                ModelCapabilityResolver.resolveImageSpec(defaultModelConfig, null, aspectRatio);
        imageRequest.setSize(sizeSpec.size());
        // 应用模型能力校验与默认参数。
        AgentModelDefault agentModel = new AgentModelDefault(modelCode);
        agentDefaultParamsApplier.applyToImage(agentModel, imageRequest, defaultModelConfig);

        return imageRequest;
    }

    /**
     * 内部受检信号：异步执行期间探测到用户取消；走独立 catch 以免被当作 FAILED 覆盖任务状态。
     */
    private static final class TaskCancelledException extends RuntimeException
    {
        TaskCancelledException()
        {
            super("用户取消");
        }
    }

    /**
     * 解析图片生成响应，兼容同步 / 异步模型。与 {@code AssetExtractServiceImpl.resolveImageUrl} 逻辑一致，
     * 同时在轮询间隙接入 {@link IAssetExtractService#isTaskCancelled} 以响应用户取消。
     */
    private String resolveImageUrl(Long taskId, MediaTaskResponse imageResponse)
    {
        if (Objects.isNull(imageResponse))
        {
            throw new RuntimeException("图片生成失败");
        }

        if (Objects.equals(TASK_STATUS_SUCCEEDED, imageResponse.getStatus()))
        {
            if (StrUtil.isBlank(imageResponse.getOssUrl()))
            {
                log.error("多机位生图同步成功但 ossUrl 为空: mediaTaskId={}, originUrl={}",
                        imageResponse.getTaskId(), imageResponse.getOriginUrl());
                throw new RuntimeException("存储失败");
            }
            return imageResponse.getOssUrl();
        }

        if (!IMAGE_IN_PROGRESS_STATUSES.contains(imageResponse.getStatus()))
        {
            String errorMsg = imageResponse.getErrorMessage();
            log.error("多机位生图失败: mediaTaskId={}, status={}, error={}",
                    imageResponse.getTaskId(), imageResponse.getStatus(), errorMsg);
            throw new RuntimeException(StrUtil.isNotBlank(errorMsg) ? errorMsg : "图片生成失败");
        }

        Long mediaTaskId = imageResponse.getTaskId();
        if (Objects.isNull(mediaTaskId))
        {
            log.error("多机位异步任务缺少 taskId");
            throw new RuntimeException("图片生成失败");
        }

        long deadline = System.currentTimeMillis() + IMAGE_POLL_TIMEOUT_SECONDS * 1000L;
        while (System.currentTimeMillis() < deadline)
        {
            try
            {
                Thread.sleep(IMAGE_POLL_INTERVAL_SECONDS * 1000L);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new RuntimeException("图片生成被中断");
            }

            // 取消检查点④：异步轮询间隙检查 cancel flag，尽快让取消生效
            if (assetExtractService.isTaskCancelled(taskId))
            {
                log.info("多机位轮询期间检测到取消, 停止等待: taskId={}, mediaTaskId={}", taskId, mediaTaskId);
                throw new TaskCancelledException();
            }

            MediaTaskResponse polled = mediaGenerationService.queryTaskRefresh(mediaTaskId);
            if (Objects.isNull(polled))
            {
                log.error("多机位轮询返回空: mediaTaskId={}", mediaTaskId);
                throw new RuntimeException("图片生成失败");
            }
            if (Objects.equals(TASK_STATUS_SUCCEEDED, polled.getStatus()))
            {
                if (StrUtil.isBlank(polled.getOssUrl()))
                {
                    log.warn("多机位成功但 ossUrl 暂空，等待下一轮持久化: mediaTaskId={}", mediaTaskId);
                    continue;
                }
                return polled.getOssUrl();
            }
            if (TASK_STATUS_FAILED.equals(polled.getStatus()))
            {
                String errorMsg = polled.getErrorMessage();
                log.error("多机位异步生成失败: mediaTaskId={}, error={}", mediaTaskId, errorMsg);
                throw new RuntimeException(StrUtil.isNotBlank(errorMsg) ? errorMsg : "图片生成失败");
            }
        }

        log.error("多机位异步生成超时: mediaTaskId={}, timeout={}s", mediaTaskId, IMAGE_POLL_TIMEOUT_SECONDS);
        throw new RuntimeException("图片生成超时");
    }
    /**
     * 落地一条 {@code sakura_role_prop_scene_form_image}（source_type = ai_multi_view）。
     * 与自动形态图（ai_auto）/ 设定卡（ai_builder）区分，但使用的存储位置 / 写入规则一致，
     * 不新建任何平行表。机位提示词作为 JSON 附带在 reference_images 列，便于追溯。
     */
    private Long persistMultiViewFormImage(SakuraRolePropSceneForm form, SakuraRolePropScene asset,
                                            String imageUrl, String finalPrompt,
                                            String referenceImageUrl, String anglePrompt,
                                            Long taskId, Long userId)
    {
        LambdaQueryWrapper<SakuraRolePropSceneFormImage> existsQuery = Wrappers.lambdaQuery();
        existsQuery.eq(SakuraRolePropSceneFormImage::getFormId, form.getId());
        existsQuery.eq(SakuraRolePropSceneFormImage::getDelFlag, DEL_FLAG_NORMAL);
        existsQuery.select(SakuraRolePropSceneFormImage::getId);
        long existingCount = rpsFormImageService.count(existsQuery);

        //    这样现有读取链路（RpsFormImageBusinessServiceImpl.deserializeReferenceImages）可直接反序列化。
        //    anglePrompt 不再塞入此字段，避免破坏已有 VO / 前端语义；追溯走 input_snapshot + prompt_snapshot。
        String referenceImagesJson = null;
        if (StrUtil.isNotBlank(referenceImageUrl))
        {
            try
            {
                referenceImagesJson = OBJECT_MAPPER.writeValueAsString(List.of(referenceImageUrl));
            }
            catch (Exception e)
            {
                log.warn("多机位参考图序列化失败: formId={}, taskId={}, err={}",
                        form.getId(), taskId, e.getMessage());
            }
        }

        SakuraRolePropSceneFormImage img = new SakuraRolePropSceneFormImage();
        img.setFormId(form.getId());
        img.setAssetId(form.getAssetId());
        img.setProjectId(form.getProjectId());
        img.setEpisodeId(form.getEpisodeId());
        img.setUserId(userId);
        // name 命名：直接复用 form.name（与 /form/generate-image 保持一致；
        // 避免拼接 change_reason 导致 "林深_初始形象_初始形象" 复读）
        String formName = StrUtil.isNotBlank(form.getName()) ? form.getName() : "形态";
        img.setName(formName);
        img.setImageUrl(imageUrl);
        img.setSourceType(FORM_IMAGE_SOURCE_TYPE_MULTI_VIEW);
        img.setDescriptionIndex(0);
        // 最终拼装 prompt 落 prompt_snapshot（机位提示词已体现在此 prompt 内，可追溯）；
        // 原始 anglePrompt 同时留存在 sakura_extract_task.input_snapshot，便于按任务级别精确回溯。
        img.setPromptSnapshot(finalPrompt);
        img.setReferenceImages(referenceImagesJson);
        img.setBatchNo(Objects.nonNull(taskId) ? String.valueOf(taskId) : null);
        img.setSortOrder((int) existingCount);
        img.setImageStatus("completed");
        img.setDelFlag(DEL_FLAG_NORMAL);
        img.setCreateTime(DateUtils.getNowDate());
        img.setCreateBy(String.valueOf(userId));
        formImageSelectionService.saveGeneratedImage(img, userId, true);
        return img.getId();
    }
    /**
     * CAS 更新任务状态：仅在 {@code expectedStatus} 命中时更新为 {@code newStatus}，防并发重复执行。
     * 返回值用于调用方判断"是否自己赢得了处理权"。
     */
    private boolean updateTaskStatus(Long taskId, String newStatus, String errorMessage, String expectedStatus)
    {
        LambdaUpdateWrapper<SakuraExtractTask> update = Wrappers.lambdaUpdate();
        update.eq(SakuraExtractTask::getId, taskId);
        update.eq(SakuraExtractTask::getStatus, expectedStatus);
        update.set(SakuraExtractTask::getStatus, newStatus);
        if (StrUtil.isNotBlank(errorMessage))
        {
            update.set(SakuraExtractTask::getErrorMessage, errorMessage)
                .set(SakuraExtractTask::getErrorDetailJson, TaskErrorSnapshot.fromMessage(errorMessage));
        }
        update.set(SakuraExtractTask::getUpdateTime, DateUtils.getNowDate());
        int rows = extractTaskService.getBaseMapper().update(null, update);
        return rows > 0;
    }

    /**
     * 任务成功：CAS 更新 PROCESSING → SUCCEEDED（与现有形态生图链路语义一致）。
     *
     * @return {@code true} 当前分支赢得终态写入权；{@code false} 终态已被其他分支抢先接管，调用方不得再发 {@code complete} 事件
     */
    private boolean updateTaskSuccess(Long taskId, int totalCount, String resultJson)
    {
        LambdaUpdateWrapper<SakuraExtractTask> update = Wrappers.lambdaUpdate();
        update.eq(SakuraExtractTask::getId, taskId);
        update.eq(SakuraExtractTask::getStatus, TASK_STATUS_PROCESSING);
        update.set(SakuraExtractTask::getStatus, TASK_STATUS_SUCCEEDED);
        update.set(SakuraExtractTask::getTotalCount, totalCount);
        if (StrUtil.isNotBlank(resultJson))
        {
            update.set(SakuraExtractTask::getResultData, resultJson);
        }
        update.set(SakuraExtractTask::getUpdateTime, DateUtils.getNowDate());
        int rows = extractTaskService.getBaseMapper().update(null, update);
        if (rows == 0)
        {
            log.warn("多机位任务成功CAS未命中, 终态已被其他分支接管, 不再发送complete事件: taskId={}", taskId);
            return false;
        }
        return true;
    }

    /**
     * 任务失败：CAS 覆盖 PENDING / PROCESSING → FAILED；若任务已是 CANCELLED / SUCCEEDED 则不动，
     * 避免把用户主动取消的任务误写为 FAILED。
     *
     * @return {@code true} 当前分支成功写入 FAILED；{@code false} 任务已进入其他终态，调用方不得再发 {@code error} 事件
     */
    private boolean updateTaskFailed(Long taskId, String errorMessage)
    {
        return updateTaskFailed(taskId, ErrorNormalizer.normalizeByMessage(errorMessage));
    }

    /** 同一次 CAS 保存失败状态、诊断摘要和安全错误快照。 */
    private boolean updateTaskFailed(Long taskId, com.sakura.common.error.TaskErrorResult errorResult)
    {
        String safeMsg = StrUtil.blankToDefault(errorResult.getRawMessage(), errorResult.getUserMessage());
        LambdaUpdateWrapper<SakuraExtractTask> update = Wrappers.lambdaUpdate();
        update.eq(SakuraExtractTask::getId, taskId);
        update.in(SakuraExtractTask::getStatus, TASK_STATUS_PENDING, TASK_STATUS_PROCESSING);
        update.set(SakuraExtractTask::getStatus, TASK_STATUS_FAILED);
        update.set(SakuraExtractTask::getErrorMessage, StrUtil.sub(MediaTaskPayloadSanitizer.sanitizeForStorage(safeMsg), 0, 255))
                .set(SakuraExtractTask::getErrorDetailJson, TaskErrorSnapshot.write(errorResult));
        update.set(SakuraExtractTask::getUpdateTime, DateUtils.getNowDate());
        int rows = extractTaskService.getBaseMapper().update(null, update);
        if (rows == 0)
        {
            log.warn("多机位任务失败CAS未命中, 终态已被其他分支接管, 不再发送error事件: taskId={}", taskId);
            return false;
        }
        return true;
    }

    /**
     * 用户取消兜底：PROCESSING → CANCELLED。仅在异步执行过程中检测到取消标记时调用。
     * 走 CAS 防止与其他终态更新互相覆盖。
     *
     * @return {@code true} 当前分支成功写入 CANCELLED；{@code false} 任务已进入其他终态，调用方不得再发 {@code cancelled} 事件
     */
    private boolean updateTaskCancelled(Long taskId)
    {
        LambdaUpdateWrapper<SakuraExtractTask> update = Wrappers.lambdaUpdate();
        update.eq(SakuraExtractTask::getId, taskId);
        update.in(SakuraExtractTask::getStatus, TASK_STATUS_PENDING, TASK_STATUS_PROCESSING);
        update.set(SakuraExtractTask::getStatus, TASK_STATUS_CANCELLED);
        update.set(SakuraExtractTask::getErrorMessage, "用户取消")
                .set(SakuraExtractTask::getErrorDetailJson, TaskErrorSnapshot.fromMessage("用户取消"));
        update.set(SakuraExtractTask::getUpdateTime, DateUtils.getNowDate());
        int rows = extractTaskService.getBaseMapper().update(null, update);
        if (rows == 0)
        {
            log.warn("多机位任务取消CAS未命中, 终态已被其他分支接管, 不再发送cancelled事件: taskId={}", taskId);
            return false;
        }
        return true;
    }

    /**
     * 已生成成功后取消：CAS PROCESSING → CANCELLED，但结果 resultData 保留。
     *
     * @return {@code true} 当前分支成功写入 CANCELLED + resultData；{@code false} 任务已进入其他终态（例如自己已先落 SUCCEEDED），
     */
    private boolean updateTaskCancelledWithResult(Long taskId, String resultJson)
    {
        LambdaUpdateWrapper<SakuraExtractTask> update = Wrappers.lambdaUpdate();
        update.eq(SakuraExtractTask::getId, taskId);
        update.in(SakuraExtractTask::getStatus, TASK_STATUS_PENDING, TASK_STATUS_PROCESSING);
        update.set(SakuraExtractTask::getStatus, TASK_STATUS_CANCELLED);
        update.set(SakuraExtractTask::getErrorMessage, "用户取消")
                .set(SakuraExtractTask::getErrorDetailJson, TaskErrorSnapshot.fromMessage("用户取消"));
        if (StrUtil.isNotBlank(resultJson))
        {
            update.set(SakuraExtractTask::getResultData, resultJson);
        }
        update.set(SakuraExtractTask::getUpdateTime, DateUtils.getNowDate());
        int rows = extractTaskService.getBaseMapper().update(null, update);
        if (rows == 0)
        {
            log.warn("多机位任务取消(保留结果)CAS未命中, 终态已被其他分支接管, 不再发送cancelled事件: taskId={}", taskId);
            return false;
        }
        return true;
    }
}
