/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.eureka.resources;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import com.netflix.eureka.cluster.PeerEurekaNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <em>jersey</em> resource that handles operations for a particular instance.
 *
 * @author Karthik Ranganathan, Greg Kim
 *
 */
@Produces({"application/xml", "application/json"})
public class InstanceResource {
    private static final Logger logger = LoggerFactory
            .getLogger(InstanceResource.class);

    private final PeerAwareInstanceRegistry registry;
    private final EurekaServerConfig serverConfig;
    private final String id;
    private final ApplicationResource app;


    InstanceResource(ApplicationResource app, String id, EurekaServerConfig serverConfig, PeerAwareInstanceRegistry registry) {
        this.app = app;
        this.id = id;
        this.serverConfig = serverConfig;
        this.registry = registry;
    }

    /**
     * Get requests returns the information about the instance's
     * {@link InstanceInfo}.
     *
     * @return response containing information about the the instance's
     *         {@link InstanceInfo}.
     */
    @GET
    public Response getInstanceInfo() {
        InstanceInfo appInfo = registry
                .getInstanceByAppAndId(app.getName(), id);
        if (appInfo != null) {
            logger.debug("Found: {} - {}", app.getName(), id);
            return Response.ok(appInfo).build();
        } else {
            logger.debug("Not Found: {} - {}", app.getName(), id);
            return Response.status(Status.NOT_FOUND).build();
        }
    }

    /**
     * A put request for renewing lease from a client instance.
     *
     * @param isReplication
     *            a header parameter containing information whether this is
     *            replicated from other nodes.
     * @param overriddenStatus
     *            overridden status if any.
     * @param status
     *            the {@link InstanceStatus} of the instance.
     * @param lastDirtyTimestamp
     *            last timestamp when this instance information was updated.
     * @return response indicating whether the operation was a success or
     *         failure.
     *         服务续约
     */
    @PUT
    public Response renewLease(
            @HeaderParam(PeerEurekaNode.HEADER_REPLICATION) String isReplication,
            @QueryParam("overriddenstatus") String overriddenStatus,
            @QueryParam("status") String status,
            @QueryParam("lastDirtyTimestamp") String lastDirtyTimestamp) {
        // isReplication：是否是集群节点间的同步复制请求，如果是客户端服务实例发送续租请求为 null ,如果是集群节点同步复制为 true
        // overriddenStatus：覆盖状态
        // status：真实的状态
        // lastDirtyTimestamp：客户端保存的最新修改时间戳（脏）


        boolean isFromReplicaNode = "true".equals(isReplication);
        // todo 调用心跳续租方法
        boolean isSuccess = registry.renew(app.getName(), id, isFromReplicaNode);

        // Not found in the registry, immediately ask for a register
        if (!isSuccess) {
            logger.warn("Not Found (Renew): {} - {}", app.getName(), id);
            // 本地注册表中没有相应实例信息，返回404
            // 客户端在发起续租心跳请求后收到服务端返回404，会立即再进行注册
            return Response.status(Status.NOT_FOUND).build();
        }
        // Check if we need to sync based on dirty time stamp, the client
        // instance might have changed some value

        // 校验，如果我们需要根据客服端的最新修改时间戳（脏）同步，客户端实例可能需要更改数据
        Response response;
        if (lastDirtyTimestamp != null && serverConfig.shouldSyncWhenTimestampDiffers()) {
            // 客户端请求中的最新修改时间戳（脏）不为空 且 本地配置的 shouldSyncWhenTimestampDiffers = true 时
            // shouldSyncWhenTimestampDiffers：检查最新修改时间戳（脏）不同时是否同步实例信息
            //  todo 检查本地的和客户端保存的最新修改时间戳（脏），根据具体情况返回相应的请求结果
            response = this.validateDirtyTimestamp(Long.valueOf(lastDirtyTimestamp), isFromReplicaNode);
            // Store the overridden status since the validation found out the node that replicates wins
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()
                    && (overriddenStatus != null)
                    && !(InstanceStatus.UNKNOWN.name().equals(overriddenStatus))
                    && isFromReplicaNode) {
                // 检查后，如果满足下列三个条件：
                //     1. 是集群节点同步复制到本地
                //     2. 本地注册表中相应实例的最新修改时间戳（脏）小于同步复制过来的
                //     3. 同步复制心跳续租请求中的 overriddenStatus 不为 null 也不为 UNKNOWN
                //  todo 更新本地相应的实例信息（覆盖状态）
                registry.storeOverriddenStatusIfRequired(app.getAppName(), id, InstanceStatus.valueOf(overriddenStatus));
            }
        } else {
            // 返回成功200
            response = Response.ok().build();
        }
        logger.debug("Found (Renew): {} - {}; reply status={}", app.getName(), id, response.getStatus());
        return response;
    }

    /**
     * Handles {@link InstanceStatus} updates.
     *
     * <p>
     * The status updates are normally done for administrative purposes to
     * change the instance status between {@link InstanceStatus#UP} and
     * {@link InstanceStatus#OUT_OF_SERVICE} to select or remove instances for
     * receiving traffic.
     * </p>
     *
     * @param newStatus
     *            the new status of the instance.
     * @param isReplication
     *            a header parameter containing information whether this is
     *            replicated from other nodes.
     * @param lastDirtyTimestamp
     *            last timestamp when this instance information was updated.
     * @return response indicating whether the operation was a success or
     *         failure.
     *     处理客户端状态修改请求
     */
    @PUT
    @Path("status")
    public Response statusUpdate(
            @QueryParam("value") String newStatus,
            @HeaderParam(PeerEurekaNode.HEADER_REPLICATION) String isReplication,
            @QueryParam("lastDirtyTimestamp") String lastDirtyTimestamp) {
        try {
            if (registry.getInstanceByAppAndId(app.getName(), id) == null) {
                logger.warn("Instance not found: {}/{}", app.getName(), id);
                // 查询本地注册表中的实例信息，如果查询不到返回404
                return Response.status(Status.NOT_FOUND).build();
            }
            // todo 处理变更状态
            boolean isSuccess = registry.statusUpdate(app.getName(), id,
                    InstanceStatus.valueOf(newStatus), lastDirtyTimestamp,
                    "true".equals(isReplication));

            if (isSuccess) {
                logger.info("Status updated: {} - {} - {}", app.getName(), id, newStatus);
                // 处理成功返回200
                return Response.ok().build();
            } else {
                logger.warn("Unable to update status: {} - {} - {}", app.getName(), id, newStatus);
                // 处理成功返回500
                return Response.serverError().build();
            }
        } catch (Throwable e) {
            logger.error("Error updating instance {} for status {}", id,
                    newStatus);
            // 处理异常返回500
            return Response.serverError().build();
        }
    }

    /**
     * Removes status override for an instance, set with
     * {@link #statusUpdate(String, String, String)}.
     *
     * @param isReplication
     *            a header parameter containing information whether this is
     *            replicated from other nodes.
     * @param lastDirtyTimestamp
     *            last timestamp when this instance information was updated.
     * @return response indicating whether the operation was a success or
     *         failure.
     *    处理客户端删除overridden状态请求
     */
    // isReplication：是否是集群节点同步复制
    // newStatusValue：客户端发起删除状态时，这里为 null
    // lastDirtyTimestamp：最新修改时间戳（脏）
    @DELETE
    @Path("status")
    public Response deleteStatusUpdate(
            @HeaderParam(PeerEurekaNode.HEADER_REPLICATION) String isReplication,
            @QueryParam("value") String newStatusValue,
            @QueryParam("lastDirtyTimestamp") String lastDirtyTimestamp) {
        try {
            // 查询本地注册表中的服务实例信息，如果查询不到返回404
            if (registry.getInstanceByAppAndId(app.getName(), id) == null) {
                logger.warn("Instance not found: {}/{}", app.getName(), id);
                return Response.status(Status.NOT_FOUND).build();
            }

            // 客户端发过来的 newStatusValue = null ，所以 newStatus = UNKNOWN
            InstanceStatus newStatus = newStatusValue == null ? InstanceStatus.UNKNOWN : InstanceStatus.valueOf(newStatusValue);
            // todo 处理删除状态
            boolean isSuccess = registry.deleteStatusOverride(app.getName(), id,
                    newStatus, lastDirtyTimestamp, "true".equals(isReplication));

            if (isSuccess) {
                logger.info("Status override removed: {} - {}", app.getName(), id);
                // 处理成功返回200
                return Response.ok().build();
            } else {
                logger.warn("Unable to remove status override: {} - {}", app.getName(), id);
                // 处理失败返回500
                return Response.serverError().build();
            }
        } catch (Throwable e) {
            logger.error("Error removing instance's {} status override", id);
            // 处理异常返回500
            return Response.serverError().build();
        }
    }

    /**
     * Updates user-specific metadata information. If the key is already available, its value will be overwritten.
     * If not, it will be added.
     * @param uriInfo - URI information generated by jersey.
     * @return response indicating whether the operation was a success or
     *         failure.
     */
    @PUT
    @Path("metadata")
    public Response updateMetadata(@Context UriInfo uriInfo) {
        try {
            InstanceInfo instanceInfo = registry.getInstanceByAppAndId(app.getName(), id);
            // ReplicationInstance information is not found, generate an error
            if (instanceInfo == null) {
                logger.warn("Cannot find instance while updating metadata for instance {}/{}", app.getName(), id);
                return Response.status(Status.NOT_FOUND).build();
            }
            MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
            Set<Entry<String, List<String>>> entrySet = queryParams.entrySet();
            Map<String, String> metadataMap = instanceInfo.getMetadata();
            // Metadata map is empty - create a new map
            if (Collections.emptyMap().getClass().equals(metadataMap.getClass())) {
                metadataMap = new ConcurrentHashMap<>();
                InstanceInfo.Builder builder = new InstanceInfo.Builder(instanceInfo);
                builder.setMetadata(metadataMap);
                instanceInfo = builder.build();
            }
            // Add all the user supplied entries to the map
            for (Entry<String, List<String>> entry : entrySet) {
                metadataMap.put(entry.getKey(), entry.getValue().get(0));
            }
            registry.register(instanceInfo, false);
            return Response.ok().build();
        } catch (Throwable e) {
            logger.error("Error updating metadata for instance {}", id, e);
            return Response.serverError().build();
        }

    }

    /**
     * Handles cancellation of leases for this particular instance.
     *
     * @param isReplication
     *            a header parameter containing information whether this is
     *            replicated from other nodes.
     * @return response indicating whether the operation was a success or
     *         failure.
     *     服务主动下架
     */
    // isReplication：是否是集群节点间的同步复制
    @DELETE
    public Response cancelLease(
            @HeaderParam(PeerEurekaNode.HEADER_REPLICATION) String isReplication) {
        try {
            // todo 处理客户端下架
            boolean isSuccess = registry.cancel(app.getName(), id,
                "true".equals(isReplication));

            if (isSuccess) {
                logger.debug("Found (Cancel): {} - {}", app.getName(), id);
                // 处理成功返回200
                return Response.ok().build();
            } else {
                logger.info("Not Found (Cancel): {} - {}", app.getName(), id);
                // 处理失败返回404
                return Response.status(Status.NOT_FOUND).build();
            }
        } catch (Throwable e) {
            logger.error("Error (cancel): {} - {}", app.getName(), id, e);
            // 处理异常返回500
            return Response.serverError().build();
        }

    }

    private Response validateDirtyTimestamp(Long lastDirtyTimestamp,
                                            boolean isReplication) {
        // 获取本地相应实例信息
        InstanceInfo appInfo = registry.getInstanceByAppAndId(app.getName(), id, false);
        if (appInfo != null) {
            // 客户端实例续租请求中的最新修改时间戳（脏） 和 本地注册表中实例的最新修改时间戳（脏） 不相等时
            if ((lastDirtyTimestamp != null) && (!lastDirtyTimestamp.equals(appInfo.getLastDirtyTimestamp()))) {
                Object[] args = {id, appInfo.getLastDirtyTimestamp(), lastDirtyTimestamp, isReplication};

                if (lastDirtyTimestamp > appInfo.getLastDirtyTimestamp()) {
                    logger.debug(
                            "Time to sync, since the last dirty timestamp differs -"
                                    + " ReplicationInstance id : {},Registry : {} Incoming: {} Replication: {}",
                            args);
                    // 如果 客户端实例续租请求中的最新修改时间戳（脏） 大于 本地注册表中相应实例的最新修改时间戳（脏）
                    // 返回404，让客户端立即发起注册给当前服务端更新相应实例信息
                    return Response.status(Status.NOT_FOUND).build();
                } else if (appInfo.getLastDirtyTimestamp() > lastDirtyTimestamp) {
                    // In the case of replication, send the current instance info in the registry for the
                    // replicating node to sync itself with this one.
                    // 如果 客户端实例续租请求中的最新修改时间戳（脏） 小于 本地注册表中相应实例的最新修改时间戳（脏）
                    // 客户端因角色不同处理可能有下列情况：
                    //     1. 如果是集群节点，同步复制给当前服务端，则当前服务端返回409且响应体中包含本地注册表的相应实例信息，让集群节点更新相应信息
                    //     2. 如果是服务实例，为自己发起心跳续租请求，则当前服务端返回成功200
                    if (isReplication) {
                        logger.debug(
                                "Time to sync, since the last dirty timestamp differs -"
                                        + " ReplicationInstance id : {},Registry : {} Incoming: {} Replication: {}",
                                args);
                        return Response.status(Status.CONFLICT).entity(appInfo).build();
                    } else {
                        return Response.ok().build();
                    }
                }
            }

        }
        // 返回成功200
        return Response.ok().build();
    }
}
