package com.sakura.model.probe.impl;

import org.springframework.stereotype.Component;

import com.sakura.media.constants.ViduConstants;

/**
 * Vidu 图片协议的任务查询探测。
 */
@Component
public class ViduImageProbe extends AbstractViduProbe {

    @Override
    public String protocol() {
        return ViduConstants.PROTOCOL_IMAGE;
    }
}
