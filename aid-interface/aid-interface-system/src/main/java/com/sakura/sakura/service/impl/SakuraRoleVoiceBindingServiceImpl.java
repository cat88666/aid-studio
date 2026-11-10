package com.sakura.sakura.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.domain.SakuraRoleVoiceBinding;
import com.sakura.sakura.mapper.SakuraRoleVoiceBindingMapper;
import com.sakura.sakura.service.ISakuraRoleVoiceBindingService;
import org.springframework.stereotype.Service;

/**
 * 角色音色绑定 基础 Service 实现（仅承载裸 CRUD）。
 *
 * @author 视觉AID
 */
@Service
public class SakuraRoleVoiceBindingServiceImpl
        extends ServiceImpl<SakuraRoleVoiceBindingMapper, SakuraRoleVoiceBinding>
        implements ISakuraRoleVoiceBindingService
{
}
