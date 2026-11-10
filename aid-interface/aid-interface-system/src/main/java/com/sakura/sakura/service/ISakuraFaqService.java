package com.sakura.sakura.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sakura.sakura.domain.SakuraFaq;

/**
 * 常见问题（FAQ）Service 接口
 *
 * @author 视觉AID
 */
public interface ISakuraFaqService extends IService<SakuraFaq>
{
    /**
     * 查询常见问题
     *
     * @param id 常见问题主键
     * @return 常见问题
     */
    SakuraFaq selectSakuraFaqById(Long id);

    /**
     * 查询常见问题列表
     *
     * @param sakuraFaq 常见问题
     * @return 常见问题集合
     */
    List<SakuraFaq> selectSakuraFaqList(SakuraFaq sakuraFaq);

    /**
     * 新增常见问题
     *
     * @param sakuraFaq 常见问题
     * @return 结果
     */
    int insertSakuraFaq(SakuraFaq sakuraFaq);

    /**
     * 修改常见问题
     *
     * @param sakuraFaq 常见问题
     * @return 结果
     */
    int updateSakuraFaq(SakuraFaq sakuraFaq);

    /**
     * 批量删除常见问题
     *
     * @param ids 需要删除的常见问题主键集合
     * @return 结果
     */
    int deleteSakuraFaqByIds(Long[] ids);

    /**
     * 删除常见问题信息
     *
     * @param id 常见问题主键
     * @return 结果
     */
    int deleteSakuraFaqById(Long id);
}
