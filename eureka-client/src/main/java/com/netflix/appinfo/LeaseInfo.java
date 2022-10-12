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

package com.netflix.appinfo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.thoughtworks.xstream.annotations.XStreamOmitField;

/**
 * Represents the <em>lease</em> information with <em>Eureka</em>.
 *
 * <p>
 * <em>Eureka</em> decides to remove the instance out of its view depending on
 * the duration that is set in
 * {@link EurekaInstanceConfig#getLeaseExpirationDurationInSeconds()} which is
 * held in this lease. The lease also tracks the last time it was renewed.
 * </p>
 *
 * @author Karthik Ranganathan, Greg Kim
 *
 */
@JsonRootName("leaseInfo")
public class LeaseInfo {

    public static final int DEFAULT_LEASE_RENEWAL_INTERVAL = 30;
    public static final int DEFAULT_LEASE_DURATION = 90;

    // Client settings
    // 客户端维护的心跳间隔时间，默认是30s
    // 用于Client 每个30s上报续约一次
    private int renewalIntervalInSecs = DEFAULT_LEASE_RENEWAL_INTERVAL;
    // 客户端维护的租约持续时间（过期时间）  默认是90s
    // 用于Server端，90s内没有收到心跳，就剔除掉对应实例
    private int durationInSecs = DEFAULT_LEASE_DURATION;

    // Server populated
    // 服务端维护的实例注册时间
    private long registrationTimestamp;
    // 服务端维护的实例最近一次更新时间，服务端记录 用于倒计时的起始值
    private long lastRenewalTimestamp;
    // 服务端维护的实例过期清理时间，服务上下线属于比较频繁的操作，但是此时服务实例并未剔除
    private long evictionTimestamp;
    // 服务端维护的实例启动时间
    private long serviceUpTimestamp;

    public static final class Builder {

        @XStreamOmitField
        private LeaseInfo result;

        private Builder() {
            result = new LeaseInfo();
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        /**
         * Sets the registration timestamp.
         *
         * @param ts
         *            time when the lease was first registered.
         * @return the {@link LeaseInfo} builder.
         */
        public Builder setRegistrationTimestamp(long ts) {
            result.registrationTimestamp = ts;
            return this;
        }

        /**
         * Sets the last renewal timestamp of lease.
         *
         * @param ts
         *            time when the lease was last renewed.
         * @return the {@link LeaseInfo} builder.
         */
        public Builder setRenewalTimestamp(long ts) {
            result.lastRenewalTimestamp = ts;
            return this;
        }

        /**
         * Sets the de-registration timestamp.
         *
         * @param ts
         *            time when the lease was removed.
         * @return the {@link LeaseInfo} builder.
         */
        public Builder setEvictionTimestamp(long ts) {
            result.evictionTimestamp = ts;
            return this;
        }

        /**
         * Sets the service UP timestamp.
         *
         * @param ts
         *            time when the leased service marked as UP.
         * @return the {@link LeaseInfo} builder.
         */
        public Builder setServiceUpTimestamp(long ts) {
            result.serviceUpTimestamp = ts;
            return this;
        }

        /**
         * Sets the client specified setting for eviction (e.g. how long to wait
         * without renewal event).
         *
         * @param d
         *            time in seconds after which the lease would expire without
         *            renewa.
         * @return the {@link LeaseInfo} builder.
         */
        public Builder setDurationInSecs(int d) {
            if (d <= 0) {
                result.durationInSecs = DEFAULT_LEASE_DURATION;
            } else {
                result.durationInSecs = d;
            }
            return this;
        }

        /**
         * Sets the client specified setting for renew interval.
         *
         * @param i
         *            the time interval with which the renewals will be renewed.
         * @return the {@link LeaseInfo} builder.
         */
        public Builder setRenewalIntervalInSecs(int i) {
            if (i <= 0) {
                result.renewalIntervalInSecs = DEFAULT_LEASE_RENEWAL_INTERVAL;
            } else {
                result.renewalIntervalInSecs = i;
            }
            return this;
        }

        /**
         * Build the {@link InstanceInfo}.
         *
         * @return the {@link LeaseInfo} information built based on the supplied
         *         information.
         */
        public LeaseInfo build() {
            return result;
        }
    }

    private LeaseInfo() {
    }

    /**
     * TODO: note about renewalTimestamp legacy:
     * The previous change to use Jackson ser/deser changed the field name for lastRenewalTimestamp to renewalTimestamp
     * for serialization, which causes an incompatibility with the jacksonNG codec when the server returns data with
     * field renewalTimestamp and jacksonNG expects lastRenewalTimestamp. Remove this legacy field from client code
     * in a few releases (once servers are updated to a release that generates json with the correct
     * lastRenewalTimestamp).
     */
    @JsonCreator
    public LeaseInfo(@JsonProperty("renewalIntervalInSecs") int renewalIntervalInSecs,
                     @JsonProperty("durationInSecs") int durationInSecs,
                     @JsonProperty("registrationTimestamp") long registrationTimestamp,
                     @JsonProperty("lastRenewalTimestamp") Long lastRenewalTimestamp,
                     @JsonProperty("renewalTimestamp") long lastRenewalTimestampLegacy,  // for legacy
                     @JsonProperty("evictionTimestamp") long evictionTimestamp,
                     @JsonProperty("serviceUpTimestamp") long serviceUpTimestamp) {
        this.renewalIntervalInSecs = renewalIntervalInSecs;
        this.durationInSecs = durationInSecs;
        this.registrationTimestamp = registrationTimestamp;
        this.evictionTimestamp = evictionTimestamp;
        this.serviceUpTimestamp = serviceUpTimestamp;

        if (lastRenewalTimestamp == null) {
            this.lastRenewalTimestamp = lastRenewalTimestampLegacy;
        } else {
            this.lastRenewalTimestamp = lastRenewalTimestamp;
        }
    }

    /**
     * Returns the registration timestamp.
     *
     * @return time in milliseconds since epoch.
     */
    public long getRegistrationTimestamp() {
        return registrationTimestamp;
    }

    /**
     * Returns the last renewal timestamp of lease.
     *
     * @return time in milliseconds since epoch.
     */
    @JsonProperty("lastRenewalTimestamp")
    public long getRenewalTimestamp() {
        return lastRenewalTimestamp;
    }

    /**
     * Returns the de-registration timestamp.
     *
     * @return time in milliseconds since epoch.
     */
    public long getEvictionTimestamp() {
        return evictionTimestamp;
    }

    /**
     * Returns the service UP timestamp.
     *
     * @return time in milliseconds since epoch.
     */
    public long getServiceUpTimestamp() {
        return serviceUpTimestamp;
    }

    /**
     * Returns client specified setting for renew interval.
     *
     * @return time in milliseconds since epoch.
     */
    public int getRenewalIntervalInSecs() {
        return renewalIntervalInSecs;
    }

    /**
     * Returns client specified setting for eviction (e.g. how long to wait w/o
     * renewal event)
     *
     * @return time in milliseconds since epoch.
     */
    public int getDurationInSecs() {
        return durationInSecs;
    }

}
