package pl.psoir.awsserviceworker.service;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.sqs.AmazonSQS;
import org.apache.commons.lang3.exception.ExceptionUtils;
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
        logger.info("Started processing scale request from message with receipt handle " + receiptHandle);
        long _startTime = System.currentTimeMillis();
        for (String key : keys) {
            logger.info("Started processing scaling request for image with id " + key);
            long startTime = System.currentTimeMillis();
            BufferedImage image = getImageFromS3(key, receiptHandle);
            long downloadStopTime = System.currentTimeMillis();

            ///-------------Scaling------------
            long scalingStartTime = System.currentTimeMillis();
            int scaledWidth = (int)(image.getWidth() * widthRatio);
            int scaledHeight = (int)(image.getHeight() * heightRatio);

            int[] dimensions = adjustScaleRatio(scaledWidth, scaledHeight, image.getSampleModel().getNumDataElements());

            BufferedImage outputImage;
            try {
                outputImage = new BufferedImage(dimensions[0], dimensions[1], image.getType());
                Graphics2D g2d = outputImage.createGraphics();
                g2d.drawImage(image, 0, 0, dimensions[0], dimensions[1], null);
                g2d.dispose();
            }
            catch (Exception e) {
                logger.error("Exception when scaling image with id " + key +
                        "\n   Receipt handle:      " + receiptHandle +
                        "\n   Source resolution:   " + image.getWidth() + "x" + image.getHeight() +
                        "\n   Expected:            " + scaledWidth + "x" + scaledHeight +
                        "\n   Adjusted:            " + dimensions[0] + "x" + dimensions[1] +
                        "\n   Additional info:\n" + ExceptionUtils.getStackTrace(e));
                return;
            }
            long scalingStopTime = System.currentTimeMillis();
            ///-------------Scaling------------

            long uploadStartTime = System.currentTimeMillis();
            putImageToS3(outputImage, key, receiptHandle);
            long uploadStopTime = System.currentTimeMillis();

            logger.info("Scaled image with id " + key +
                    "\n   Receipt handle:      " + receiptHandle +
                    "\n   Source resolution:   " + image.getWidth() + "x" + image.getHeight() +
                    "\n   Expected:            " + scaledWidth + "x" + scaledHeight +
                    "\n   Adjusted:            " + dimensions[0] + "x" + dimensions[1] +
                    "\n   Download time:       " + (downloadStopTime - startTime) + "ms" +
                    "\n   Scaling time:        " + (scalingStopTime - scalingStartTime) + "ms" +
                    "\n   Upload time:         " + (uploadStopTime - uploadStartTime) + "ms" +
                    "\n   Total:               " + (uploadStopTime - startTime) + "ms");
        }
        deleteMessage(receiptHandle);
        long stopTime = System.currentTimeMillis();
        logger.info("Finished processing scale request from message with receipt handle " + receiptHandle +
                "\n   Total time:          " + (stopTime - _startTime) + "ms");
    }

    private BufferedImage getImageFromS3(String key, String receiptHandle) {
        BufferedImage image = null;
        try {
            S3Object object = amazonS3Client.getObject(bucket, key);
            S3ObjectInputStream s3ObjectInputStream = object.getObjectContent();
            image = ImageIO.read(s3ObjectInputStream);
        } catch (AmazonServiceException | IOException e) {
            logger.error("Exception when downloading image with id " + key +
                    "\n   Receipt handle:      " + receiptHandle +
                    "\n   Additional info:\n" + ExceptionUtils.getStackTrace(e));
        }
        return image;
    }

    private void putImageToS3(BufferedImage image, String key, String receiptHandle) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, getImageExtension(key), byteArrayOutputStream);

            if (byteArrayOutputStream.size() == 0) {
                throw new IllegalArgumentException("Image output stream size = 0");
            }
            else {
                ObjectMetadata objectMetadata = new ObjectMetadata();
                objectMetadata.setContentLength(byteArrayOutputStream.size());
                InputStream inputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
                amazonS3Client.putObject(bucket, key, inputStream, objectMetadata);
            }
        } catch (AmazonServiceException | IOException | IllegalArgumentException e) {
            logger.error("Exception when uploading image with id " + key +
                    "\n   Receipt handle:      " + receiptHandle +
                    "\n   Additional info:\n" + ExceptionUtils.getStackTrace(e));
        }
    }

    private void deleteMessage(String receiptHandle) {
        try {
            String queueUrl = amazonSQSClient.getQueueUrl(queue).getQueueUrl();
            amazonSQSClient.deleteMessage(queueUrl, receiptHandle);
        }
        catch (AmazonServiceException e) {
            logger.error("Exception when deleting message with receipt handle " + receiptHandle +
                    "\n   Additional info:\n" + ExceptionUtils.getStackTrace(e));
        }
    }

    /*
    This method determines if calculated dimensions multiplied by each other and samples per pixel don't exceed BufferedImage Integer.MAX_VALUE limit.
    If so, step by step it reduces their size until they don't exceed the limit
     */
    private int[] adjustScaleRatio(int scaledWidth, int scaledHeight, int samplesPerPixel) {
        while (true) {
            if ((long) scaledWidth * scaledHeight * samplesPerPixel > Integer.MAX_VALUE) {
                scaledWidth = ((int)(scaledWidth / 1.05) > 0) ? ((int)(scaledWidth / 1.05)) : 1;
                scaledHeight = ((int)(scaledHeight / 1.05) > 0) ? ((int)(scaledHeight / 1.05)) : 1;
            }
            else {
                break;
            }
        }
        return new int[]{scaledWidth, scaledHeight};
    }

    private String getImageExtension(String key) {
        return key.substring(key.lastIndexOf(".") + 1);
    }
}
