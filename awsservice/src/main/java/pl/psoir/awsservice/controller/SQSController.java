package pl.psoir.awsservice.controller;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.SendMessageBatchResult;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.psoir.awsservice.model.DebugData;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@RestController
@CrossOrigin(origins = {"${crossorigin.url}"}, maxAge = 3600)
public class SQSController {

    private static final Logger logger = LoggerFactory.getLogger(SQSController.class);

    private final AmazonSQS amazonSQSClient;

    private final AmazonDynamoDB amazonDynamoDBClient;

    @Value("${aws.sqs.queue}")
    private String queue;

    public SQSController(AmazonSQS amazonSQSClient, AmazonDynamoDB amazonDynamoDBClient) {
        this.amazonSQSClient = amazonSQSClient;
        this.amazonDynamoDBClient = amazonDynamoDBClient;
    }

    @RequestMapping(method= RequestMethod.POST, value="/queue")
    public ResponseEntity<?> postToQueue(@RequestBody String messages) {
        List<SendMessageBatchRequestEntry> sendMessageBatchRequestEntries;

        try {
            JSONArray messagesJson = new JSONArray(messages);
            sendMessageBatchRequestEntries = IntStream.range(0, messagesJson.length()).mapToObj(i -> new SendMessageBatchRequestEntry(UUID.randomUUID().toString(),
                    messagesJson.getJSONObject(i).toString())).collect(Collectors.toList());
        }
        catch (JSONException e) {
            String message = "Exception when parsing messages into SendMessageBatchRequestEntry array" +
                    "\n   Messages:       \n" +
                    messages +
                    "\n   Additional info:\n" + ExceptionUtils.getStackTrace(e);
            logger.error(message);
            new DynamoDBMapper(amazonDynamoDBClient).save(new DebugData(new Date(), this.getClass().getSimpleName(), DebugData.Type.ERROR, message));
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        boolean failedDeliveries = false;
        try {
            String queueUrl = amazonSQSClient.getQueueUrl(queue).getQueueUrl();

            //Batch request can consist of up to 10 messages, slice array
            List<List<SendMessageBatchRequestEntry>> sendMessageBatchRequestEntriesList = new ArrayList<>();
            List<SendMessageBatchRequestEntry> slice = new ArrayList<>();
            for (int i = 0; i < sendMessageBatchRequestEntries.size(); i++) {
                if (i % 10 == 0) {
                    slice = new ArrayList<>();
                    sendMessageBatchRequestEntriesList.add(slice);
                }
                slice.add(sendMessageBatchRequestEntries.get(i));
            }

            for (List<SendMessageBatchRequestEntry> _slice : sendMessageBatchRequestEntriesList) {
                SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest()
                        .withQueueUrl(queueUrl)
                        .withEntries(_slice);
                SendMessageBatchResult result = amazonSQSClient.sendMessageBatch(sendMessageBatchRequest);
                if (result.getFailed().size() > 0) {
                    failedDeliveries = true;
                }
            }
        }
        catch (AmazonClientException e) {
            String message = "Exception when sending messages to queue" +
                    "\n   Messages:       \n" +
                    messages +
                    "\n   Additional info:\n" + ExceptionUtils.getStackTrace(e);
            logger.error(message);
            new DynamoDBMapper(amazonDynamoDBClient).save(new DebugData(new Date(), this.getClass().getSimpleName(), DebugData.Type.ERROR, message));
            return new ResponseEntity<>("One or more messages failed to be delivered", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        if (!failedDeliveries) {
            return new ResponseEntity<>(HttpStatus.OK);
        }
        return new ResponseEntity<>("One or more messages failed to be delivered", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
