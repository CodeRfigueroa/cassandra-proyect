/*******************************************************************************
 * * Copyright 2012 Impetus Infotech.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 ******************************************************************************/
package com.impetus.kundera.property.accessor;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.impetus.kundera.property.PropertyAccessException;
import com.impetus.kundera.property.PropertyAccessor;

/**
 * The Class ByteAccessor.
 * 
 * @author Amresh Singh
 */
public class ByteAccessor implements PropertyAccessor<Byte>
{

    /** The log. */
    private static Logger log = LoggerFactory.getLogger(ByteAccessor.class);

    /*
     * (non-Javadoc)
     * 
     * @see com.impetus.kundera.property.PropertyAccessor#fromBytes(byte[])
     */
    @Override
    public Byte fromBytes(Class targetClass, byte[] b)
    {
        try
        {
            // return new Byte(new String(b, Constants.ENCODING));
            return (ByteBuffer.wrap(b).get());
        }
        catch (NumberFormatException e)
        {
            log.warn("number format exception caught!,returning null!");
            return null;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.impetus.kundera.property.PropertyAccessor#toBytes(java.lang.Object)
     */
    @Override
    public byte[] toBytes(Object object)
    {
        Byte b = (Byte) object;
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.put(b);
        return buffer.array();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.impetus.kundera.property.PropertyAccessor#toString(java.lang.Object)
     */
    @Override
    public String toString(Object object)
    {
        return object.toString();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.impetus.kundera.property.PropertyAccessor#fromString(java.lang.String
     * )
     */
    @Override
    public Byte fromString(Class targetClass, String s)
    {
        try
        {
            Byte b = new Byte(s);
            return b;
        }
        catch (NumberFormatException e)
        {
            throw new PropertyAccessException(e);
        }
    }

    public Byte getInstance(Class<?> clazz)
    {
        return Byte.MAX_VALUE;
    }
}
