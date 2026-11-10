package com.sakura.sakura.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraPublishWhitelist;
import com.sakura.sakura.domain.vo.SakuraPublishUserVo;
import com.sakura.sakura.domain.vo.SakuraPublishWhitelistVo;

/**
 * 作品发布白名单Service接口
 *
 * @author 视觉AID
 */
public interface ISakuraPublishWhitelistService extends IService<SakuraPublishWhitelist>
{
    /**
     * 判断用户是否在发布白名单内
     *
     * @param userId 用户ID
     * @return 是否在白名单
     */
    boolean existsByUserId(Long userId);

    /**
     * 查询白名单列表（联表用户信息，keyword 匹配昵称/邮箱/手机号）
     *
     * @param keyword 搜索关键字（可空）
     * @return 白名单列表
     */
    List<SakuraPublishWhitelistVo> selectWhitelistVoList(String keyword);

    /**
     * 按 昵称/邮箱/手机号 搜索用户（带发布权限与白名单标记）
     *
     * @param keyword 搜索关键字
     * @return 用户列表
     */
    List<SakuraPublishUserVo> searchPublishUsers(String keyword);
}
