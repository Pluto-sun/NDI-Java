package com.pluto.ndi.documents.service;

interface ObjectStorage {

    void ensureBucket(String bucket);

    String signUpload(String bucket, String stagingObjectKey);

    String sealAndVerify(
            String bucket, String stagingObjectKey, String objectKey,
            long expectedSize, String expectedMediaType, String expectedSha256
    );

    String store(String bucket, String key, String mediaType, byte[] content);

    void remove(String bucket, String key);

    String signDownload(String bucket, String objectKey);

    String signAttachment(String bucket, String objectKey, String filename);
}
