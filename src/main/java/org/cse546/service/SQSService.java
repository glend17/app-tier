package org.cse546.service;

import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import org.cse546.utility.AWSUtility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SQSService {
    @Autowired
    private AWSUtility awsUtility;

    public List<Message> receiveSqsRequestMessage(){
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(awsUtility.getSqsRequestUrl());
        receiveMessageRequest.setWaitTimeSeconds(0);
        receiveMessageRequest.setVisibilityTimeout(15);
        receiveMessageRequest.setMaxNumberOfMessages(1);
        ReceiveMessageResult receiveMessageResult  = awsUtility.getSQSQueue().receiveMessage(receiveMessageRequest);
        List<Message> messageList = receiveMessageResult.getMessages();
        if(messageList.isEmpty()) return null;
        return messageList;
    }
}
