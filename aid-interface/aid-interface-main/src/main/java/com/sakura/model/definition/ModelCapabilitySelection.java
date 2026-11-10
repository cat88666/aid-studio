package com.sakura.model.definition;

import com.sakura.sakura.domain.model.ModelCapabilityDefinition;
import java.util.List;
import java.util.Objects;

/** 列表投影与实际调用共用的能力选择规则，精确编码优先于历史生成类型。 */
public final class ModelCapabilitySelection {
    private ModelCapabilitySelection() { }

    public static List<ModelCapabilityDefinition> candidates(List<ModelCapabilityDefinition> definitions, String requested) {
        boolean explicit = requested != null && !requested.isBlank();
        // 禁用的精确能力也不允许退回同类型的其他能力。
        boolean exact = explicit && definitions.stream().anyMatch(d -> Objects.equals(d.getCode(), requested));
        return definitions.stream().filter(d -> Boolean.TRUE.equals(d.getEnabled()))
                .filter(d -> explicit
                        ? Objects.equals(d.getCode(), requested) || (!exact && Objects.equals(d.getGenerateMode(), requested))
                        : Boolean.TRUE.equals(d.getDefaultCapability()))
                .toList();
    }
}
