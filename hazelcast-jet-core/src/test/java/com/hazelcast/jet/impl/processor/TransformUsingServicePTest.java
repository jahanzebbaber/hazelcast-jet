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

package com.hazelcast.jet.impl.processor;

import com.hazelcast.jet.Traverser;
import com.hazelcast.jet.core.ProcessorSupplier;
import com.hazelcast.jet.core.ResettableSingletonTraverser;
import com.hazelcast.jet.core.test.TestOutbox;
import com.hazelcast.jet.core.test.TestProcessorContext;
import com.hazelcast.jet.core.test.TestProcessorSupplierContext;
import com.hazelcast.jet.function.TriFunction;
import com.hazelcast.jet.pipeline.ServiceFactory;
import com.hazelcast.test.HazelcastSerialClassRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.hazelcast.jet.impl.processor.TransformUsingServiceP.supplier;
import static org.junit.Assert.assertEquals;

@RunWith(HazelcastSerialClassRunner.class)
public class TransformUsingServicePTest {

    @Test
    public void when_sharedLocally_then_oneContextInstance() throws Exception {
        testSharing(true);
    }

    @Test
    public void when_notSharedLocally_then_multipleContextInstances() throws Exception {
        testSharing(false);
    }

    private void testSharing(boolean share) throws Exception {
        int[] createCounter = {0};
        int[] destroyCounter = {0};
        ServiceFactory<String> serviceFactory = ServiceFactory.withCreateFn(jet -> "context-" + createCounter[0]++)
                                                              .withDestroyFn(ctx -> destroyCounter[0]++);
        if (share) {
            serviceFactory = serviceFactory.withLocalSharing();
        }
        ProcessorSupplier supplier = supplier(serviceFactory, mapToContext());

        TestOutbox outbox1 = new TestOutbox(1);
        TestOutbox outbox2 = new TestOutbox(1);

        supplier.init(new TestProcessorSupplierContext());
        assertEquals(share ? 1 : 0, createCounter[0]);
        //noinspection SuspiciousToArrayCall
        TransformUsingServiceP[] processors = supplier.get(2).toArray(new TransformUsingServiceP[0]);
        processors[0].init(outbox1, new TestProcessorContext());
        assertEquals(1, createCounter[0]);
        processors[1].init(outbox2, new TestProcessorContext());
        assertEquals(share ? 1 : 2, createCounter[0]);
        assertEquals(share, processors[0].service == processors[1].service);

        processors[0].tryProcess(0, "foo");
        processors[1].tryProcess(0, "foo");

        assertEquals("context-0", outbox1.queue(0).poll());
        assertEquals(share ? "context-0" : "context-1", outbox2.queue(0).poll());

        processors[0].close();
        assertEquals(share ? 0 : 1, destroyCounter[0]);
        processors[1].close();
        assertEquals(share ? 0 : 2, destroyCounter[0]);
        supplier.close(null);
        assertEquals(share ? 1 : 2, destroyCounter[0]);
    }

    @Test
    public void when_nonCooperativeServiceFactory_then_nonCooperativeProcessor() throws Exception {
        testEqualCooperativity(false);
    }

    @Test
    public void when_cooperativeServiceFactory_then_cooperativeProcessor() throws Exception {
        testEqualCooperativity(true);
    }

    private void testEqualCooperativity(boolean cooperative) throws Exception {
        ServiceFactory<String> serviceFactory = ServiceFactory.withCreateFn(jet -> "foo");
        if (!cooperative) {
            serviceFactory = serviceFactory.toNonCooperative();
        }

        ProcessorSupplier supplier = supplier(serviceFactory, mapToContext());
        supplier.init(new TestProcessorSupplierContext());
        assertEquals(cooperative, supplier.get(1).iterator().next().isCooperative());
    }

    private static <T> TriFunction<ResettableSingletonTraverser<T>, T, Object, Traverser<T>> mapToContext() {
        return (traverser, context, item) -> {
            traverser.accept(context);
            return traverser;
        };
    }
}
