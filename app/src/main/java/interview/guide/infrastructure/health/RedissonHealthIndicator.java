package interview.guide.infrastructure.health;

import org.redisson.api.RedissonClient;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Reports Redis availability through the standard Actuator health endpoint. */
@Component("redis")
public class RedissonHealthIndicator implements HealthIndicator {

    private final RedissonClient redissonClient;

    public RedissonHealthIndicator(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public Health health() {
        try {
            redissonClient.getKeys().count();
            return Health.up().build();
        } catch (Exception exception) {
            return Health.down(exception).build();
        }
    }
}
