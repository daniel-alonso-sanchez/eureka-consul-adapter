/**
 * The MIT License
 * Copyright © 2018 Twinformatics GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package at.twinformatics.eureka.adapter.consul.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;

import at.twinformatics.eureka.adapter.consul.mapper.InstanceInfoMapper;
import at.twinformatics.eureka.adapter.consul.mapper.MetadataMapper;
import at.twinformatics.eureka.adapter.consul.mapper.NodeMetadataMapper;
import at.twinformatics.eureka.adapter.consul.mapper.ServiceMetadataMapper;
import at.twinformatics.eureka.adapter.consul.service.RegistrationService;
import at.twinformatics.eureka.adapter.consul.service.ServiceChangeDetector;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;

@Slf4j
@RunWith(SpringRunner.class)
@WebMvcTest(controllers = { HealthController.class, ServiceChangeDetector.class, InstanceInfoMapper.class,
                            RegistrationService.class, MetadataMapper.class })
public class HealthControllerTest {

    private MockMvc mockMvc;

    @MockBean
    private PeerAwareInstanceRegistry registry;

    @Autowired
    private ServiceChangeDetector serviceChangeDetector;

    @Autowired
    private HealthController controller;
    
    @Autowired
    private InstanceInfoMapper instanceInfoMapper;

    private ExecutorService executorService1;

    @Before
    public void setUp() {
        executorService1 = Executors.newSingleThreadExecutor();
        serviceChangeDetector.reset();

        mockMvc = MockMvcBuilders.standaloneSetup(controller).alwaysDo(MockMvcResultHandlers.print()).build();
    }

    @After
    public void tearDown() throws InterruptedException {
        executorService1.shutdown();
        executorService1.awaitTermination(10, TimeUnit.SECONDS);
    }



    @Test
    public void service_sampleService_jsonObject() throws Exception {

        Applications applications = mock2Applications();
        Mockito.when(registry.getApplications()).thenReturn(applications);
        Application ms1 = applications.getRegisteredApplications().get(0);

        InstanceInfo instance1 = mock1Instance();
        ms1.addInstance(instance1);

        Mockito.when(registry.getApplication("ms1")).thenReturn(ms1);

        MetadataMapper metadataMapper = new ServiceMetadataMapper();
        instanceInfoMapper.setMetadataMapper(metadataMapper);

        performAsync("/v1/health/service/ms1?wait=1ms")
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(jsonPath("$[0].Node.Address", Matchers.is("1.2.3.4")))
                .andExpect(jsonPath("$[0].Node.Node", Matchers.is("ms1")))
                .andExpect(jsonPath("$[0].Service.Address", Matchers.is("1.2.3.4")))
                .andExpect(jsonPath("$[0].Service.Service", Matchers.is("ms1")))
                .andExpect(jsonPath("$[0].Service.ID", Matchers.is("1")))
                .andExpect(jsonPath("$[0].Service.Port", Matchers.is(80)))
                .andExpect(jsonPath("$[0].Node.Meta").isEmpty())
                .andExpect(jsonPath("$[0].Service.Meta").isEmpty())
                .andExpect(jsonPath("$[0].Service.Tags", Matchers.is(new JSONArray())));

        InstanceInfo instance2 = mock1Instance("2","1.2.3.5", "ms2.com", 81, true);

        Map<String, String> md = new HashMap<>();
        md.put("k1", "v1");
        md.put("k2", "v2");
        Mockito.when(instance2.getMetadata()).thenReturn(md);

        ms1.addInstance(instance2);

        performAsync("/v1/health/service/ms1?wait=1ms")
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(jsonPath("$[0].Node.Address", Matchers.is("1.2.3.4")))
                .andExpect(jsonPath("$[0].Node.Node", Matchers.is("ms1")))
                .andExpect(jsonPath("$[0].Service.Address", Matchers.is("1.2.3.4")))
                .andExpect(jsonPath("$[0].Service.Service", Matchers.is("ms1")))
                .andExpect(jsonPath("$[0].Service.ID", Matchers.is("1")))
                .andExpect(jsonPath("$[0].Service.Port", Matchers.is(80)))
                .andExpect(jsonPath("$[0].Node.Meta").isEmpty())
                .andExpect(jsonPath("$[0].Service.Meta").isEmpty())
                .andExpect(jsonPath("$[0].Service.Tags", Matchers.is(new JSONArray())))

                .andExpect(jsonPath("$[1].Node.Address", Matchers.is("1.2.3.5")))
                .andExpect(jsonPath("$[1].Node.Node", Matchers.is("ms1")))
                .andExpect(jsonPath("$[1].Service.Address", Matchers.is("1.2.3.5")))
                .andExpect(jsonPath("$[1].Service.Service", Matchers.is("ms1")))
                .andExpect(jsonPath("$[1].Service.ID", Matchers.is("2")))
                .andExpect(jsonPath("$[1].Service.Port", Matchers.is(443)))
                .andExpect(jsonPath("$[1].Node.Meta").isEmpty())
                .andExpect(jsonPath("$[1].Service.Meta.k1", Matchers.is("v1")))
                .andExpect(jsonPath("$[1].Service.Meta.k2", Matchers.is("v2")))
                .andExpect(jsonPath("$[1].Service.Tags", Matchers.is(new JSONArray())));
    }

    @Test
    public void service_sampleService_jsonObject_nodeMetadataMapper() throws Exception{

        Applications applications = mock2Applications();
        Mockito.when(registry.getApplications()).thenReturn(applications);
        Application ms1 = applications.getRegisteredApplications().get(0);

        InstanceInfo instance = mock1Instance();
        ms1.addInstance(instance);

        Mockito.when(registry.getApplication("ms1")).thenReturn(ms1);

        String nodeMetaPrefix = "nodeMeta_";
        MetadataMapper metadataMapper = new NodeMetadataMapper(nodeMetaPrefix);
        instanceInfoMapper.setMetadataMapper(metadataMapper);

        Map<String, String> md = new HashMap<>();
        md.put("k1", "v1");
        md.put("k2", "v2");
        md.put("nodeMeta_k1", "nv1");
        md.put("nodeMeta_k2", "nv2");
        Mockito.when(instance.getMetadata()).thenReturn(md);

        performAsync("/v1/health/service/ms1?wait=1ms")
            .andExpect(content().contentType("application/json;charset=UTF-8"))
            .andExpect(jsonPath("$[0].Node.Address", Matchers.is("1.2.3.4")))
            .andExpect(jsonPath("$[0].Node.Node", Matchers.is("ms1")))
            .andExpect(jsonPath("$[0].Service.Address", Matchers.is("1.2.3.4")))
            .andExpect(jsonPath("$[0].Service.Service", Matchers.is("ms1")))
            .andExpect(jsonPath("$[0].Service.ID", Matchers.is("1")))
            .andExpect(jsonPath("$[0].Service.Port", Matchers.is(80)))
            .andExpect(jsonPath("$[0].Node.Meta.k1", Matchers.is("nv1")))
            .andExpect(jsonPath("$[0].Node.Meta.k2", Matchers.is("nv2")))
            .andExpect(jsonPath("$[0].Service.Meta.k1", Matchers.is("v1")))
            .andExpect(jsonPath("$[0].Service.Meta.k2", Matchers.is("v2")))
            .andExpect(jsonPath("$[0].Service.Tags", Matchers.is(new JSONArray())));

        nodeMetaPrefix = "";
        metadataMapper = new NodeMetadataMapper(nodeMetaPrefix);
        instanceInfoMapper.setMetadataMapper(metadataMapper);

        performAsync("/v1/health/service/ms1?wait=1ms")
            .andExpect(content().contentType("application/json;charset=UTF-8"))
            .andExpect(jsonPath("$[0].Node.Address", Matchers.is("1.2.3.4")))
            .andExpect(jsonPath("$[0].Node.Node", Matchers.is("ms1")))
            .andExpect(jsonPath("$[0].Service.Address", Matchers.is("1.2.3.4")))
            .andExpect(jsonPath("$[0].Service.Service", Matchers.is("ms1")))
            .andExpect(jsonPath("$[0].Service.ID", Matchers.is("1")))
            .andExpect(jsonPath("$[0].Service.Port", Matchers.is(80)))
            .andExpect(jsonPath("$[0].Node.Meta.k1", Matchers.is("v1")))
            .andExpect(jsonPath("$[0].Node.Meta.k2", Matchers.is("v2")))

            .andExpect(jsonPath("$[0].Service.Tags", Matchers.is(new JSONArray())));
    }

    @Test
    public void service_sampleService_jsonObject_preferHostName() throws Exception {

        Applications applications = mock2Applications();
        Mockito.when(registry.getApplications()).thenReturn(applications);
        Application ms1 = applications.getRegisteredApplications().get(0);

        InstanceInfo instance1 = mock1Instance();
        ms1.addInstance(instance1);

        InstanceInfo instance2 = mock1Instance("2","1.2.3.5", "2.ms1.com", 81, true);
        ms1.addInstance(instance2);

        Mockito.when(registry.getApplication("ms1")).thenReturn(ms1);

        instanceInfoMapper.setPreferHostName(true);

        performAsync("/v1/health/service/ms1?wait=1ms")
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(jsonPath("$[0].Node.Address", Matchers.is("ms1.com")))
                .andExpect(jsonPath("$[0].Service.Address", Matchers.is("ms1.com")))

                .andExpect(jsonPath("$[1].Node.Address", Matchers.is("2.ms1.com")))
                .andExpect(jsonPath("$[1].Service.Address", Matchers.is("2.ms1.com")));
    }

    @Test
    public void service_healthEndpoint_jsonObject() throws Exception {

        Applications applications = mock2Applications();
        Mockito.when(registry.getApplications()).thenReturn(applications);
        Application ms1 = applications.getRegisteredApplications().get(0);

        InstanceInfo instance1 = mock1Instance();
        ms1.addInstance(instance1);

        Mockito.when(registry.getApplication("ms1")).thenReturn(ms1);

        MetadataMapper metadataMapper = new ServiceMetadataMapper();
        instanceInfoMapper.setMetadataMapper(metadataMapper);

        performAsync("/v1/health/service/ms1?wait=1ms")
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(jsonPath("$[0].Node.Node", Matchers.is("ms1")))
                .andExpect(jsonPath("$[0].Node.Address", Matchers.is("1.2.3.4")))
                .andExpect(jsonPath("$[0].Node.Meta").isEmpty())
                .andExpect(jsonPath("$[0].Service.ID", Matchers.is("1")))
                .andExpect(jsonPath("$[0].Service.Service", Matchers.is("ms1")))
                .andExpect(jsonPath("$[0].Service.Address", Matchers.is("1.2.3.4")))
                .andExpect(jsonPath("$[0].Service.Port", Matchers.is(80)))
                .andExpect(jsonPath("$[0].Service.Meta").isEmpty())
                .andExpect(jsonPath("$[0].Service.Tags", Matchers.is(new JSONArray())))
                .andExpect(jsonPath("$[0].Checks[0].Node", Matchers.is("ms1")))
                .andExpect(jsonPath("$[0].Checks[0].CheckID", Matchers.is("service:1")))
                .andExpect(jsonPath("$[0].Checks[0].Name", Matchers.is("Service '1' check")))
                .andExpect(jsonPath("$[0].Checks[0].Status", Matchers.is("UP")));

        InstanceInfo instance2 = mock1Instance("2","1.2.3.5", "ms2.com", 81, true);

        Map<String, String> md = new HashMap<>();
        md.put("k1", "v1");
        md.put("k2", "v2");
        Mockito.when(instance2.getMetadata()).thenReturn(md);

        ms1.addInstance(instance2);

        performAsync("/v1/health/service/ms1?wait=1ms")
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(jsonPath("$[0].Node.Node", Matchers.is("ms1")))
                .andExpect(jsonPath("$[0].Node.Address", Matchers.is("1.2.3.4")))
                .andExpect(jsonPath("$[0].Node.Meta").isEmpty())
                .andExpect(jsonPath("$[0].Service.ID", Matchers.is("1")))
                .andExpect(jsonPath("$[0].Service.Service", Matchers.is("ms1")))
                .andExpect(jsonPath("$[0].Service.Address", Matchers.is("1.2.3.4")))
                .andExpect(jsonPath("$[0].Service.Port", Matchers.is(80)))
                .andExpect(jsonPath("$[0].Service.Meta").isEmpty())
                .andExpect(jsonPath("$[0].Service.Tags", Matchers.is(new JSONArray())))
                .andExpect(jsonPath("$[0].Checks[0].Node", Matchers.is("ms1")))
                .andExpect(jsonPath("$[0].Checks[0].CheckID", Matchers.is("service:1")))
                .andExpect(jsonPath("$[0].Checks[0].Name", Matchers.is("Service '1' check")))
                .andExpect(jsonPath("$[0].Checks[0].Status", Matchers.is("UP")))

                .andExpect(jsonPath("$[1].Node.Node", Matchers.is("ms1")))
                .andExpect(jsonPath("$[1].Node.Address", Matchers.is("1.2.3.5")))
                .andExpect(jsonPath("$[1].Node.Meta").isEmpty())
                .andExpect(jsonPath("$[1].Service.ID", Matchers.is("2")))
                .andExpect(jsonPath("$[1].Service.Service", Matchers.is("ms1")))
                .andExpect(jsonPath("$[1].Service.Address", Matchers.is("1.2.3.5")))
                .andExpect(jsonPath("$[1].Service.Port", Matchers.is(443)))
                .andExpect(jsonPath("$[1].Service.Meta.k1", Matchers.is("v1")))
                .andExpect(jsonPath("$[1].Service.Meta.k2", Matchers.is("v2")))
                .andExpect(jsonPath("$[1].Service.Tags", Matchers.is(new JSONArray())))
                .andExpect(jsonPath("$[1].Checks[0].Node", Matchers.is("ms1")))
                .andExpect(jsonPath("$[1].Checks[0].CheckID", Matchers.is("service:2")))
                .andExpect(jsonPath("$[1].Checks[0].Name", Matchers.is("Service '2' check")))
                .andExpect(jsonPath("$[1].Checks[0].Status", Matchers.is("UP")));
    }

    @Test(timeout = 10000)
    public void service_serviceChangesToOtherServices_interruptOnCorrectService() throws Exception {

        Applications applications = mock2Applications();
        Mockito.when(registry.getApplications()).thenReturn(applications);
        Application ms1 = applications.getRegisteredApplications().get(0);

        InstanceInfo instance1 = mock1Instance();
        ms1.addInstance(instance1);

        Mockito.when(registry.getApplication("ms1")).thenReturn(ms1);

        startThread(() -> {
            sleepFor(1000);
            serviceChangeDetector.publish("ms1", 2);
            sleepFor(500);
            serviceChangeDetector.publish("ms2", 1);
            serviceChangeDetector.publish("ms3", 1);
            serviceChangeDetector.publish("ms4", 1);
            sleepFor(500);
            Mockito.when(instance1.getIPAddr()).thenReturn("8.8.8.8");
            serviceChangeDetector.publish("ms1", 3);
        });

        performAsync("/v1/health/service/ms1?wait=30s")
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(header().string("X-Consul-Index", "1"))
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(jsonPath("$[0].Node.Address", Matchers.is("1.2.3.4")));

        performAsync("/v1/health/service/ms1?wait=30s&index=1")
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(header().string("X-Consul-Index", "2"))
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(jsonPath("$[0].Node.Address", Matchers.is("1.2.3.4")));


        performAsync("/v1/health/service/ms1?wait=30s&index=2")
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(header().string("X-Consul-Index", "3"))
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(jsonPath("$[0].Node.Address", Matchers.is("8.8.8.8")));

    }

    @Test(timeout = 3000)
    public void service_eventInterruptsRequestError_isResolved() throws Exception {

        Applications applications = mock2Applications();
        Mockito.when(registry.getApplications()).thenReturn(applications);

        Application ms1 = applications.getRegisteredApplications().get(0);
        InstanceInfo instance1 = mock1Instance();
        ms1.addInstance(instance1);
        Mockito.when(registry.getApplication("ms1")).thenReturn(ms1);

        startThread(() -> {
            sleepFor(500);
            serviceChangeDetector.publish("ms2", 1);
            sleepFor(500);
            serviceChangeDetector.publish("ms2", 2);
            sleepFor(500);
            serviceChangeDetector.publish("ms2", 3);
            sleepFor(500);
            serviceChangeDetector.publish("ms2", 4);
            sleepFor(500);
            serviceChangeDetector.publish("ms2", 5);
        });

        long t1 = System.currentTimeMillis();
        performAsync("/v1/health/service/ms1?wait=2s&index=1")
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(header().string("X-Consul-Index", "1"))
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(jsonPath("$[0].Node.Address", Matchers.is("1.2.3.4")));

        Assert.assertThat(System.currentTimeMillis() - t1, Matchers.is(Matchers.greaterThan(2000L)));

    }

    private Applications mock2Applications() {
        Applications applications = new Applications();
        Application app1 = new Application();
        app1.setName("ms1");
        applications.addApplication(app1);
        Application app2 = new Application();
        app2.setName("ms2");
        applications.addApplication(app2);

        return applications;
    }

    private void startThread(Runnable t) {
        executorService1.submit(t);
    }

    private InstanceInfo mock1Instance() {
        return mock1Instance("1",  "1.2.3.4", "ms1.com", 80, false);
    }

    private InstanceInfo mock1Instance(String id, String ip, String hostName, int port, boolean securePort) {
        InstanceInfo instance1 = Mockito.mock(InstanceInfo.class);
        Mockito.when(instance1.getId()).thenReturn(id);
        Mockito.when(instance1.getAppName()).thenReturn("ms1");
        Mockito.when(instance1.getHostName()).thenReturn(hostName);
        Mockito.when(instance1.getIPAddr()).thenReturn(ip);
        Mockito.when(instance1.getPort()).thenReturn(port);
        Mockito.when(instance1.isPortEnabled(InstanceInfo.PortType.SECURE)).thenReturn(securePort);
        Mockito.when(instance1.getSecurePort()).thenReturn(443);
        Mockito.when(instance1.getStatus()).thenReturn(InstanceInfo.InstanceStatus.UP);
        return instance1;
    }

    private void sleepFor(final int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Assert.fail();
        }
    }
    private ResultActions performAsync(String url) throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn();

        return this.mockMvc.perform(asyncDispatch(mvcResult));
    }
}
