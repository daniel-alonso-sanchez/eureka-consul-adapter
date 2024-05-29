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

import static java.util.stream.Collectors.toList;

import java.util.List;

import javax.ws.rs.QueryParam;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import at.twinformatics.eureka.adapter.consul.mapper.InstanceInfoMapper;
import at.twinformatics.eureka.adapter.consul.model.ServiceHealth;
import at.twinformatics.eureka.adapter.consul.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import rx.Single;

@Controller
@RequiredArgsConstructor
@Slf4j
public class HealthController extends BaseConsulController {



    private static final String QUERY_PARAM_WAIT = "wait";
    private static final String QUERY_PARAM_INDEX = "index";

    private final RegistrationService registrationService;
    private final InstanceInfoMapper instanceInfoMapper;


    @GetMapping(value = "/v1/health/service/{appName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Single<ResponseEntity<List<ServiceHealth>>> getServiceHealth(@PathVariable("appName") String appName,
                                                                        @QueryParam(QUERY_PARAM_WAIT) String wait,
                                                                        @QueryParam(QUERY_PARAM_INDEX) Long index) {
        Assert.isTrue(appName != null, "service name can not be null");
        return registrationService.getHealthService(appName, getWaitMillis(wait), index)
                .map(item -> {
                    List<ServiceHealth> services = item.getItem().stream()
                            .map(instanceInfoMapper::mapToHealth).collect(toList());
                    return createResponseEntity(services, item.getChangeIndex());
                });
    }

}
