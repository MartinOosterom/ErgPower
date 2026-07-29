package work.zing.ergpower;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import work.zing.ergpower.pm5.config.ErgPowerBleProperties;
import work.zing.ergpower.pm5.config.LlmProperties;

@SpringBootApplication
@EnableConfigurationProperties({ErgPowerBleProperties.class, LlmProperties.class})
public class ErgPowerApplication {

    public static void main(String[] args) {
        // Only `serve` runs the reactive web server; capture/replay stay as plain CLI commands that exit.
        boolean serve = Arrays.asList(args).contains("serve");
        SpringApplication app = new SpringApplication(ErgPowerApplication.class);
        app.setWebApplicationType(serve ? WebApplicationType.REACTIVE : WebApplicationType.NONE);
        app.run(args);
    }

}
