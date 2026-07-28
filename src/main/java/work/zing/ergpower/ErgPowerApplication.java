package work.zing.ergpower;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import work.zing.ergpower.pm5.config.ErgPowerBleProperties;

@SpringBootApplication
@EnableConfigurationProperties(ErgPowerBleProperties.class)
public class ErgPowerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ErgPowerApplication.class, args);
    }

}
