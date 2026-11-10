package com.sakura.project.snapshot.service;

import com.sakura.project.snapshot.dto.AdminProjectCopyLabelRequest;
import com.sakura.project.snapshot.dto.AdminProjectSnapshotRebuildRequest;
import com.sakura.project.snapshot.dto.ProjectSnapshotCopyRequest;
import com.sakura.project.snapshot.vo.ProjectSnapshotCopyResultVO;
import com.sakura.project.snapshot.vo.ProjectSnapshotPreviewVO;

public interface IProjectSnapshotService
{
    void captureApprovedSnapshot(Long projectId, Long ownerUserId, String operator);

    ProjectSnapshotPreviewVO preview(Long projectId);

    ProjectSnapshotCopyResultVO copy(ProjectSnapshotCopyRequest request, Long userId, String operator);

    void updateCopyLabel(AdminProjectCopyLabelRequest request, String operator);

    void rebuildApprovedSnapshot(AdminProjectSnapshotRebuildRequest request, String operator);
}
