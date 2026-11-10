package com.sakura.sakura.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.sakura.sakura.domain.dto.OssUploadResponse;

public interface IAdminUploadService
{
    OssUploadResponse uploadOne(MultipartFile file);

    List<OssUploadResponse> uploadList(List<MultipartFile> files);

    OssUploadResponse uploadAvatar(MultipartFile file);
}
