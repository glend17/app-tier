package org.cse546.service;

import java.awt.*;
import java.io.*;
import java.util.List;

import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.sqs.model.Message;
import org.cse546.utility.AWSUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.apache.commons.io.IOUtils;

@Service
public class ImageRecognitionService {

    private static final Logger logger = LoggerFactory.getLogger(ImageRecognitionService.class);
    @Autowired
    private SQSService sqsService;

    @Autowired
    private S3Service s3Service;

    @Autowired
    private AWSUtility awsUtility;

    public void processImageAndGetResults(){

        while (true){
            List<Message> messageList = sqsService.receiveSqsRequestMessage();
            if(messageList == null){
                logger.info("Finished processing all images. Request Queue is empty");
            } else {
                processImages (messageList);
            }
        }
    }

    private void processImages(List<Message> imageList){
        logger.info("Processing images");
        for (Message message : imageList){
            String fileName = message.getBody();
            String predictedValue = getPrediction(fileName);
            if(predictedValue == null){
                predictedValue = new String("No image predicted");
            }
            logger.info("Predicted image name for file: {} is, {}", fileName, predictedValue);
            s3Service.insertResultData(fileName, predictedValue);
            String result = fileName + ":" + predictedValue;
            sqsService.sendMessage(result, awsUtility.getSqsResponseUrl());

        }
        sqsService.deleteQueueMessages(imageList, awsUtility.getSqsRequestUrl());
    }

    private String getPrediction(String messageName){

        logger.info("Running the deep learning model for image name {}", messageName);
        String imageUrl = "s3://" + awsUtility.getImageBucketName() + "/" + messageName;
        logger.info("s3ImageUrl: " + imageUrl);

        GetObjectRequest request = new GetObjectRequest(awsUtility.getImageBucketName(), messageName);
        S3Object object = awsUtility.getAmazonS3().getObject(request);
        S3ObjectInputStream objectContent = object.getObjectContent();
        logger.info("s3ImageUrl: " + imageUrl);

        try {
            logger.info("Downloading to location: ");
            IOUtils.copy(objectContent, new FileOutputStream("/home/ubuntu/" + messageName));
        } catch (FileNotFoundException e) {
            logger.info("FileNotFoundException");
            e.printStackTrace();
        } catch (IOException e) {
            logger.info("IOException");
            e.printStackTrace();
        }

        String output = null;
        Process p;
        try {
            p = new ProcessBuilder("/bin/bash", "-c",
                    "cd  /home/ubuntu && " + "python3 image_classification.py " + messageName).start();

            p.waitFor();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
//			logger.info("ProcessBuilder: " + p);
            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            logger.info("br: " + br);
            logger.info("strError: " + stdError);
            output = br.readLine();
            logger.info("termOutput: " + output);
            p.destroy();
        } catch (Exception e) {
            logger.info("Error while processing image recognition");
            e.printStackTrace();
        }
        return output.trim();

    }
}
