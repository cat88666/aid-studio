package com.sakura.sakura.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.domain.SakuraReferenceAudio;
import com.sakura.sakura.mapper.SakuraReferenceAudioMapper;
import com.sakura.sakura.service.ISakuraReferenceAudioService;
import org.springframework.stereotype.Service;

/**
 * 用户上传参考音频 基础 Service 实现（仅承载裸 CRUD）。
 *
 * @author 视觉AID
 */
@Service
public class SakuraReferenceAudioServiceImpl
        extends ServiceImpl<SakuraReferenceAudioMapper, SakuraReferenceAudio>
        implements ISakuraReferenceAudioService
{
}
