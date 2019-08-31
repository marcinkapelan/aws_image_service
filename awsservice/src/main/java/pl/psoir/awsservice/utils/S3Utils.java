package pl.psoir.awsservice.utils;

import org.apache.commons.codec.binary.Hex;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class S3Utils {

    public static final String POLICY = "Policy";
    public static final String SIGNATURE = "X-Amz-Signature";

    public static String createPolicy(Date expirationDate, String bucket, String accessKey, Date currentDate, String region, String key) {
        return "{\"expiration\": \"" + ISO8601GMT(expirationDate) + "\",\n" +
                "  \"conditions\": [\n" +
                "    {\"bucket\": \"" + bucket + "\"},\n" +
                "    {\"key\": \"" + key + "\"},\n" +
                "\n" +
                "    {\"x-amz-credential\": \"" + accessKey + "/" + YYYYMMDD(currentDate) + "/" + region + "/s3/aws4_request\"},\n" +
                "    {\"x-amz-algorithm\": \"AWS4-HMAC-SHA256\"},\n" +
                "    {\"x-amz-date\": \"" + ISO8601GMTSimple(currentDate) + "\" }\n" +
                "  ]\n" +
                "}";
    }

    public static ArrayList<Map<String, String>> createPolicyMap(Date expirationDate, String bucket, String accessKey, Date currentDate, String region, String key) {
        ArrayList<Map<String, String>> policy = new ArrayList<>();
        policy.add(new HashMap<>());
        policy.add(new HashMap<>());

        policy.get(0).put("expiration", ISO8601GMT(expirationDate));
        policy.get(1).put("bucket", bucket);
        policy.get(1).put("key", key);
        policy.get(1).put("x-amz-credential", accessKey + "/" + YYYYMMDD(currentDate) + "/" + region + "/s3/aws4_request");
        policy.get(1).put("x-amz-algorithm", "AWS4-HMAC-SHA256");
        policy.get(1).put("x-amz-date", ISO8601GMTSimple(currentDate));

        return policy;
    }

    public static String calculateSignature(String stringToSign, String secretKey, Date currentDate, String region, String service) throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {
        byte[] dateKey = HmacSHA256(YYYYMMDD(currentDate), ("AWS4" + secretKey).getBytes());
        byte[] dateRegionKey = HmacSHA256(region, dateKey);
        byte[] dateRegionServiceKey = HmacSHA256(service, dateRegionKey);
        byte[] signingKey = HmacSHA256("aws4_request", dateRegionServiceKey);

        return Hex.encodeHexString(HmacSHA256(stringToSign, signingKey));
    }

    private static byte[] HmacSHA256(String data, byte[] key) throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {
        String algorithm = "HmacSHA256";
        String charsetName = "UTF-8";

        Mac sha256_HMAC = Mac.getInstance(algorithm);

        SecretKeySpec secret_key = new SecretKeySpec(key, algorithm);
        sha256_HMAC.init(secret_key);

        return sha256_HMAC.doFinal(data.getBytes(charsetName));
    }

    private static String ISO8601GMT(Date date) {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        df.setTimeZone(tz);
        return df.format(date);
    }

    private static String ISO8601GMTSimple(Date date) {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        df.setTimeZone(tz);
        return df.format(date);
    }

    private static String YYYYMMDD(Date date) {
        DateFormat df = new SimpleDateFormat("yyyyMMdd");
        return df.format(date);
    }
}
