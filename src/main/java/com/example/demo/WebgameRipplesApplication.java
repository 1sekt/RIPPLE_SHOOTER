package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WebgameRipplesApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebgameRipplesApplication.class, args);
	}

}
