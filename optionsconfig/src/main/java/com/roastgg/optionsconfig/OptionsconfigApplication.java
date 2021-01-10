package com.roastgg.optionsconfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableConfigServer
public class OptionsconfigApplication {

    public static void main(String[] args) {
        SpringApplication.run(OptionsconfigApplication.class, args);
    }

}
