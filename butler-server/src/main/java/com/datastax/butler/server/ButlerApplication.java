/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server;

import org.springframework.boot.Banner.Mode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Controller;

/** Entry point of the butler service. */
@ComponentScan("com.datastax.butler") // scan for ButlerProject @Components
@SpringBootApplication
@EnableScheduling
@Controller
public class ButlerApplication extends SpringBootServletInitializer {

  /** Main method of the butler service. */
  public static void main(String[] args) {
    SpringApplication application = new SpringApplication(ButlerApplication.class);
    application.setBannerMode(Mode.OFF);
    application.run(args);
  }

  @Override
  protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    return application.sources(ButlerApplication.class);
  }
}
