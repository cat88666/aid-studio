package com.sakura.sakura.service.impl;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakura.sakura.domain.SakuraFaq;
import com.sakura.sakura.mapper.SakuraFaqMapper;
import com.sakura.sakura.service.ISakuraFaqService;
import com.sakura.common.utils.DateUtils;
import com.sakura.common.utils.SecurityUtils;

import cn.hutool.core.util.StrUtil;

/**
 * 常见问题（FAQ）Service 业务层处理
 *
 * @author 视觉AID
 */
@Service
public class SakuraFaqServiceImpl extends ServiceImpl<SakuraFaqMapper, SakuraFaq> implements ISakuraFaqService
{
    /**
     * 查询常见问题
     *
     * @param id 常见问题主键
     * @return 常见问题
     */
    @Override
    public SakuraFaq selectSakuraFaqById(Long id)
    {
        return this.getById(id);
    }

    /**
     * 查询常见问题列表
     *
     * @param sakuraFaq 常见问题
     * @return 常见问题
     */
    @Override
    public List<SakuraFaq> selectSakuraFaqList(SakuraFaq sakuraFaq)
    {
        LambdaQueryWrapper<SakuraFaq> wrapper = Wrappers.lambdaQuery();
        if (sakuraFaq != null)
        {
            // 标题模糊检索
            if (StrUtil.isNotBlank(sakuraFaq.getTitle()))
            {
                wrapper.like(SakuraFaq::getTitle, sakuraFaq.getTitle());
            }
            // 分类精确检索
            if (StrUtil.isNotBlank(sakuraFaq.getCategory()))
            {
                wrapper.eq(SakuraFaq::getCategory, sakuraFaq.getCategory());
            }
            // 状态精确检索
            if (StrUtil.isNotBlank(sakuraFaq.getStatus()))
            {
                wrapper.eq(SakuraFaq::getStatus, sakuraFaq.getStatus());
            }
        }
        // 排序：sort_order 升序，相同再按 id 倒序
        wrapper.orderByAsc(SakuraFaq::getSortOrder).orderByDesc(SakuraFaq::getId);
        return this.list(wrapper);
    }

    /**
     * 新增常见问题
     *
     * @param sakuraFaq 常见问题
     * @return 结果
     */
    @Override
    public int insertSakuraFaq(SakuraFaq sakuraFaq)
    {
        // 填充创建时间与创建者
        sakuraFaq.setCreateTime(DateUtils.getNowDate());
        sakuraFaq.setCreateBy(SecurityUtils.getUsername());
        return this.save(sakuraFaq) ? 1 : 0;
    }

    /**
     * 修改常见问题
     *
     * @param sakuraFaq 常见问题
     * @return 结果
     */
    @Override
    public int updateSakuraFaq(SakuraFaq sakuraFaq)
    {
        // 填充更新时间与更新者
        sakuraFaq.setUpdateTime(DateUtils.getNowDate());
        sakuraFaq.setUpdateBy(SecurityUtils.getUsername());
        return this.updateById(sakuraFaq) ? 1 : 0;
    }

    /**
     * 批量删除常见问题
     *
     * @param ids 需要删除的常见问题主键
     * @return 结果
     */
    @Override
    public int deleteSakuraFaqByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            return 0;
        }
        return this.removeByIds(Arrays.asList(ids)) ? 1 : 0;
    }

    /**
     * 删除常见问题信息
     *
     * @param id 常见问题主键
     * @return 结果
     */
    @Override
    public int deleteSakuraFaqById(Long id)
    {
        if (id == null)
        {
            return 0;
        }
        return this.removeById(id) ? 1 : 0;
    }
}
