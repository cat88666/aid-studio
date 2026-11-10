package com.sakura.service;

import com.sakura.domain.dto.ImageGenReqDTO;
import com.sakura.domain.dto.MediaGenRespDTO;
import com.sakura.domain.dto.TextGenReqDTO;
import com.sakura.domain.dto.VideoGenReqDTO;

/**
 * 分镜媒体生成业务 Service 接口。
 *
 * @author 视觉AID
 */
public interface IMediaGenerationBizService {

    /**
     * 生成分镜文本。
     *
     * @param dto 文本生成请求
     * @return 媒体生成响应
     */
    MediaGenRespDTO generateText(TextGenReqDTO dto);

    /**
     * 生成分镜图片。
     *
     * @param dto 图片生成请求
     * @return 媒体生成响应
     */
    MediaGenRespDTO generateImage(ImageGenReqDTO dto);

    /**
     * 生成分镜视频。
     *
     * @param dto 视频生成请求
     * @return 媒体生成响应
     */
    MediaGenRespDTO generateVideo(VideoGenReqDTO dto);
}
