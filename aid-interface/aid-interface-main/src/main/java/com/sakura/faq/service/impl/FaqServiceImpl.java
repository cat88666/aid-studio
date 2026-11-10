package com.sakura.faq.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakura.sakura.domain.SakuraFaq;
import com.sakura.sakura.service.ISakuraFaqService;
import com.sakura.common.exception.ServiceException;
import com.sakura.faq.dto.FaqDetailRequest;
import com.sakura.faq.dto.FaqListRequest;
import com.sakura.faq.service.IFaqService;
import com.sakura.faq.vo.FaqDetailVO;
import com.sakura.faq.vo.FaqListItemVO;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 常见问题 - C 端只读 Service 实现
 *
 * @author 视觉AID
 */
@Slf4j
@Service
public class FaqServiceImpl implements IFaqService
{
    /** 状态：显示 */
    private static final String STATUS_SHOW = "0";
    /** 删除标志：未删除 */
    private static final String DEL_FLAG_NORMAL = "0";
    /** 默认页码 */
    private static final int DEFAULT_PAGE_NUM = 1;
    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 10;
    /** 每页最大条数（防止恶意拉全表） */
    private static final int MAX_PAGE_SIZE = 50;

    @Resource
    private ISakuraFaqService sakuraFaqService;

    /**
     * 分页查询常见问题列表（仅显示状态，不含完整内容）
     * 仅查询字段：id, title, category, sortOrder, viewCount, publishTime
     */
    @Override
    public IPage<FaqListItemVO> listFaqs(FaqListRequest request)
    {
        FaqListRequest req = Objects.isNull(request) ? new FaqListRequest() : request;
        int pageNum = Objects.isNull(req.getPageNum()) || req.getPageNum() < 1 ? DEFAULT_PAGE_NUM : req.getPageNum();
        int pageSize = normalizePageSize(req.getPageSize());

        // 仅返回列表必要字段，不含 content 大字段
        LambdaQueryWrapper<SakuraFaq> wrapper = Wrappers.lambdaQuery();
        wrapper.select(
                SakuraFaq::getId,
                SakuraFaq::getTitle,
                SakuraFaq::getCategory,
                SakuraFaq::getSortOrder,
                SakuraFaq::getViewCount,
                SakuraFaq::getPublishTime
        );
        wrapper.eq(SakuraFaq::getStatus, STATUS_SHOW);
        wrapper.eq(SakuraFaq::getDelFlag, DEL_FLAG_NORMAL);
        if (StrUtil.isNotBlank(req.getCategory()))
        {
            wrapper.eq(SakuraFaq::getCategory, req.getCategory());
        }
        if (StrUtil.isNotBlank(req.getKeyword()))
        {
            wrapper.like(SakuraFaq::getTitle, req.getKeyword());
        }
        wrapper.orderByAsc(SakuraFaq::getSortOrder).orderByDesc(SakuraFaq::getId);

        Page<SakuraFaq> page = new Page<>(pageNum, pageSize);
        IPage<SakuraFaq> result = sakuraFaqService.page(page, wrapper);

        List<FaqListItemVO> voList = new ArrayList<>();
        for (SakuraFaq entity : result.getRecords())
        {
            voList.add(FaqListItemVO.builder()
                    .id(entity.getId())
                    .title(entity.getTitle())
                    .category(entity.getCategory())
                    .sortOrder(entity.getSortOrder())
                    .viewCount(entity.getViewCount())
                    .publishTime(entity.getPublishTime())
                    .build());
        }

        Page<FaqListItemVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    /**
     * 查询常见问题详情（含完整内容），命中后累加浏览次数（自增失败不影响返回）
     */
    @Override
    public FaqDetailVO getFaqDetail(FaqDetailRequest request)
    {
        if (Objects.isNull(request) || Objects.isNull(request.getId()))
        {
            log.error("常见问题详情查询入参非法");
            throw new ServiceException("参数错误");
        }

        // 仅返回必要字段
        LambdaQueryWrapper<SakuraFaq> wrapper = Wrappers.lambdaQuery();
        wrapper.select(
                SakuraFaq::getId,
                SakuraFaq::getTitle,
                SakuraFaq::getCategory,
                SakuraFaq::getContent,
                SakuraFaq::getViewCount,
                SakuraFaq::getPublishTime
        );
        wrapper.eq(SakuraFaq::getId, request.getId());
        wrapper.eq(SakuraFaq::getStatus, STATUS_SHOW);
        wrapper.eq(SakuraFaq::getDelFlag, DEL_FLAG_NORMAL);
        wrapper.last("LIMIT 1");

        SakuraFaq entity = sakuraFaqService.getOne(wrapper);
        if (Objects.isNull(entity))
        {
            log.error("常见问题不存在或未显示, id={}", request.getId());
            throw new ServiceException("问题不存在");
        }

        // 累加浏览次数（原子自增，避免读改写并发丢更新；失败不影响详情返回）
        try
        {
            UpdateWrapper<SakuraFaq> updateWrapper = new UpdateWrapper<>();
            updateWrapper.setSql("view_count = view_count + 1");
            updateWrapper.eq("id", entity.getId());
            sakuraFaqService.update(null, updateWrapper);
        }
        catch (Exception e)
        {
            log.error("常见问题浏览次数自增失败, id={}", entity.getId(), e);
        }

        // 出参回显累加后的浏览次数（原值 + 1，避免再查一次库）
        long viewCount = (entity.getViewCount() == null ? 0L : entity.getViewCount()) + 1;
        return FaqDetailVO.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .category(entity.getCategory())
                .content(entity.getContent())
                .viewCount(viewCount)
                .publishTime(entity.getPublishTime())
                .build();
    }

    /** 归一化每页条数：缺省 10，限制在 1..MAX_PAGE_SIZE */
    private int normalizePageSize(Integer pageSize)
    {
        if (Objects.isNull(pageSize) || pageSize < 1)
        {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
