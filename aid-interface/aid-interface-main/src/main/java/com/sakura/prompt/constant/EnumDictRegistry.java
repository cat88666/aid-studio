package com.sakura.prompt.constant;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.sakura.enums.AspectRatioEnum;
import com.sakura.enums.AssetSourceTypeEnum;
import com.sakura.enums.AssetTypeEnum;
import com.sakura.enums.AudioSourceEnum;
import com.sakura.enums.CommonEnableStatusEnum;
import com.sakura.enums.CommonYesNoEnum;
import com.sakura.enums.CreationModeEnum;
import com.sakura.enums.CreationStepEnum;
import com.sakura.enums.EpisodeStatusEnum;
import com.sakura.enums.GenModeEnum;
import com.sakura.enums.GenResultTargetEnum;
import com.sakura.enums.GenTypeEnum;
import com.sakura.enums.GenerateModeEnum;
import com.sakura.enums.MediaTypeEnum;
import com.sakura.enums.ModelTypeEnum;
import com.sakura.enums.ProductTypeEnum;
import com.sakura.enums.ProjectStatusEnum;
import com.sakura.enums.ProjectTypeEnum;
import com.sakura.enums.PromptTypeEnum;
import com.sakura.enums.ScriptStatusEnum;
import com.sakura.enums.ScriptTypeEnum;
import com.sakura.enums.StoryboardShotDensityEnum;
import com.sakura.enums.VideoStyleTypeEnum;

/**
 * 枚举字典白名单注册表
 * 仅注册在此白名单内的枚举类型，允许通过 /api/user/dict/enum/list 暴露给 C 端。
 * 不允许任意类名透传；不允许大小写混用（必须精确匹配类名 SimpleName）。
 *
 * @author 视觉AID
 */
public final class EnumDictRegistry {

    private static final Map<String, Entry> REGISTRY = new LinkedHashMap<>();

    static {
        register(PromptTypeEnum.class, "提示词分类");
        register(ModelTypeEnum.class, "AI模型分类");
        register(GenerateModeEnum.class, "AI模型生成模式");
        register(GenTypeEnum.class, "生成类型");
        register(CreationModeEnum.class, "创作模式");
        register(ProjectStatusEnum.class, "项目状态");
        register(EpisodeStatusEnum.class, "剧集状态");
        register(ScriptStatusEnum.class, "脚本状态");
        register(ScriptTypeEnum.class, "脚本类型");
        register(VideoStyleTypeEnum.class, "视频风格类型");
        register(StoryboardShotDensityEnum.class, "分镜镜头密度");
        register(GenModeEnum.class, "生成模式");
        register(ProjectTypeEnum.class, "项目类型");
        register(AssetSourceTypeEnum.class, "素材来源类型");
        register(AssetTypeEnum.class, "素材类型");
        register(AudioSourceEnum.class, "音频来源");
        register(MediaTypeEnum.class, "媒体类型");
        register(ProductTypeEnum.class, "产品类型");
        register(AspectRatioEnum.class, "宽高比");
        register(CreationStepEnum.class, "创作步骤");
        register(GenResultTargetEnum.class, "生成结果目标");
        register(CommonEnableStatusEnum.class, "通用启用状态");
        register(CommonYesNoEnum.class, "通用是否");
    }

    private EnumDictRegistry() {
    }

    private static void register(Class<? extends Enum<?>> clazz, String desc) {
        REGISTRY.put(clazz.getSimpleName(), new Entry(clazz, desc));
    }

    /** 是否存在该枚举类型（严格大小写） */
    public static boolean contains(String enumType) {
        return enumType != null && REGISTRY.containsKey(enumType);
    }

    /** 获取枚举项 */
    public static Entry get(String enumType) {
        return enumType == null ? null : REGISTRY.get(enumType);
    }

    /** 所有已注册类型名称 */
    public static Map<String, Entry> all() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    /** 注册项 */
    public static final class Entry {
        private final Class<? extends Enum<?>> clazz;
        private final String desc;

        public Entry(Class<? extends Enum<?>> clazz, String desc) {
            this.clazz = clazz;
            this.desc = desc;
        }

        public Class<? extends Enum<?>> getClazz() {
            return clazz;
        }

        public String getDesc() {
            return desc;
        }
    }
}
