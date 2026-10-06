package com.splitledger;

import org.springframework.boot.SpringApplication;

public class TestSplitLedgerApplication {

	public static void main(String[] args) {
		SpringApplication.from(SplitLedgerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
