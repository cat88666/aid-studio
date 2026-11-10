package com.sakura.dashboard.service.impl;

import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sakura.sakura.domain.SakuraComicEpisode;
import com.sakura.sakura.domain.SakuraComicProject;
import com.sakura.sakura.domain.SakuraGenRecord;
import com.sakura.sakura.domain.SakuraPayOrder;
import com.sakura.sakura.domain.SakuraStoryboard;
import com.sakura.sakura.service.ISakuraComicEpisodeService;
import com.sakura.sakura.service.ISakuraComicProjectService;
import com.sakura.sakura.service.ISakuraGenRecordService;
import com.sakura.sakura.service.ISakuraPayOrderService;
import com.sakura.sakura.service.ISakuraStoryboardService;
import com.sakura.common.constant.CacheConstants;
import com.sakura.common.core.domain.entity.SysUser;
import com.sakura.common.core.redis.RedisCache;
import com.sakura.core.service.ISysUserService;
import com.sakura.dashboard.service.IDashboardStatService;
import com.sakura.dashboard.vo.DashboardOverviewVO;
import cn.hutool.core.collection.CollectionUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 后台首页统计聚合Service实现
 * 把原前端逐项 count 的 10+ 个请求收敛为后端一次聚合，所有计数走 COUNT 查询，不拉明细。
 *
 * @author 视觉AID
 */
@Slf4j
@Service
public class DashboardStatServiceImpl implements IDashboardStatService {

    /** 删除标志：正常（未删除） */
    private static final String DEL_FLAG_NORMAL = "0";

    /** 用户状态：启用 */
    private static final String USER_STATUS_ENABLED = "0";

    /** 项目状态：制作中 */
    private static final Integer PROJECT_STATUS_MAKING = 1;

    /** 生成记录状态：处理中/成功/失败 */
    private static final Integer GEN_STATUS_PROCESSING = 0;
    private static final Integer GEN_STATUS_SUCCESS = 1;
    private static final Integer GEN_STATUS_FAILED = 2;

    /** 支付状态：已支付/待支付 */
    private static final String PAY_STATUS_PAID = "paid";
    private static final String PAY_STATUS_PENDING = "pending";

    @Autowired
    private ISakuraComicProjectService sakuraComicProjectService;

    @Autowired
    private ISakuraComicEpisodeService sakuraComicEpisodeService;

    @Autowired
    private ISakuraStoryboardService sakuraStoryboardService;

    @Autowired
    private ISakuraGenRecordService sakuraGenRecordService;

    @Autowired
    private ISakuraPayOrderService sakuraPayOrderService;

    @Autowired
    private ISysUserService sysUserService;

    @Autowired
    private RedisCache redisCache;

    /**
     * 查询后台首页业务概览
     */
    @Override
    public DashboardOverviewVO getOverview() {
        // 用户统计（系统用户量级小，一次查列表后内存统计总数与启用数）
        long userTotal = 0L;
        long userEnabled = 0L;
        try {
            List<SysUser> users = sysUserService.selectUserList(new SysUser());
            if (CollectionUtil.isNotEmpty(users)) {
                userTotal = users.size();
                userEnabled = users.stream()
                        .filter(u -> USER_STATUS_ENABLED.equals(u.getStatus()))
                        .count();
            }
        } catch (Exception e) {
            // 用户统计失败不影响其它指标，记录后继续
            log.error("首页统计-用户计数失败: {}", e.getMessage());
        }

        // 在线用户：Redis 登录令牌数量
        long online = 0L;
        try {
            Collection<String> keys = redisCache.keys(CacheConstants.LOGIN_TOKEN_KEY + "*");
            online = keys == null ? 0L : keys.size();
        } catch (Exception e) {
            log.error("首页统计-在线用户计数失败: {}", e.getMessage());
        }

        // 项目 / 剧集 / 分镜（均过滤未删除）
        long projectTotal = sakuraComicProjectService.count(Wrappers.<SakuraComicProject>lambdaQuery()
                .eq(SakuraComicProject::getDelFlag, DEL_FLAG_NORMAL));
        long projectMaking = sakuraComicProjectService.count(Wrappers.<SakuraComicProject>lambdaQuery()
                .eq(SakuraComicProject::getDelFlag, DEL_FLAG_NORMAL)
                .eq(SakuraComicProject::getStatus, PROJECT_STATUS_MAKING));
        long episodeTotal = sakuraComicEpisodeService.count(Wrappers.<SakuraComicEpisode>lambdaQuery()
                .eq(SakuraComicEpisode::getDelFlag, DEL_FLAG_NORMAL));
        long storyboardTotal = sakuraStoryboardService.count(Wrappers.<SakuraStoryboard>lambdaQuery()
                .eq(SakuraStoryboard::getDelFlag, DEL_FLAG_NORMAL));

        // 生成记录（总数 + 三态）
        long genTotal = sakuraGenRecordService.count(Wrappers.<SakuraGenRecord>lambdaQuery()
                .eq(SakuraGenRecord::getDelFlag, DEL_FLAG_NORMAL));
        long genSuccess = sakuraGenRecordService.count(Wrappers.<SakuraGenRecord>lambdaQuery()
                .eq(SakuraGenRecord::getDelFlag, DEL_FLAG_NORMAL)
                .eq(SakuraGenRecord::getStatus, GEN_STATUS_SUCCESS));
        long genProcessing = sakuraGenRecordService.count(Wrappers.<SakuraGenRecord>lambdaQuery()
                .eq(SakuraGenRecord::getDelFlag, DEL_FLAG_NORMAL)
                .eq(SakuraGenRecord::getStatus, GEN_STATUS_PROCESSING));
        long genFailed = sakuraGenRecordService.count(Wrappers.<SakuraGenRecord>lambdaQuery()
                .eq(SakuraGenRecord::getDelFlag, DEL_FLAG_NORMAL)
                .eq(SakuraGenRecord::getStatus, GEN_STATUS_FAILED));

        // 支付订单（已支付 + 待支付）
        long orderPaid = sakuraPayOrderService.count(Wrappers.<SakuraPayOrder>lambdaQuery()
                .eq(SakuraPayOrder::getDelFlag, DEL_FLAG_NORMAL)
                .eq(SakuraPayOrder::getPayStatus, PAY_STATUS_PAID));
        long orderPending = sakuraPayOrderService.count(Wrappers.<SakuraPayOrder>lambdaQuery()
                .eq(SakuraPayOrder::getDelFlag, DEL_FLAG_NORMAL)
                .eq(SakuraPayOrder::getPayStatus, PAY_STATUS_PENDING));

        return DashboardOverviewVO.builder()
                .userTotal(userTotal)
                .userEnabled(userEnabled)
                .online(online)
                .projectTotal(projectTotal)
                .projectMaking(projectMaking)
                .episodeTotal(episodeTotal)
                .storyboardTotal(storyboardTotal)
                .genTotal(genTotal)
                .genSuccess(genSuccess)
                .genProcessing(genProcessing)
                .genFailed(genFailed)
                .orderPaid(orderPaid)
                .orderPending(orderPending)
                .build();
    }
}
