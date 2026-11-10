package com.sakura.common.image.detection;

public interface ImageObjectDetector {
    String provider();
    DetectImageObjectsResult detect(DetectImageObjectsInput input, ImageDetectionConfig config);
}
