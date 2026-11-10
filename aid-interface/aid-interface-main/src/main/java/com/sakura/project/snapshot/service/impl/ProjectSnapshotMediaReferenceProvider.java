package com.sakura.project.snapshot.service.impl;

import java.util.Collection;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.sakura.sakura.domain.SakuraComicProject;
import com.sakura.sakura.service.ISakuraComicProjectService;
import com.sakura.media.cleanup.IAdditionalMediaReferenceProvider;
import com.sakura.project.snapshot.domain.SakuraProjectPublishSnapshotMedia;
import com.sakura.project.snapshot.mapper.SakuraProjectPublishSnapshotMediaMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import lombok.RequiredArgsConstructor;

/** 当前发布版本或有效复制工程引用的快照媒体参与保护。 */
@Component
@RequiredArgsConstructor
public class ProjectSnapshotMediaReferenceProvider implements IAdditionalMediaReferenceProvider
{
    private final SakuraProjectPublishSnapshotMediaMapper mediaMapper;
    private final ISakuraComicProjectService projectService;

    @Override
    public Set<String> findReferenced(Collection<String> candidates)
    {
        if (candidates == null || candidates.isEmpty()) {
            return Set.of();
        }
        List<SakuraProjectPublishSnapshotMedia> references = mediaMapper.selectList(
                Wrappers.<SakuraProjectPublishSnapshotMedia>lambdaQuery()
                        .select(SakuraProjectPublishSnapshotMedia::getSnapshotId,
                                SakuraProjectPublishSnapshotMedia::getMediaUrl)
                        .in(SakuraProjectPublishSnapshotMedia::getMediaUrl, candidates));
        Set<Long> snapshotIds = new LinkedHashSet<>();
        references.forEach(row -> snapshotIds.add(row.getSnapshotId()));
        snapshotIds.remove(null);
        if (snapshotIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> activeSnapshotIds = new LinkedHashSet<>();
        projectService.list(Wrappers.<SakuraComicProject>lambdaQuery()
                .select(SakuraComicProject::getPublishedSnapshotId, SakuraComicProject::getSourceSnapshotId)
                .eq(SakuraComicProject::getDelFlag, "0")
                .and(wrapper -> wrapper.in(SakuraComicProject::getPublishedSnapshotId, snapshotIds)
                        .or().in(SakuraComicProject::getSourceSnapshotId, snapshotIds)))
                .forEach(row -> {
                    activeSnapshotIds.add(row.getPublishedSnapshotId());
                    activeSnapshotIds.add(row.getSourceSnapshotId());
                });
        activeSnapshotIds.remove(null);
        Set<String> result = new LinkedHashSet<>();
        references.stream()
                .filter(row -> activeSnapshotIds.contains(row.getSnapshotId()))
                .map(SakuraProjectPublishSnapshotMedia::getMediaUrl)
                .forEach(result::add);
        return result;
    }
}
