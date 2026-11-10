package com.sakura.prompt.controller;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sakura.common.core.controller.BaseController;
import com.sakura.common.core.domain.AjaxResult;
import com.sakura.prompt.dto.EnumDictListRequest;
import com.sakura.prompt.service.IEnumDictService;
import com.sakura.prompt.vo.EnumDictGroupVO;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

/**
 * 枚举字典 Controller（C 端只读）
 * 与数据库词库完全分离，不允许与 sakura_prompt_lib 混查。
 * 仅允许白名单内的枚举类型。
 *
 * @author 视觉AID
 */
@Slf4j
@RestController
@RequestMapping("/api/user/dict")
public class EnumDictController extends BaseController {

    @Resource
    private IEnumDictService enumDictService;

    /**
     * 批量查询枚举字典
     */
    @PostMapping("/enum/list")
    public AjaxResult enumList(@Valid @RequestBody EnumDictListRequest request) {
        List<EnumDictGroupVO> data = enumDictService.listEnums(request);
        return AjaxResult.success(data);
    }
}
