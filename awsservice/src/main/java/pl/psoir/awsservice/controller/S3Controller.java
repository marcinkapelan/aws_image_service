package pl.psoir.awsservice.controller;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3control.model.S3ObjectMetadata;
import org.apache.http.entity.ContentType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.psoir.awsservice.utils.S3Utils;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@RestController
@CrossOrigin(origins = {"${crossorigin.url}"}, maxAge = 3600)
public class S3Controller {

    private static final Logger logger = LoggerFactory.getLogger(S3Controller.class);

    @Autowired
    private AmazonS3 amazonS3Client;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.accessKey}")
    private String accessKey;

    @Value("${aws.s3.secretKey}")
    private String secretKey;

    @Value("${aws.s3.region}")
    private String region;

    //Doesn't seem to work for delete requests..
    @RequestMapping(method= RequestMethod.GET, value="/presignedurl")
    public ResponseEntity<?> getPresignedUrl(@RequestParam String fileName, @RequestParam(required = false) String fileType, @RequestParam String httpMethod) {
        if (!httpMethod.toUpperCase().equals("GET") &&
                !httpMethod.toUpperCase().equals("PUT") &&
                !httpMethod.toUpperCase().equals("DELETE")) {
            return new ResponseEntity<>("Invalid httpMethod param. Accepted: GET, PUT, DELETE", HttpStatus.BAD_REQUEST);
        }
        if (httpMethod.toUpperCase().equals("PUT") && (fileType == null || fileType.isEmpty())) {
            return new ResponseEntity<>("fileType param is required for PUT method", HttpStatus.BAD_REQUEST);
        }
        GeneratePresignedUrlRequest generatePresignedUrlRequest;
        URL url;
        if (httpMethod.toUpperCase().equals("GET") || httpMethod.toUpperCase().equals("DELETE")) {
            generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucket, fileName)
                    .withMethod(HttpMethod.valueOf(httpMethod.toUpperCase()))
                    .withExpiration(generateExpirationDate(1200000)); //20 minutes
            url = amazonS3Client.generatePresignedUrl(generatePresignedUrlRequest);
        }
        else {
            generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucket, generateObjectKey(fileName))
                    .withMethod(HttpMethod.valueOf(httpMethod.toUpperCase()))
                    .withContentType(fileType)
                    .withExpiration(generateExpirationDate(1200000)); //20 minutes
            url = amazonS3Client.generatePresignedUrl(generatePresignedUrlRequest);
        }
        return new ResponseEntity<>(url, HttpStatus.OK);
    }

    @RequestMapping(method= RequestMethod.GET, value="/presignedurls")
    public ResponseEntity<?> getPresignedUrls() {
        JSONArray urls = new JSONArray();
        S3Objects.inBucket(amazonS3Client, bucket).forEach((S3ObjectSummary objectSummary) -> {
            String fileName = objectSummary.getKey();
            Long fileSize = objectSummary.getSize();
            GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucket, fileName)
                    .withMethod(HttpMethod.GET)
                    .withExpiration(generateExpirationDate(1200000)); //20 minutes
            URL url = amazonS3Client.generatePresignedUrl(generatePresignedUrlRequest);
            urls.put(new JSONObject()
                    .put("url", url)
                    .put("size", fileSize)
            );
        });
        logger.info("getPresignedUrls(): Created " + urls.length() + " urls");
        return new ResponseEntity<>(urls.toString(), HttpStatus.OK);
    }

    @RequestMapping(method= RequestMethod.GET, value="/presignedpost")
    public ResponseEntity<?> getPresignedPost(@RequestParam String fileName) {
        String objectKey = generateObjectKey(fileName);
        Date currentDate = new Date();
        Date expirationDate = generateExpirationDate(1200000); //20 minutes

        String policy = S3Utils.createPolicy(expirationDate, bucket, accessKey,
                currentDate, region, objectKey);
        logger.info("Policy:\n" + policy);

        String policyBase64 = Base64.getEncoder().encodeToString(policy.getBytes());
        logger.info("Policy base64: " + policyBase64);

        String signature = null;
        try {
            signature = S3Utils.calculateSignature(policyBase64, secretKey, currentDate, region, "s3");
        } catch (NoSuchAlgorithmException | InvalidKeyException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        logger.info("Signature: " + signature);

        ArrayList<Map<String, String>> policyMap = S3Utils.createPolicyMap(expirationDate, bucket, accessKey,
                currentDate, region, objectKey);

        policyMap.get(1).put(S3Utils.POLICY, policyBase64);
        policyMap.get(1).put(S3Utils.SIGNATURE, signature);

        return new ResponseEntity<>(
                new JSONObject()
                .put("url", "https://s3." + region + ".amazonaws.com/" + bucket)
                .put("fields", new JSONObject(policyMap.get(1))).toString(), HttpStatus.OK);
    }

    @RequestMapping(method= RequestMethod.DELETE, value="/object")
    public ResponseEntity<?> object(@RequestParam String fileName) {
        amazonS3Client.deleteObject(bucket, fileName);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    private String generateObjectKey(String fileName) {
        return UUID.randomUUID().toString() + new Date().getTime() + "_" + fileName;
    }

    private Date generateExpirationDate(long offsetMillis) {
        Date date = new Date();
        date.setTime(date.getTime() + offsetMillis);
        return date;
    }
}
