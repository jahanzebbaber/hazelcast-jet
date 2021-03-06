/*
 * Copyright (c) 2008-2019, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.jet.examples.helloworld;

import com.hazelcast.function.ComparatorEx;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.Job;
import com.hazelcast.jet.aggregate.AggregateOperations;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.config.ProcessingGuarantee;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.test.TestSources;
import com.hazelcast.jet.server.JetBootstrap;
import com.hazelcast.map.IMap;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.hazelcast.jet.Util.entry;

/**
 * Demonstrates a simple job which calculates the top 10 numbers from a
 * stream of random numbers.
 */
public class HelloWorld {

    private static final String KEY = "top10";
    private static Logger LOGGER = Logger.getLogger(HelloWorld.class);

    private static Pipeline buildPipeline(String mapName) {
        Pipeline p = Pipeline.create();
        p.drawFrom(TestSources.itemStream(10, (ts, seq) -> nextRandomNumber()))
         .withIngestionTimestamps()
         .rollingAggregate(AggregateOperations.topN(10, ComparatorEx.comparingLong(l -> l)))
         .map(l -> entry(KEY, l))
         .drainTo(Sinks.map(mapName))
         .setLocalParallelism(1);
        return p;
    }

    private static long nextRandomNumber() {
        return ThreadLocalRandom.current().nextLong();
    }

    public static void main(String[] args) throws InterruptedException {
        JetInstance jet = JetBootstrap.getInstance();

        String mapName = "top10_" + UUID.randomUUID().toString();
        Pipeline p = buildPipeline(mapName);

        JobConfig config = new JobConfig();
        config.setName("hello-world");
        config.setProcessingGuarantee(ProcessingGuarantee.EXACTLY_ONCE);
        Job job = jet.newJob(p, config);

        LOGGER.info("Generating a stream of random numbers and calculating the top 10");
        LOGGER.info("The results will be written to a distributed map");
        while (true) {
            IMap<String, List<Long>> top10Map = jet.getMap(mapName);

            List<Long> top10numbers = top10Map.get(KEY);
            if (top10numbers != null) {
                LOGGER.info("Top 10 random numbers observed so far in the stream are: ");
                for (int i = 0; i < top10numbers.size(); i++) {
                    LOGGER.info(String.format("%d. %,d", i, top10numbers.get(i)));
                }
            }
            Thread.sleep(1000);
        }
    }

}
