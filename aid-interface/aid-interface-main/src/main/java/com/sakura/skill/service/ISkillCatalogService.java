package com.sakura.skill.service;

import com.sakura.skill.vo.SkillCatalogVO;

import java.util.List;

/** Read-only catalog for callable Skill Runtime entrypoints. */
public interface ISkillCatalogService {
    List<SkillCatalogVO.Item> listEntrypoints();
}
