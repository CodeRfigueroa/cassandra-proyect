/**
 * Copyright 2012 Impetus Infotech.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.impetus.kundera.persistence.context;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.impetus.kundera.graph.Node;

/**
 * Test case for {@link FlushStack}
 * 
 * @author amresh.singh
 */
public class FlushStackTest
{

    FlushStack fs;

    FlushManager flushManager;

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception
    {
        flushManager = new FlushManager();

        fs = flushManager.getFlushStack();
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception
    {
    }

    @Test
    public void testFlushStackPush()
    {
        PersistenceCache pc = new PersistenceCache();
        fs.push(new Node("A", new Object(), pc));
        fs.push(new Node("B", new Object(), pc));
        fs.push(new Node("C", new Object(), pc));
        fs.push(new Node("D", new Object(), pc));

        Assert.assertEquals(4, fs.size());
    }

}
