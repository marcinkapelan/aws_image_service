package pl.psoir.awsserviceworker.service;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.sqs.AmazonSQS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;

@Service
public class ImageProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(ImageProcessingService.class);

    @Autowired
    private AmazonS3 amazonS3Client;

    @Autowired
    private AmazonSQS amazonSQSClient;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.sqs.queue}")
    private String queue;

    @Async
    public void scaleImages(float widthRatio, float heightRatio, List<String> keys, String receiptHandle) {
        for (String key : keys) {
            BufferedImage image = getImageFromS3(key);

            int scaledWidth = (int)(image.getWidth() * widthRatio);
            int scaledHeight = (int)(image.getHeight() * heightRatio);

            BufferedImage outputImage = new BufferedImage(scaledWidth, scaledHeight, image.getType());
            Graphics2D g2d = outputImage.createGraphics();
            g2d.drawImage(image, 0, 0, scaledWidth, scaledHeight, null);
            g2d.dispose();

            putImageToS3(outputImage, key);
            deleteMessage(receiptHandle);
        }
    }

    private BufferedImage getImageFromS3(String key) {
        BufferedImage image = null;
        try {
            S3Object object = amazonS3Client.getObject(bucket, key);
            S3ObjectInputStream s3ObjectInputStream = object.getObjectContent();
            image = ImageIO.read(s3ObjectInputStream);
        } catch (AmazonServiceException e) {
            System.err.println(e.getErrorMessage());
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        return image;
    }

    private void putImageToS3(BufferedImage image, String key) {
        logger.info("Resized image: " + image.getWidth()  + "x" + image.getHeight());
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, getImageExtension(key), byteArrayOutputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (byteArrayOutputStream.size() == 0) {
            logger.error("Image output stream size = 0");
        }
        else {
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentLength(byteArrayOutputStream.size());
            InputStream inputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
            amazonS3Client.putObject(bucket, key, inputStream, objectMetadata);
        }
    }

    private void deleteMessage(String receiptHandle) {
        String queueUrl = amazonSQSClient.getQueueUrl(queue).getQueueUrl();
        amazonSQSClient.deleteMessage(queueUrl, receiptHandle);
    }

    private String getImageExtension(String key) {
        return key.substring(key.lastIndexOf(".") + 1);
    }
}
