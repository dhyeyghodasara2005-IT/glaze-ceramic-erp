package com.ceramicerp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CeramicErpApplication {

	public static void main(String[] args) {
		SpringApplication.run(CeramicErpApplication.class, args);
	}

}
