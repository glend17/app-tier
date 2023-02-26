package org.cse546.service;

import java.awt.*;
import java.io.*;
import java.net.URL;
import java.util.List;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.sqs.model.Message;
import org.cse546.utility.AWSUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${isEc2On}")
    private String isEc2On;

    public void processImageAndGetResults() throws IOException {
        int try_no =0;
        while (true){
            List<Message> messageList = sqsService.receiveSqsRequestMessage();
            try_no++;
            if(messageList != null){
                processImages (messageList);
                try_no =0;
            }
            else{
                if(try_no ==3){
                    logger.info("Finished processing all images. Request Queue is empty. Terminating instance");
                    //terminate logic
                    if (isEc2On.equals("true")) {
                        String instance_id = getInstanceId ();
                        System.out.println ("Currently running on Ec2");
                        BasicAWSCredentials awsCredentials = awsUtility.getAWSCREDENTIALS ();
                        AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard ()
                                .withCredentials (new AWSStaticCredentialsProvider (awsCredentials))
                                .withRegion (Regions.US_EAST_1)
                                .build ();

                        // Create a terminate request with the instance ID
                        TerminateInstancesRequest terminateRequest = new TerminateInstancesRequest().withInstanceIds(instance_id);

                        // Terminate the instance and get the result
                        TerminateInstancesResult terminateResult = ec2.terminateInstances(terminateRequest);

                    }
                    break;
                }
            }
        }
    }

    public String getInstanceId() throws IOException {
        URL instanceUrl = new URL ("http://169.254.169.254/latest/meta-data/instance-id");
        BufferedReader in = new BufferedReader(new InputStreamReader(instanceUrl.openStream()));
        String instanceId = in.readLine();
        in.close();
        return instanceId;
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
            long start = System.currentTimeMillis ();
            p = new ProcessBuilder("/bin/bash", "-c",
                    "cd  /home/ubuntu && " + "python3 image_classification.py " + messageName).start();

            p.waitFor();
            long end = System.currentTimeMillis ();
            logger.info("Time taken to process classification: " + (end-start));
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
