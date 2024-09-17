package org.swasth.hcx.service;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.checkerframework.checker.index.qual.SearchIndexBottom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;

@Service
public class CloudStorageClient {

    @Value("${certificates.accesskey}")
    private String accessKey;
    @Value("${certificates.secretKey}")
    private String secretKey;

    public AmazonS3 getClient() {
        return AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
                .withRegion(Regions.AP_SOUTH_1)
                .build();
    }

    public void putObject(String folderName, String bucketName) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, folderName + "/", new ByteArrayInputStream(new byte[0]), new ObjectMetadata());
        putObjectRequest.setCannedAcl(CannedAccessControlList.PublicRead);
    }

    public void putObject(String bucketName, String folderName, MultipartFile content) throws IOException {
        getClient().putObject(bucketName, folderName, content.getInputStream(), new ObjectMetadata());
    }

    public URL getUrl(String bucketName, String path) {
        return getClient().getUrl(bucketName, path);
    }
}
