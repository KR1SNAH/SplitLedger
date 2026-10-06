package com.splitledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SplitLedgerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SplitLedgerApplication.class, args);
	}

}
