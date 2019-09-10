package pl.psoir.awsserviceworker.service;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class SQSReadMessagesService {

    private static final Logger logger = LoggerFactory.getLogger(SQSReadMessagesService.class);

    @Value("${aws.sqs.queue}")
    private String queue;

    private final AmazonSQS amazonSQSClient;

    private final ImageProcessingService imageProcessingService;

    public SQSReadMessagesService(AmazonSQS amazonSQSClient, ImageProcessingService imageProcessingService) {
        this.amazonSQSClient = amazonSQSClient;
        this.imageProcessingService = imageProcessingService;
    }

    @Scheduled(fixedDelay = 5000L)
    public void poolQueue() {
        String queueUrl = amazonSQSClient.getQueueUrl(queue).getQueueUrl();
        final ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(queueUrl);
        final List<Message> messages = amazonSQSClient.receiveMessage(receiveMessageRequest).getMessages();

        for (final Message message : messages) {
            logger.info("Message");
            logger.info("  MessageId:     " + message.getMessageId());
            logger.info("  ReceiptHandle: " + message.getReceiptHandle());
            logger.info("  Body:          " + message.getBody());

            JSONObject messageBody = new JSONObject(message.getBody());
            String receiptHandle = message.getReceiptHandle();
            float widthRatio = messageBody.getFloat("width");
            float heightRatio = messageBody.getFloat("height");
            JSONArray filesJson =  messageBody.getJSONArray("files");
            List<String> files = IntStream.range(0, filesJson.length()).mapToObj(i -> filesJson.getJSONObject(i).getString("name")).collect(Collectors.toList());

            imageProcessingService.scaleImages(widthRatio, heightRatio, files, receiptHandle);
        }
    }
}
