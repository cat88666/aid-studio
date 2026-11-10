package com.sakura.sakura.service;

import java.util.List;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraUserSocial;

/**
 * 用户第三方登录授权Service接口
 *
 * @author 视觉AID
 */
public interface ISakuraUserSocialService extends IService<SakuraUserSocial>
{
    /**
     * 查询用户第三方登录授权
     *
     * @param id 用户第三方登录授权主键
     * @return 用户第三方登录授权
     */
    public SakuraUserSocial selectSakuraUserSocialById(Long id);

    /**
     * 查询用户第三方登录授权列表
     *
     * @param sakuraUserSocial 用户第三方登录授权
     * @return 用户第三方登录授权集合
     */
    public List<SakuraUserSocial> selectSakuraUserSocialList(SakuraUserSocial sakuraUserSocial);

    /**
     * 新增用户第三方登录授权
     *
     * @param sakuraUserSocial 用户第三方登录授权
     * @return 结果
     */
    public int insertSakuraUserSocial(SakuraUserSocial sakuraUserSocial);

    /**
     * 修改用户第三方登录授权
     *
     * @param sakuraUserSocial 用户第三方登录授权
     * @return 结果
     */
    public int updateSakuraUserSocial(SakuraUserSocial sakuraUserSocial);

    /**
     * 批量删除用户第三方登录授权
     *
     * @param ids 需要删除的用户第三方登录授权主键集合
     * @return 结果
     */
    public int deleteSakuraUserSocialByIds(Long[] ids);

    /**
     * 删除用户第三方登录授权信息
     *
     * @param id 用户第三方登录授权主键
     * @return 结果
     */
    public int deleteSakuraUserSocialById(Long id);
}
