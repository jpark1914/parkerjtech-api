package com.parkerjtech.backendserviceapi.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import com.parkerjtech.backendserviceapi.support.HttpTestClient;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class H2ConsoleDisabledTest {
    @LocalServerPort int port;
    @Autowired ApplicationContext context;
    @Test void doesNotExposeConsoleWithoutLocalProfile() throws Exception {
        assertThat(context.containsBean("h2Console")).isFalse();
        var response=new HttpTestClient(port).get("/h2-console/");
        assertThat(response.status()).isEqualTo(401);
        assertThat(response.body()).doesNotContain("<title>H2 Console");
    }
}
