package com.netflix.eureka.registry.rule;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.eureka.lease.Lease;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This rule matches if we have an existing lease for the instance that is UP or OUT_OF_SERVICE.
 *
 * Created by Nikos Michalakis on 7/13/16.
 */
public class LeaseExistsRule implements InstanceStatusOverrideRule {

    private static final Logger logger = LoggerFactory.getLogger(LeaseExistsRule.class);

    @Override
    public StatusOverrideResult apply(InstanceInfo instanceInfo,
                                      Lease<InstanceInfo> existingLease,
                                      boolean isReplication) {
        // This is for backward compatibility until all applications have ASG
        // names, otherwise while starting up
        // the client status may override status replicated from other servers
        // 当执行该规则时，实例的状态为 UP 或 OUT_OF_SERVICE
        // 该规则执行逻辑：如果不是集群节点同步复制的情况下，查看本地注册表相应实例的实例状态
        // 如果实例状态存在，且为 UP 或 OUT_OF_SERVICE ，则直接返回相应状态
        // 如果实例状态存在，且不为 UP 或 OUT_OF_SERVICE ，则需要下一个规则处理
        // 如果实例状态不存在，则需要下一个规则处理
        if (!isReplication) {
            InstanceInfo.InstanceStatus existingStatus = null;
            if (existingLease != null) {
                existingStatus = existingLease.getHolder().getStatus();
            }
            // Allow server to have its way when the status is UP or OUT_OF_SERVICE
            if ((existingStatus != null)
                    && (InstanceInfo.InstanceStatus.OUT_OF_SERVICE.equals(existingStatus)
                    || InstanceInfo.InstanceStatus.UP.equals(existingStatus))) {
                logger.debug("There is already an existing lease with status {}  for instance {}",
                        existingLease.getHolder().getStatus().name(),
                        existingLease.getHolder().getId());
                return StatusOverrideResult.matchingStatus(existingLease.getHolder().getStatus());
            }
        }
        return StatusOverrideResult.NO_MATCH;
    }

    @Override
    public String toString() {
        return LeaseExistsRule.class.getName();
    }
}
