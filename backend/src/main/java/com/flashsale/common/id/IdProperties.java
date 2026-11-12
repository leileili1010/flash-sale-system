package com.flashsale.common.id;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param workerId identifier of this application instance, unique across a deployment
 */
@ConfigurationProperties(prefix = "flashsale.id")
public record IdProperties(long workerId) {
}
