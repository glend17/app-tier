package org.cse546.controller;


import org.cse546.service.ImageRecognitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Listener {

    @Autowired
    private ImageRecognitionService imageRecognitionService;

    public void processQueue(){
        imageRecognitionService.processImageAndGetResults();

    }
}
