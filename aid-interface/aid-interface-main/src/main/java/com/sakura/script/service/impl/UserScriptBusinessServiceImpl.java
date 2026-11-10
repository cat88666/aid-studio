package com.sakura.script.service.impl;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sakura.sakura.domain.SakuraComicEpisode;
import com.sakura.sakura.domain.SakuraComicProject;
import com.sakura.sakura.domain.SakuraComicScript;
import com.sakura.sakura.service.ISakuraComicEpisodeService;
import com.sakura.sakura.service.ISakuraComicProjectService;
import com.sakura.sakura.service.ISakuraComicScriptService;
import com.sakura.common.exception.ServiceException;
import com.sakura.common.page.SafePageUtils;
import com.sakura.common.utils.DateUtils;
import com.sakura.enums.ProjectTypeEnum;
import com.sakura.enums.ScriptStatusEnum;
import com.sakura.project.service.IProjectContentGuardService;
import com.sakura.script.dto.UserScriptAutoSaveRequest;
import com.sakura.script.dto.UserScriptQueryRequest;
import com.sakura.script.dto.UserScriptSaveRequest;
import com.sakura.script.dto.UserScriptUploadRequest;
import com.sakura.script.helper.ScriptFileExtractor;
import com.sakura.script.helper.UserScriptContentHash;
import com.sakura.script.service.IUserScriptBusinessService;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户剧本业务Service实现
 *
 * @author 视觉AID
 */
@Slf4j
@Service
public class UserScriptBusinessServiceImpl implements IUserScriptBusinessService
{
    /**
     * HTML 标签检测正则。
     * 剧本原文 {@code original_text} 仅允许 Markdown，不允许 HTML 标签。
     * 该正则只匹配"真正的 HTML 标签形态"：{@code <} + 可选 {@code /} + 字母开头标签名（+ 任意属性） + {@code >}，
     * 以及自闭合写法（如 {@code <br/>}）。
     * 不会误伤 Markdown 中合法的 {@code <}：例如 {@code a < b}、{@code 1<2}、箭头 {@code ->}，
     * 因为这些 {@code <} 后面跟的是空格 / 数字 / 非字母，匹配不到 {@code <字母} 的标签开头结构。
     */
    private static final Pattern HTML_TAG_PATTERN =
            Pattern.compile("</?[a-zA-Z][a-zA-Z0-9]*(\\s[^<>]*)?/?>");

    /**
     * 换行标签匹配（上传剧本中唯一允许保留的 HTML 标签）。
     * 匹配 {@code <br>}、{@code <br/>}、{@code <br />}、{@code </br>}，大小写不敏感。
     */
    private static final Pattern BR_TAG_PATTERN = Pattern.compile("(?i)</?br\\s*/?>");

    /** 上传剧本允许的文件扩展名白名单（仅纯文本 .txt，图片/Word 等一律拒绝） */
    private static final Set<String> ALLOWED_UPLOAD_EXTENSIONS =
            Set.of(ScriptFileExtractor.EXT_TXT);

    /** 电影剧本字数上限：1 万字 */
    private static final int MAX_WORD_MOVIE = 10000;

    /** 剧集-单集（指定集数上传）字数上限：1 万字 */
    private static final int MAX_WORD_EPISODE_SINGLE = 10000;

    /** 剧集-整篇（未指定集数、待分集导入）字数上限：10 万字 */
    private static final int MAX_WORD_SERIES = 100000;

    /** 上传剧本文件大小上限（字节）：10 万字 UTF-8 约 300KB，10MB 富余；超限先拒绝再解析，防超大文件整包进堆 */
    private static final long MAX_UPLOAD_FILE_BYTES = 10L * 1024L * 1024L;
    @Autowired
    private ISakuraComicScriptService sakuraComicScriptService;

    @Autowired
    private ISakuraComicProjectService sakuraComicProjectService;

    @Autowired
    private ISakuraComicEpisodeService sakuraComicEpisodeService;

    @Autowired
    private IProjectContentGuardService projectContentGuardService;

    /**
     * 校验项目归属并返回项目
     */
    private SakuraComicProject getAndCheckProject(Long projectId, Long userId)
    {
        return getAndCheckProject(projectId, userId, false);
    }

    /**
     * 校验项目归属，写入流程可同时锁定项目行。
     */
    private SakuraComicProject getAndCheckProject(Long projectId, Long userId, boolean forUpdate)
    {
        LambdaQueryWrapper<SakuraComicProject> wrapper = Wrappers.<SakuraComicProject>lambdaQuery()
                .eq(SakuraComicProject::getId, projectId)
                .eq(SakuraComicProject::getUserId, userId)
                .eq(SakuraComicProject::getDelFlag, "0");
        if (forUpdate) {
            wrapper.last("FOR UPDATE");
        }
        SakuraComicProject project = sakuraComicProjectService.getOne(wrapper);
        if (project == null) {
            log.info("剧本操作项目缺失或越权, projectId={}, userId={}", projectId, userId);
            throw new ServiceException("项目不存在");
        }
        if (forUpdate) {
            projectContentGuardService.assertProjectEditable(project);
        }
        return project;
    }

    /**
     * 校验剧集归属
     */
    private void checkEpisodeBelongsToProject(Long episodeId, Long projectId)
    {
        if (episodeId == null || episodeId == 0L) {
            return;
        }
        SakuraComicEpisode episode = sakuraComicEpisodeService.getOne(
                Wrappers.<SakuraComicEpisode>lambdaQuery()
                        .eq(SakuraComicEpisode::getId, episodeId)
                        .eq(SakuraComicEpisode::getProjectId, projectId)
                        .eq(SakuraComicEpisode::getDelFlag, "0"));
        if (episode == null) {
            throw new RuntimeException("剧集不存在或不属于该项目");
        }
    }

    /**
     * 校验请求参数（项目归属 + 电影/剧集类型校验）
     */
    private SakuraComicProject validateRequest(Long projectId, Long episodeId, String originalText,
            Long userId, boolean lockProject)
    {
        SakuraComicProject project = getAndCheckProject(projectId, userId, lockProject);
        boolean isMovie = ProjectTypeEnum.MOVIE.getValue().equals(project.getProjectType());
        if (isMovie && episodeId != 0L) {
            throw new RuntimeException("电影类型项目集数ID应为0");
        }
        if (!isMovie) {
            if (episodeId == 0L) {
                throw new RuntimeException("剧集类型项目必须指定集数ID");
            }
            checkEpisodeBelongsToProject(episodeId, projectId);
        }
        // 剧本原文与上传口径一致：仅允许 <br> 换行标签，其它 HTML 标签一律拒绝
        // （上传/分集导入允许 <br> 入库，保存必须兼容，否则导入内容一经编辑即无法保存）
        assertOnlyLineBreakHtml(originalText);
        // 字数上限与上传口径一致：保存目标恒为单集/电影整篇，超限拒绝（防绕过上传限制写入超大文本）
        int wordCount = countWord(originalText);
        if (wordCount > MAX_WORD_EPISODE_SINGLE) {
            log.info("剧本保存超字数, projectId={}, episodeId={}, wordCount={}, max={}",
                    projectId, episodeId, wordCount, MAX_WORD_EPISODE_SINGLE);
            throw new ServiceException("剧本超1万字");
        }
        return project;
    }

    /**
     * 查询该集当前使用中的剧本
     */
    private SakuraComicScript getCurrentScript(Long projectId, Long episodeId, Long userId, boolean forUpdate)
    {
        LambdaQueryWrapper<SakuraComicScript> wrapper = Wrappers.<SakuraComicScript>lambdaQuery()
                .select(SakuraComicScript::getId, SakuraComicScript::getProjectId,
                        SakuraComicScript::getEpisodeId, SakuraComicScript::getOriginalText,
                        SakuraComicScript::getIsExtracted, SakuraComicScript::getComicVersion,
                        SakuraComicScript::getStatus, SakuraComicScript::getCreateTime,
                        SakuraComicScript::getUpdateTime)
                .eq(SakuraComicScript::getProjectId, projectId)
                .eq(SakuraComicScript::getEpisodeId, episodeId)
                .eq(SakuraComicScript::getUserId, userId)
                .eq(SakuraComicScript::getStatus, ScriptStatusEnum.IN_USE.getValue())
                .eq(SakuraComicScript::getDelFlag, "0")
                .orderByDesc(SakuraComicScript::getId);
        wrapper.last(forUpdate ? "LIMIT 1 FOR UPDATE" : "LIMIT 1");
        return sakuraComicScriptService.getOne(wrapper);
    }

    /**
     * 通过剧本ID校验归属，返回剧本
     */
    private SakuraComicScript getAndCheckScript(Long id, Long userId)
    {
        SakuraComicScript script = sakuraComicScriptService.getOne(
                Wrappers.<SakuraComicScript>lambdaQuery()
                        .select(SakuraComicScript::getId, SakuraComicScript::getProjectId,
                                SakuraComicScript::getStatus)
                        .eq(SakuraComicScript::getId, id)
                        .eq(SakuraComicScript::getUserId, userId)
                        .eq(SakuraComicScript::getDelFlag, "0"));
        if (script == null) {
            throw new RuntimeException("剧本不存在");
        }
        getAndCheckProject(script.getProjectId(), userId);
        return script;
    }

    /**
     * 校验客户端编辑基线，避免不同页面静默覆盖。
     */
    private void assertBaselineMatches(SakuraComicScript currentScript, Long baseScriptId, String baseContentHash,
            Long projectId, Long episodeId, Long userId)
    {
        boolean hasBaseScriptId = Objects.nonNull(baseScriptId);
        boolean hasBaseContentHash = StrUtil.isNotBlank(baseContentHash);
        if (!hasBaseScriptId && !hasBaseContentHash) {
            return;
        }
        if (!hasBaseScriptId || !hasBaseContentHash) {
            log.info("剧本保存基线参数不完整, projectId={}, episodeId={}, userId={}",
                    projectId, episodeId, userId);
            throw new ServiceException("基线参数有误", 400);
        }

        boolean matched;
        if (Objects.isNull(currentScript)) {
            matched = Objects.equals(0L, baseScriptId)
                    && UserScriptContentHash.calculate("").equalsIgnoreCase(baseContentHash);
        } else {
            matched = Objects.equals(currentScript.getId(), baseScriptId)
                    && UserScriptContentHash.calculate(currentScript.getOriginalText())
                            .equalsIgnoreCase(baseContentHash);
        }
        if (!matched) {
            log.info("剧本保存基线冲突, projectId={}, episodeId={}, userId={}, baseScriptId={}, currentScriptId={}",
                    projectId, episodeId, userId, baseScriptId,
                    Objects.isNull(currentScript) ? null : currentScript.getId());
            throw new ServiceException("剧本内容冲突", 409);
        }
    }

    @Override
    public List<SakuraComicScript> selectUserScriptList(UserScriptQueryRequest request, Long userId)
    {
        getAndCheckProject(request.getProjectId(), userId);

        LambdaQueryWrapper<SakuraComicScript> wrapper = Wrappers.lambdaQuery();
        wrapper.select(SakuraComicScript::getId, SakuraComicScript::getProjectId,
                SakuraComicScript::getEpisodeId, SakuraComicScript::getOriginalText,
                SakuraComicScript::getIsExtracted, SakuraComicScript::getComicVersion,
                SakuraComicScript::getStatus, SakuraComicScript::getCreateTime,
                SakuraComicScript::getUpdateTime);
        wrapper.eq(SakuraComicScript::getProjectId, request.getProjectId());
        wrapper.eq(SakuraComicScript::getUserId, userId);
        wrapper.eq(SakuraComicScript::getDelFlag, "0");
        if (request.getEpisodeId() != null) {
            wrapper.eq(SakuraComicScript::getEpisodeId, request.getEpisodeId());
        }
        if (request.getStatus() != null) {
            wrapper.eq(SakuraComicScript::getStatus, request.getStatus());
        }
        wrapper.orderByDesc(SakuraComicScript::getComicVersion);
        // 分页紧邻列表查询开启（归属校验查询在前会消费分页拦截，导致列表退化为全量）
        SafePageUtils.startClampedPage();
        return sakuraComicScriptService.list(wrapper);
    }

    @Override
    public SakuraComicScript selectUserScriptByProject(Long projectId, Long episodeId, Long userId)
    {
        // 校验项目归属
        getAndCheckProject(projectId, userId);

        // 校验剧集归属（剧集类型项目）
        if (episodeId != null && episodeId != 0L) {
            checkEpisodeBelongsToProject(episodeId, projectId);
        }

        // 优先查询当前使用中的剧本(status=1)
        SakuraComicScript script = sakuraComicScriptService.getOne(
                Wrappers.<SakuraComicScript>lambdaQuery()
                        .select(SakuraComicScript::getId, SakuraComicScript::getProjectId,
                                SakuraComicScript::getEpisodeId, SakuraComicScript::getOriginalText,
                                SakuraComicScript::getIsExtracted, SakuraComicScript::getComicVersion,
                                SakuraComicScript::getStatus, SakuraComicScript::getCreateTime,
                                SakuraComicScript::getUpdateTime)
                        .eq(SakuraComicScript::getProjectId, projectId)
                        .eq(SakuraComicScript::getEpisodeId, episodeId)
                        .eq(SakuraComicScript::getUserId, userId)
                        .eq(SakuraComicScript::getStatus, ScriptStatusEnum.IN_USE.getValue())
                        .eq(SakuraComicScript::getDelFlag, "0"));

        // 如果不存在使用中的剧本，则返回草稿版本(status=0)
        if (script == null) {
            script = sakuraComicScriptService.getOne(
                    Wrappers.<SakuraComicScript>lambdaQuery()
                            .select(SakuraComicScript::getId, SakuraComicScript::getProjectId,
                                    SakuraComicScript::getEpisodeId, SakuraComicScript::getOriginalText,
                                    SakuraComicScript::getIsExtracted, SakuraComicScript::getComicVersion,
                                    SakuraComicScript::getStatus, SakuraComicScript::getCreateTime,
                                    SakuraComicScript::getUpdateTime)
                            .eq(SakuraComicScript::getProjectId, projectId)
                            .eq(SakuraComicScript::getEpisodeId, episodeId)
                            .eq(SakuraComicScript::getUserId, userId)
                            .eq(SakuraComicScript::getStatus, ScriptStatusEnum.DRAFT.getValue())
                            .eq(SakuraComicScript::getDelFlag, "0")
                            .orderByDesc(SakuraComicScript::getComicVersion)
                            .last("LIMIT 1"));
        }

        // 都不存在则返回null
        return script;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SakuraComicScript saveUserScript(UserScriptSaveRequest request, Long userId)
    {
        validateRequest(request.getProjectId(), request.getEpisodeId(), request.getOriginalText(), userId, true);

        // 创建 / 版本+1 统一入库
        return doSaveAsNewVersion(request.getProjectId(), request.getEpisodeId(), userId,
                request.getOriginalText(), request.getBaseScriptId(), request.getBaseContentHash());
    }

    /**
     * 核心入库逻辑：无当前使用中剧本则创建版本1，已有则将其转历史并创建版本+1。
     *
     * @param projectId    项目ID
     * @param episodeId    集数ID（电影为0）
     * @param userId       当前用户ID
     * @param originalText 剧本原文
     * @return 新写入的剧本记录
     */
    private SakuraComicScript doSaveAsNewVersion(Long projectId, Long episodeId, Long userId, String originalText,
            Long baseScriptId, String baseContentHash)
    {
        // 查询该集当前使用中的剧本
        SakuraComicScript currentScript = getCurrentScript(projectId, episodeId, userId, true);
        assertBaselineMatches(currentScript, baseScriptId, baseContentHash, projectId, episodeId, userId);

        if (currentScript == null) {
            SakuraComicScript script = new SakuraComicScript();
            script.setProjectId(projectId);
            script.setEpisodeId(episodeId);
            script.setUserId(userId);
            script.setOriginalText(originalText);
            script.setComicVersion(1);
            script.setStatus(ScriptStatusEnum.IN_USE.getValue());
            script.setIsExtracted(0);
            script.setDelFlag("0");
            script.setCreateBy(String.valueOf(userId));
            script.setCreateTime(DateUtils.getNowDate());
            sakuraComicScriptService.save(script);
            return script;
        } else {
            // 将当前版本标记为历史版本
            LambdaUpdateWrapper<SakuraComicScript> updateWrapper = Wrappers.lambdaUpdate();
            updateWrapper.eq(SakuraComicScript::getId, currentScript.getId());
            updateWrapper.set(SakuraComicScript::getStatus, ScriptStatusEnum.HISTORY.getValue());
            updateWrapper.set(SakuraComicScript::getUpdateBy, String.valueOf(userId));
            updateWrapper.set(SakuraComicScript::getUpdateTime, DateUtils.getNowDate());
            sakuraComicScriptService.update(updateWrapper);

            // 创建新版本
            SakuraComicScript newScript = new SakuraComicScript();
            newScript.setProjectId(currentScript.getProjectId());
            newScript.setEpisodeId(currentScript.getEpisodeId());
            newScript.setUserId(userId);
            newScript.setOriginalText(originalText);
            newScript.setComicVersion(currentScript.getComicVersion() + 1);
            newScript.setStatus(ScriptStatusEnum.IN_USE.getValue());
            newScript.setIsExtracted(currentScript.getIsExtracted());
            newScript.setDelFlag("0");
            newScript.setCreateBy(String.valueOf(userId));
            newScript.setCreateTime(DateUtils.getNowDate());
            sakuraComicScriptService.save(newScript);
            return newScript;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SakuraComicScript autoSaveUserScript(UserScriptAutoSaveRequest request, Long userId)
    {
        validateRequest(request.getProjectId(), request.getEpisodeId(), request.getOriginalText(), userId, true);

        // 查询该集当前使用中的剧本
        SakuraComicScript currentScript = getCurrentScript(request.getProjectId(), request.getEpisodeId(), userId, true);
        assertBaselineMatches(currentScript, request.getBaseScriptId(), request.getBaseContentHash(),
                request.getProjectId(), request.getEpisodeId(), userId);

        if (Objects.isNull(currentScript))
        {
            SakuraComicScript script = new SakuraComicScript();
            script.setProjectId(request.getProjectId());
            script.setEpisodeId(request.getEpisodeId());
            script.setUserId(userId);
            script.setOriginalText(request.getOriginalText());
            script.setComicVersion(1);
            script.setStatus(ScriptStatusEnum.IN_USE.getValue());
            script.setIsExtracted(0);
            script.setDelFlag("0");
            script.setCreateBy(String.valueOf(userId));
            script.setCreateTime(DateUtils.getNowDate());
            sakuraComicScriptService.save(script);
            return script;
        }
        LambdaUpdateWrapper<SakuraComicScript> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.eq(SakuraComicScript::getId, currentScript.getId());
        updateWrapper.set(SakuraComicScript::getOriginalText, request.getOriginalText());
        updateWrapper.set(SakuraComicScript::getUpdateBy, String.valueOf(userId));
        updateWrapper.set(SakuraComicScript::getUpdateTime, DateUtils.getNowDate());
        sakuraComicScriptService.update(updateWrapper);

        currentScript.setOriginalText(request.getOriginalText());
        currentScript.setUpdateTime(DateUtils.getNowDate());
        return currentScript;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SakuraComicScript uploadUserScript(UserScriptUploadRequest request, Long userId)
    {
        Long projectId = request.getProjectId();
        Long episodeId = request.getEpisodeId();
        MultipartFile file = request.getFile();

        SakuraComicProject project = getAndCheckProject(projectId, userId);

        if (Objects.isNull(file) || file.isEmpty()) {
            log.error("剧本上传失败：文件为空, projectId={}, userId={}", projectId, userId);
            throw new ServiceException("请选择文件");
        }

        String ext = FileUtil.extName(file.getOriginalFilename());
        ext = StrUtil.isBlank(ext) ? "" : ext.toLowerCase();
        if (!ALLOWED_UPLOAD_EXTENSIONS.contains(ext)) {
            log.error("剧本上传失败：不支持的文件类型, fileName={}", file.getOriginalFilename());
            throw new ServiceException("格式不支持");
        }

        // 解析前按文件大小快速拒绝：超大文件不进堆内存（字数校验在解析后，无法拦截读取本身）
        if (file.getSize() > MAX_UPLOAD_FILE_BYTES) {
            log.error("剧本上传失败：文件过大, fileName={}, size={}B, max={}B",
                    file.getOriginalFilename(), file.getSize(), MAX_UPLOAD_FILE_BYTES);
            throw new ServiceException("文件过大");
        }

        String originalText = ScriptFileExtractor.extractText(file, ext);
        if (StrUtil.isBlank(originalText)) {
            log.error("剧本上传失败：解析后内容为空, fileName={}", file.getOriginalFilename());
            throw new ServiceException("内容不能为空");
        }

        assertOnlyLineBreakHtml(originalText);

        boolean isMovie = ProjectTypeEnum.MOVIE.getValue().equals(project.getProjectType());
        int wordCount = countWord(originalText);

        if (isMovie) {
            // 电影：整篇限制 1 万字，集数ID固定为 0
            if (wordCount > MAX_WORD_MOVIE) {
                log.error("剧本上传失败：电影超字数, wordCount={}, max={}", wordCount, MAX_WORD_MOVIE);
                throw new ServiceException("剧本超1万字");
            }
            getAndCheckProject(projectId, userId, true);
            return doSaveAsNewVersion(projectId, 0L, userId, originalText, null, null);
        }
        boolean singleEpisode = Objects.nonNull(episodeId) && episodeId != 0L;
        if (singleEpisode) {
            // 指定了剧集ID：按"单集上传"处理，单集限制 1 万字，直接落到该集
            checkEpisodeBelongsToProject(episodeId, projectId);
            if (wordCount > MAX_WORD_EPISODE_SINGLE) {
                log.error("剧本上传失败：剧集单集超字数, episodeId={}, wordCount={}, max={}",
                        episodeId, wordCount, MAX_WORD_EPISODE_SINGLE);
                throw new ServiceException("单集超1万字");
            }
            getAndCheckProject(projectId, userId, true);
            return doSaveAsNewVersion(projectId, episodeId, userId, originalText, null, null);
        }

        // 未指定剧集ID：按"整篇导入"处理，整篇限制 10 万字
        if (wordCount > MAX_WORD_SERIES) {
            log.error("剧本上传失败：剧集整篇超字数, wordCount={}, max={}", wordCount, MAX_WORD_SERIES);
            throw new ServiceException("剧本超10万");
        }
        // 剧集整篇导入统一走「剧本分集」链路：前端读取文件文本后调
        // /api/user/script/split/preview 预览分集，确认后调 /split/confirm 自动建集入库
        log.info("剧本上传：剧集整篇请走分集导入接口, projectId={}, userId={}", projectId, userId);
        throw new ServiceException("请用分集导入");
    }

    /**
     * 校验剧本原文：除换行符 {@code <br>} 外不允许出现任何 HTML 标签。
     * 先移除唯一允许的 {@code <br>} 标签，再用 {@link #HTML_TAG_PATTERN} 检测是否仍存在其它标签；
     * 命中则按规范先 log.error 再抛短文案异常。仅用于校验，不修改入库内容（{@code <br>} 原样保留）。
     *
     * @param text 剧本原文
     */
    private void assertOnlyLineBreakHtml(String text)
    {
        if (StrUtil.isBlank(text)) {
            return;
        }
        // 移除允许的换行标签后再检测，避免误伤 <br>
        String stripped = BR_TAG_PATTERN.matcher(text).replaceAll("");
        if (HTML_TAG_PATTERN.matcher(stripped).find()) {
            log.error("剧本上传失败：含非法 HTML 标签, contentLen={}", text.length());
            throw new ServiceException("标签格式有误");
        }
    }

    /**
     * 统计剧本字数（中文按字计）。
     * 剔除换行标签 {@code <br>} 与所有空白字符后按字符数计，作为字数上限的判定依据。
     *
     * @param text 剧本原文
     * @return 字数
     */
    private int countWord(String text)
    {
        if (StrUtil.isBlank(text)) {
            return 0;
        }
        // 去掉换行标签与全部空白（换行/空格/制表符），仅统计有效文字
        String pure = BR_TAG_PATTERN.matcher(text).replaceAll("");
        pure = pure.replaceAll("\\s+", "");
        return pure.length();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int softDeleteUserScriptById(Long id, Long userId)
    {
        SakuraComicScript snapshot = getAndCheckScript(id, userId);
        getAndCheckProject(snapshot.getProjectId(), userId, true);

        SakuraComicScript script = sakuraComicScriptService.getOne(
                Wrappers.<SakuraComicScript>lambdaQuery()
                        .select(SakuraComicScript::getId, SakuraComicScript::getProjectId,
                                SakuraComicScript::getStatus)
                        .eq(SakuraComicScript::getId, id)
                        .eq(SakuraComicScript::getUserId, userId)
                        .eq(SakuraComicScript::getDelFlag, "0")
                        .last("FOR UPDATE"));
        if (Objects.isNull(script)) {
            log.info("剧本删除目标不存在, scriptId={}, userId={}", id, userId);
            throw new ServiceException("剧本不存在");
        }
        if (!Objects.equals(ScriptStatusEnum.HISTORY.getValue(), script.getStatus())) {
            log.info("剧本删除目标不是历史版本, scriptId={}, status={}, userId={}",
                    id, script.getStatus(), userId);
            throw new ServiceException("仅历史可删除");
        }

        LambdaUpdateWrapper<SakuraComicScript> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.eq(SakuraComicScript::getId, id);
        updateWrapper.eq(SakuraComicScript::getUserId, userId);
        updateWrapper.eq(SakuraComicScript::getStatus, ScriptStatusEnum.HISTORY.getValue());
        updateWrapper.eq(SakuraComicScript::getDelFlag, "0");
        updateWrapper.set(SakuraComicScript::getDelFlag, "1");
        updateWrapper.set(SakuraComicScript::getUpdateBy, String.valueOf(userId));
        updateWrapper.set(SakuraComicScript::getUpdateTime, DateUtils.getNowDate());
        return sakuraComicScriptService.update(updateWrapper) ? 1 : 0;
    }
}
