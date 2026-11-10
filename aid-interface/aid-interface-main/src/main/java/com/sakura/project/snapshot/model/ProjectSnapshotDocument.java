package com.sakura.project.snapshot.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sakura.sakura.domain.SakuraAudioAsset;
import com.sakura.sakura.domain.SakuraAudioRecord;
import com.sakura.sakura.domain.SakuraComicProject;
import com.sakura.sakura.domain.SakuraComicEpisode;
import com.sakura.sakura.domain.SakuraComicScript;
import com.sakura.sakura.domain.SakuraEpisodeEditor;
import com.sakura.sakura.domain.SakuraGenRecord;
import com.sakura.sakura.domain.SakuraProjectGenConfig;
import com.sakura.sakura.domain.SakuraRolePropScene;
import com.sakura.sakura.domain.SakuraRolePropSceneForm;
import com.sakura.sakura.domain.SakuraRolePropSceneFormImage;
import com.sakura.sakura.domain.SakuraRoleVoiceBinding;
import com.sakura.sakura.domain.SakuraScenePlot;
import com.sakura.sakura.domain.SakuraStoryboard;
import com.sakura.rps.voice.vo.RoleVoiceBindingVO;

import lombok.Data;

/** 快照内部文档；接口层会转换为脱敏的节点/连线结构。 */
@Data
public class ProjectSnapshotDocument
{
    private Integer schemaVersion;
    private SakuraComicProject project;
    private List<SakuraComicEpisode> episodes = new ArrayList<>();
    private List<SakuraComicScript> scripts = new ArrayList<>();
    private List<SakuraRolePropScene> assets = new ArrayList<>();
    private List<SakuraRolePropSceneForm> forms = new ArrayList<>();
    private List<SakuraRolePropSceneFormImage> formImages = new ArrayList<>();
    private List<SakuraScenePlot> scenePlots = new ArrayList<>();
    private List<SakuraStoryboard> storyboards = new ArrayList<>();
    private List<SakuraGenRecord> generationRecords = new ArrayList<>();
    private List<SakuraAudioRecord> audioRecords = new ArrayList<>();
    /** 发布时冻结的对口型状态，避免预览阶段回查实时任务表。 */
    private Map<Long, String> audioLipSyncStatuses = new LinkedHashMap<>();
    private List<SakuraAudioAsset> audioAssets = new ArrayList<>();
    private List<SakuraRoleVoiceBinding> voiceBindings = new ArrayList<>();
    /** 发布时按正常角色资产接口批量固化的音色展示契约；旧快照为空时由绑定冗余字段安全降级。 */
    private List<RoleVoiceBindingVO> voiceBindingViews = new ArrayList<>();
    private List<SakuraProjectGenConfig> generationConfigs = new ArrayList<>();
    private List<SakuraEpisodeEditor> editors = new ArrayList<>();
}
