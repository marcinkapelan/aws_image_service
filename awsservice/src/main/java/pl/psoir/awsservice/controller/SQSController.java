package pl.psoir.awsservice.controller;

import com.amazonaws.HttpMethod;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.psoir.awsservice.model.DebugData;

import java.util.Date;
import java.util.UUID;


@RestController
@CrossOrigin(origins = "http://localhost:3000", maxAge = 3600)
public class SQSController {

    private static final Logger logger = LoggerFactory.getLogger(SQSController.class);

    @Autowired
    private AmazonSQS amazonSQSClient;

    @Autowired
    private AmazonDynamoDB amazonDynamoDBClient;

    @Value("${aws.sqs.queue}")
    private String queue;

    @RequestMapping(method= RequestMethod.POST, value="/queue")
    public ResponseEntity<?> postToQueue(@RequestBody String messages) {
        SendMessageBatchRequestEntry[] sendMessageBatchRequests;

        try {
            JSONArray messagesJson = new JSONArray(messages);

            sendMessageBatchRequests = new SendMessageBatchRequestEntry[messagesJson.length()];
            for (int i = 0; i < messagesJson.length(); i++) {
                sendMessageBatchRequests[i] = new SendMessageBatchRequestEntry(UUID.randomUUID().toString(),
                        messagesJson.getJSONObject(i).toString());
            }
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

        try {
            String queueUrl = amazonSQSClient.getQueueUrl(queue).getQueueUrl();
            SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest()
                    .withQueueUrl(queueUrl)
                    .withEntries(sendMessageBatchRequests);
            SendMessageBatchResult result = amazonSQSClient.sendMessageBatch(sendMessageBatchRequest);
            if (result.getFailed().size() > 0) {
                throw new RuntimeException("Failed sending" + result.getFailed().size() + "messages");
            }
            HttpStatus httpStatus = (result.getFailed().size() > 0) ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.OK;
        }
        catch (Exception e) {
            String message = "Exception when sending messages to queue" +
                    "\n   Messages:       \n" +
                    messages +
                    "\n   Additional info:\n" + ExceptionUtils.getStackTrace(e);
            logger.error(message);
            new DynamoDBMapper(amazonDynamoDBClient).save(new DebugData(new Date(), this.getClass().getSimpleName(), DebugData.Type.ERROR, message));
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
