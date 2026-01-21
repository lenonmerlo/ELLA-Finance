package com.ella.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

import com.ella.backend.config.DotenvLoader;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class BackendApplication {

	public static void main(String[] args) {
		DotenvLoader.loadFromWorkingDirectoryIfPresent();
		SpringApplication.run(BackendApplication.class, args);
	}

}
