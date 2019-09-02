package pl.psoir.awsservice.controller;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.SendMessageBatchResult;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@RestController
@CrossOrigin(origins = "http://localhost:3000", maxAge = 3600)
public class SQSController {

    private static final Logger logger = LoggerFactory.getLogger(SQSController.class);

    @Autowired
    private AmazonSQS amazonSQSClient;

    @Value("${aws.sqs.queue}")
    private String queue;

    @RequestMapping(method= RequestMethod.POST, value="/queue")
    public ResponseEntity<?> postToQueue(@RequestBody String messages) {
        JSONArray messagesJson = new JSONArray(messages);

        SendMessageBatchRequestEntry[] sendMessageBatchRequests = new SendMessageBatchRequestEntry[messagesJson.length()];
        for (int i = 0; i < messagesJson.length(); i++) {
            sendMessageBatchRequests[i] = new SendMessageBatchRequestEntry(UUID.randomUUID().toString(),
                    messagesJson.getJSONObject(i).toString());
        }

        String queueUrl = amazonSQSClient.getQueueUrl(queue).getQueueUrl();
        SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest()
                .withQueueUrl(queueUrl)
                .withEntries(sendMessageBatchRequests);
        SendMessageBatchResult result = amazonSQSClient.sendMessageBatch(sendMessageBatchRequest);
        HttpStatus httpStatus = (result.getFailed().size() > 0) ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.OK;
        return new ResponseEntity<>(httpStatus);
    }
}
