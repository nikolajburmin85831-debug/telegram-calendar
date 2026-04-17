package io.github.nadya.assistant.app.ec2;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class AssistantEc2Application {

    public static void main(String[] args) {
        SpringApplication.run(AssistantEc2Application.class, args);
    }
}
