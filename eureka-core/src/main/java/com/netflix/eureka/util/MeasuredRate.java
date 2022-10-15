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
package com.netflix.eureka.util;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for getting a count in last X milliseconds.
 *
 * @author Karthik Ranganathan,Greg Kim
 */
// 除了这里使用这个计数任务工具类
// 还有 PeerAwareInstanceRegistryImpl.numberOfReplicationsLastMin 字段也使用该工具类
// numberOfReplicationsLastMin：服务端统计最后一分钟同步复制给集群节点的操作数
public class MeasuredRate {
    private static final Logger logger = LoggerFactory.getLogger(MeasuredRate.class);
    // 最近一分钟（上一分钟）的计数
    // getCount() 返回该计数
    private final AtomicLong lastBucket = new AtomicLong(0);
    // 当前一分钟正在统计的计数
    private final AtomicLong currentBucket = new AtomicLong(0);
    // 定时任务执行间隔
    private final long sampleInterval;
    private final Timer timer;

    private volatile boolean isActive;

    /**
     * @param sampleInterval in milliseconds
     */
    public MeasuredRate(long sampleInterval) {
        this.sampleInterval = sampleInterval;
        this.timer = new Timer("Eureka-MeasureRateTimer", true);
        this.isActive = false;
    }

    public synchronized void start() {
        if (!isActive) {
            // 开启任务
            // 固定时间重复执行，默认60s
            timer.schedule(new TimerTask() {

                @Override
                public void run() {
                    try {
                        // Zero out the current bucket.
                        // 每当到了任务刚刚开始的时候
                        // 记录上一分钟的计数
                        // 清零当前一分钟正在统计的计数
                        lastBucket.set(currentBucket.getAndSet(0));
                    } catch (Throwable e) {
                        logger.error("Cannot reset the Measured Rate", e);
                    }
                }
            }, sampleInterval, sampleInterval);

            isActive = true;
        }
    }

    public synchronized void stop() {
        if (isActive) {
            timer.cancel();
            isActive = false;
        }
    }

    /**
     * Returns the count in the last sample interval.
     */
    public long getCount() {
        return lastBucket.get();
    }

    /**
     * Increments the count in the current sample interval.
     */
    public void increment() {
        currentBucket.incrementAndGet();
    }
}
