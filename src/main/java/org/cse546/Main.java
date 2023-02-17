package org.cse546;

import org.cse546.controller.Listener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Main {

    @Autowired
    private Listener listener;

    public static void main(String[] args) {
        ApplicationContext app = SpringApplication.run(Main.class, args);
        Listener listener = app.getBean(Listener.class);
        listener.processQueue();
    }

    @Bean
    public Listener getBean(){
        return new Listener();
    }

}