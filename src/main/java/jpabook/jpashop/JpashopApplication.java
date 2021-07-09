package jpabook.jpashop;

import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class JpashopApplication {

    public static void main(String[] args) {
        SpringApplication.run(JpashopApplication.class, args);
    }

    /**
     * Rest API에서 Entity 노출로 인해 이것을 사용했다면... 비추
     */
    @Bean
    Hibernate5Module hibernate5Module() {
        return new Hibernate5Module();
    }
}
