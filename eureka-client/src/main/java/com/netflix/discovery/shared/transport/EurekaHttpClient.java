package com.netflix.discovery.shared.transport;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;

/**
 * Low level Eureka HTTP client API.
 *
 * @author Tomasz Bak
 */
public interface EurekaHttpClient {

    // 服务注册
    EurekaHttpResponse<Void> register(InstanceInfo info);

    // 服务下线
    EurekaHttpResponse<Void> cancel(String appName, String id);

    // 心跳 续约
    EurekaHttpResponse<InstanceInfo> sendHeartBeat(String appName, String id, InstanceInfo info, InstanceStatus overriddenStatus);

    // 状态更新
    EurekaHttpResponse<Void> statusUpdate(String appName, String id, InstanceStatus newStatus, InstanceInfo info);

    // 删除StatusOverride
    EurekaHttpResponse<Void> deleteStatusOverride(String appName, String id, InstanceInfo info);

    // 获取全量注册表信息
    EurekaHttpResponse<Applications> getApplications(String... regions);

    // 获取增量注册表信息
    EurekaHttpResponse<Applications> getDelta(String... regions);

    EurekaHttpResponse<Applications> getVip(String vipAddress, String... regions);

    EurekaHttpResponse<Applications> getSecureVip(String secureVipAddress, String... regions);

    // 根据appName 获取对应的Application
    EurekaHttpResponse<Application> getApplication(String appName);

    EurekaHttpResponse<InstanceInfo> getInstance(String appName, String id);

    EurekaHttpResponse<InstanceInfo> getInstance(String id);

    void shutdown();
}
