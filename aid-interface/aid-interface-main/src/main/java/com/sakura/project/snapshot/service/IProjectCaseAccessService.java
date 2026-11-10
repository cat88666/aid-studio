package com.sakura.project.snapshot.service;

import java.util.List;
import java.util.Map;

import com.sakura.project.snapshot.model.ProjectCaseAccessSubject;
import com.sakura.project.snapshot.vo.ProjectCaseAccessSummary;

/** 统一计算案例广场项目的有效查看与复制能力。 */
public interface IProjectCaseAccessService
{
    ProjectCaseAccessSummary evaluate(ProjectCaseAccessSubject subject);

    Map<Long, ProjectCaseAccessSummary> evaluateBatch(List<ProjectCaseAccessSubject> subjects);
}
