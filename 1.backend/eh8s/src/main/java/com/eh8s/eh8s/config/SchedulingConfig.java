package com.eh8s.eh8s.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring scheduling only when {@code eh8s.reconcile.scheduled=true}. Off by default so
 * live confirm paths are never coupled to a nightly job.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "eh8s.reconcile.scheduled", havingValue = "true")
public class SchedulingConfig {}
