/*
 * Copyright 2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.particleframework.management.health.indicator.discovery;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import org.particleframework.context.annotation.Requires;
import org.particleframework.discovery.DiscoveryClient;
import org.particleframework.discovery.ServiceInstance;
import org.particleframework.health.HealthStatus;
import org.particleframework.management.health.indicator.HealthIndicator;
import org.particleframework.management.health.indicator.HealthResult;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A health indicator for the discovery client
 *
 * @author graemerocher
 * @since 1.0
 */
@Requires(beans = DiscoveryClient.class)
@Singleton
public class DiscoveryClientHealthIndicator implements HealthIndicator {
    private final DiscoveryClient discoveryClient;

    public DiscoveryClientHealthIndicator(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    @Override
    public Publisher<HealthResult> getResult() {
        return Flowable.fromPublisher(discoveryClient.getServiceIds())
                .flatMap((Function<List<String>, Publisher<HealthResult>>) ids -> {
                    List<Flowable<Map<String, List<ServiceInstance>>>> serviceMap = ids.stream()
                            .map(id -> {
                                Flowable<List<ServiceInstance>> serviceList = Flowable.fromPublisher(discoveryClient.getInstances(id));
                                return serviceList
                                        .map(serviceInstances -> Collections.singletonMap(id, serviceInstances));
                            })
                            .collect(Collectors.toList());
                    Flowable<Map<String, List<ServiceInstance>>> mergedServiceMap = Flowable.merge(serviceMap);

                    return mergedServiceMap.reduce(new LinkedHashMap<String, List<ServiceInstance>>(), (allServiceMap, service) -> {
                        allServiceMap.putAll(service);
                        return allServiceMap;
                    }).map(details -> {
                        HealthResult.Builder builder = HealthResult.builder(discoveryClient.getDescription(), HealthStatus.UP);
                        Stream<Map.Entry<String, List<ServiceInstance>>> entryStream = details.entrySet().stream();
                        Map<String, Object> value = entryStream.collect(
                                Collectors.toMap(Map.Entry::getKey, entry ->
                                        entry
                                            .getValue()
                                            .stream()
                                            .map(ServiceInstance::getURI)
                                            .collect(Collectors.toList())
                                )
                        );

                        builder.details(Collections.singletonMap(
                                "services", value
                        ));
                        return builder.build();
                    }).toFlowable();
                }).onErrorReturn(throwable -> {
                    HealthResult.Builder builder = HealthResult.builder(discoveryClient.getDescription(), HealthStatus.DOWN);
                    builder.exception(throwable);
                    return builder.build();
                });
    }
}