package com.sakura.sakura.domain;

import com.sakura.common.sakura.oss.annotation.MediaUrl;
import com.sakura.common.core.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;
import java.util.Date;

/** 可被搜索引擎发现的规范页面。 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("sakura_seo_page")
public class SakuraSeoPage extends BaseEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String sourceType;
    private String sourceId;
    private String pagePath;
    private String canonicalUrl;
    private String pageTitle;
    private String metaDescription;
    private String metaKeywords;
    @MediaUrl
    private String ogImageUrl;
    private Integer indexable;
    private Integer sitemapEnabled;
    private String contentHash;
    private Date sourceUpdateTime;
    private Date lastSeenTime;
    private String status;
    private String delFlag;
}
