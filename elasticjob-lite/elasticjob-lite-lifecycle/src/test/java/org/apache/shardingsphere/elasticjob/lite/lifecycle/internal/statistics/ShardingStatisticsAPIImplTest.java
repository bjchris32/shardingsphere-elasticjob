/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.elasticjob.lite.lifecycle.internal.statistics;

import org.apache.shardingsphere.elasticjob.lite.lifecycle.api.ShardingStatisticsAPI;
import org.apache.shardingsphere.elasticjob.lite.lifecycle.domain.ShardingInfo;
import org.apache.shardingsphere.elasticjob.reg.base.CoordinatorRegistryCenter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public final class ShardingStatisticsAPIImplTest {
    
    private ShardingStatisticsAPI shardingStatisticsAPI;

    // TODO We should not use `Mock.Strictness.LENIENT` here, but the default. This is a flaw in the unit test design.
    @Mock(strictness = Mock.Strictness.LENIENT)
    private CoordinatorRegistryCenter regCenter;
    
    @BeforeEach
    public void setUp() {
        shardingStatisticsAPI = new ShardingStatisticsAPIImpl(regCenter);
    }
    
    @Test
    public void assertGetShardingInfo() {
        when(regCenter.getChildrenKeys("/test_job/sharding")).thenReturn(Arrays.asList("0", "1", "2", "3"));
        when(regCenter.get("/test_job/sharding/0/instance")).thenReturn("ip1@-@1234");
        when(regCenter.get("/test_job/sharding/1/instance")).thenReturn("ip2@-@2341");
        when(regCenter.get("/test_job/sharding/2/instance")).thenReturn("ip3@-@3412");
        when(regCenter.get("/test_job/sharding/3/instance")).thenReturn("ip4@-@4123");
        when(regCenter.get("/test_job/instances/ip1@-@1234")).thenReturn("jobInstanceId: ip1@-@1234\nserverIp: ip1\n");
        when(regCenter.get("/test_job/instances/ip2@-@2341")).thenReturn("jobInstanceId: ip2@-@2341\nserverIp: ip2\n");
        when(regCenter.get("/test_job/instances/ip3@-@3412")).thenReturn("jobInstanceId: ip3@-@3412\nserverIp: ip3\n");
        when(regCenter.get("/test_job/instances/ip4@-@4123")).thenReturn("jobInstanceId: ip4@-@4123\nserverIp: ip4\n");
        when(regCenter.isExisted("/test_job/instances/ip4@-@4123")).thenReturn(true);
        when(regCenter.isExisted("/test_job/sharding/0/running")).thenReturn(true);
        when(regCenter.isExisted("/test_job/sharding/1/running")).thenReturn(false);
        when(regCenter.isExisted("/test_job/sharding/2/running")).thenReturn(false);
        when(regCenter.isExisted("/test_job/sharding/3/running")).thenReturn(false);
        when(regCenter.isExisted("/test_job/sharding/0/failover")).thenReturn(false);
        when(regCenter.isExisted("/test_job/sharding/1/failover")).thenReturn(true);
        when(regCenter.isExisted("/test_job/sharding/2/disabled")).thenReturn(true);
        int i = 0;
        for (ShardingInfo each : shardingStatisticsAPI.getShardingInfo("test_job")) {
            i++;
            assertThat(each.getItem(), is(i - 1));
            switch (i) {
                case 1:
                    assertThat(each.getStatus(), is(ShardingInfo.ShardingStatus.RUNNING));
                    assertThat(each.getServerIp(), is("ip1"));
                    assertThat(each.getInstanceId(), is("ip1@-@1234"));
                    break;
                case 2:
                    assertTrue(each.isFailover());
                    assertThat(each.getStatus(), is(ShardingInfo.ShardingStatus.SHARDING_FLAG));
                    assertThat(each.getServerIp(), is("ip2"));
                    assertThat(each.getInstanceId(), is("ip2@-@2341"));
                    break;
                case 3:
                    assertThat(each.getStatus(), is(ShardingInfo.ShardingStatus.DISABLED));
                    assertThat(each.getServerIp(), is("ip3"));
                    assertThat(each.getInstanceId(), is("ip3@-@3412"));
                    break;
                case 4:
                    assertThat(each.getStatus(), is(ShardingInfo.ShardingStatus.PENDING));
                    assertThat(each.getServerIp(), is("ip4"));
                    assertThat(each.getInstanceId(), is("ip4@-@4123"));
                    break;
                default:
                    break;
            }
        }
    }
}
