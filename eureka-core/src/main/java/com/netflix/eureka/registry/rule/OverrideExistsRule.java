package com.netflix.eureka.registry.rule;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.eureka.lease.Lease;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * This rule checks to see if we have overrides for an instance and if we do then we return those.
 *
 * Created by Nikos Michalakis on 7/13/16.
 */
public class OverrideExistsRule implements InstanceStatusOverrideRule {

    private static final Logger logger = LoggerFactory.getLogger(OverrideExistsRule.class);

    private Map<String, InstanceInfo.InstanceStatus> statusOverrides;

    public OverrideExistsRule(Map<String, InstanceInfo.InstanceStatus> statusOverrides) {
        this.statusOverrides = statusOverrides;
    }

    @Override
    public StatusOverrideResult apply(InstanceInfo instanceInfo, Lease<InstanceInfo> existingLease, boolean isReplication) {
        InstanceInfo.InstanceStatus overridden = statusOverrides.get(instanceInfo.getId());
        // If there are instance specific overrides, then they win - otherwise the ASG status
        // 当执行该规则时，实例的状态为 UP 或 OUT_OF_SERVICE
        // 该规则执行逻辑：查看服务端缓存的 overriddenInstanceStatusMap 中是否有相应实例的 overriddenStatus
        // 如果有则认为是可信的，直接返回 overriddenStatus
        // 如果没有则需要下一个规则处理
        // 因为通过 Actuator 更改实例状态时，会缓存在 overriddenInstanceStatusMap 中
        if (overridden != null) {
            logger.debug("The instance specific override for instance {} and the value is {}",
                    instanceInfo.getId(), overridden.name());
            return StatusOverrideResult.matchingStatus(overridden);
        }
        return StatusOverrideResult.NO_MATCH;
    }

    @Override
    public String toString() {
        return OverrideExistsRule.class.getName();
    }

}
