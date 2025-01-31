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

package org.apache.shardingsphere.elasticjob.cloud.scheduler.state.running;

import org.apache.shardingsphere.elasticjob.cloud.config.CloudJobExecutionType;
import org.apache.shardingsphere.elasticjob.cloud.scheduler.fixture.CloudJsonConstants;
import org.apache.shardingsphere.elasticjob.cloud.scheduler.fixture.TaskNode;
import org.apache.shardingsphere.elasticjob.infra.context.ExecutionType;
import org.apache.shardingsphere.elasticjob.infra.context.TaskContext;
import org.apache.shardingsphere.elasticjob.infra.context.TaskContext.MetaInfo;
import org.apache.shardingsphere.elasticjob.reg.base.CoordinatorRegistryCenter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public final class RunningServiceTest {
    
    private TaskContext taskContext;
    
    private TaskContext taskContextT;
    
    @Mock
    private CoordinatorRegistryCenter regCenter;
    
    private RunningService runningService;
    
    @BeforeEach
    public void setUp() {
        when(regCenter.get("/config/job/test_job")).thenReturn(CloudJsonConstants.getJobJson(CloudJobExecutionType.DAEMON));
        when(regCenter.get("/config/job/test_job_t")).thenReturn(CloudJsonConstants.getJobJson("test_job_t"));
        runningService = new RunningService(regCenter);
        taskContext = TaskContext.from(TaskNode.builder().build().getTaskNodeValue());
        taskContextT = TaskContext.from(TaskNode.builder().jobName("test_job_t").build().getTaskNodeValue());
        runningService.add(taskContext);
        runningService.add(taskContextT);
        assertThat(runningService.getAllRunningDaemonTasks().size(), is(1));
        assertThat(runningService.getAllRunningTasks().size(), is(2));
        String path = RunningNode.getRunningTaskNodePath(taskContext.getMetaInfo().toString());
        verify(regCenter).isExisted(path);
        verify(regCenter).persist(path, taskContext.getId());
    }
    
    @AfterEach
    public void tearDown() {
        runningService.clear();
    }
    
    @Test
    public void assertStart() {
        TaskNode taskNode1 = TaskNode.builder().jobName("test_job").shardingItem(0).slaveId("111").type(ExecutionType.READY).uuid(UUID.randomUUID().toString()).build();
        TaskNode taskNode2 = TaskNode.builder().jobName("test_job").shardingItem(1).slaveId("222").type(ExecutionType.FAILOVER).uuid(UUID.randomUUID().toString()).build();
        when(regCenter.getChildrenKeys(RunningNode.ROOT)).thenReturn(Collections.singletonList("test_job"));
        when(regCenter.getChildrenKeys(RunningNode.getRunningJobNodePath("test_job"))).thenReturn(Arrays.asList(taskNode1.getTaskNodePath(), taskNode2.getTaskNodePath()));
        when(regCenter.get(RunningNode.getRunningTaskNodePath(taskNode1.getTaskNodePath()))).thenReturn(taskNode1.getTaskNodeValue());
        when(regCenter.get(RunningNode.getRunningTaskNodePath(taskNode2.getTaskNodePath()))).thenReturn(taskNode2.getTaskNodeValue());
        runningService.start();
        assertThat(runningService.getAllRunningDaemonTasks().size(), is(2));
    }
    
    @Test
    public void assertAddWithoutData() {
        assertThat(runningService.getRunningTasks("test_job").size(), is(1));
        assertThat(runningService.getRunningTasks("test_job").iterator().next(), is(taskContext));
        assertThat(runningService.getRunningTasks("test_job_t").size(), is(1));
        assertThat(runningService.getRunningTasks("test_job_t").iterator().next(), is(taskContextT));
    }
    
    @Test
    public void assertAddWithData() {
        when(regCenter.get("/config/job/other_job")).thenReturn(CloudJsonConstants.getJobJson("other_job"));
        TaskNode taskNode = TaskNode.builder().jobName("other_job").build();
        runningService.add(TaskContext.from(taskNode.getTaskNodeValue()));
        assertThat(runningService.getRunningTasks("other_job").size(), is(1));
        assertThat(runningService.getRunningTasks("other_job").iterator().next(), is(TaskContext.from(taskNode.getTaskNodeValue())));
    }
    
    @Test
    public void assertUpdateIdle() {
        runningService.updateIdle(taskContext, true);
        assertThat(runningService.getRunningTasks("test_job").size(), is(1));
        assertTrue(runningService.getRunningTasks("test_job").iterator().next().isIdle());
    }
    
    @Test
    public void assertRemoveByJobName() {
        runningService.remove("test_job");
        assertTrue(runningService.getRunningTasks("test_job").isEmpty());
        verify(regCenter).remove(RunningNode.getRunningJobNodePath("test_job"));
        runningService.remove("test_job_t");
        assertTrue(runningService.getRunningTasks("test_job_t").isEmpty());
    }
    
    @Test
    public void assertRemoveByTaskContext() {
        when(regCenter.isExisted(RunningNode.getRunningJobNodePath("test_job"))).thenReturn(true);
        when(regCenter.getChildrenKeys(RunningNode.getRunningJobNodePath("test_job"))).thenReturn(Collections.emptyList());
        runningService.remove(taskContext);
        assertTrue(runningService.getRunningTasks("test_job").isEmpty());
        verify(regCenter).remove(RunningNode.getRunningTaskNodePath(taskContext.getMetaInfo().toString()));
        runningService.remove(taskContextT);
        assertTrue(runningService.getRunningTasks("test_job_t").isEmpty());
    }
    
    @Test
    public void assertIsJobRunning() {
        assertTrue(runningService.isJobRunning("test_job"));
    }
    
    @Test
    public void assertIsTaskRunning() {
        assertTrue(runningService.isTaskRunning(MetaInfo.from(TaskNode.builder().build().getTaskNodePath())));
    }
    
    @Test
    public void assertIsTaskNotRunning() {
        assertFalse(runningService.isTaskRunning(MetaInfo.from(TaskNode.builder().shardingItem(2).build().getTaskNodePath())));
    }
    
    @Test
    public void assertMappingOperate() {
        String taskId = TaskNode.builder().build().getTaskNodeValue();
        assertNull(runningService.popMapping(taskId));
        runningService.addMapping(taskId, "localhost");
        assertThat(runningService.popMapping(taskId), is("localhost"));
        assertNull(runningService.popMapping(taskId));
    }
    
    @Test
    public void assertClear() {
        assertFalse(runningService.getRunningTasks("test_job").isEmpty());
        runningService.addMapping(TaskNode.builder().build().getTaskNodeValue(), "localhost");
        runningService.clear();
        assertTrue(runningService.getRunningTasks("test_job").isEmpty());
        assertNull(runningService.popMapping(TaskNode.builder().build().getTaskNodeValue()));
    }
}
