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

import com.impetus.kundera.property.PropertyAccessException;
import com.impetus.kundera.property.PropertyAccessor;

/**
 * The Class IntegerAccessor.
 * 
 * @author animesh.kumar
 */
public class IntegerAccessor implements PropertyAccessor<Integer>
{

    /* @see com.impetus.kundera.property.PropertyAccessor#fromBytes(byte[]) */
    /*
     * (non-Javadoc)
     * 
     * @see com.impetus.kundera.property.PropertyAccessor#fromBytes(byte[])
     */
    @Override
    public final Integer fromBytes(Class targetClass, byte[] b)
    {
        return ((b[0] << 24) + ((b[1] & 0xFF) << 16) + ((b[2] & 0xFF) << 8) + (b[3] & 0xFF));
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.impetus.kundera.property.PropertyAccessor#toBytes(java.lang.Object)
     */
    @Override
    public final byte[] toBytes(Object val)
    {
        if (val != null)
        {
            Integer value = (Integer) (val);
            return new byte[] { (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8),
                    (byte) value.intValue() };
        }
        return null;
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
    public Integer fromString(Class targetClass, String s)
    {
        try
        {
            Integer i = new Integer(s);
            return i;
        }
        catch (NumberFormatException e)
        {
            throw new PropertyAccessException(e);
        }
    }

    public Integer getInstance(Class<?> clazz)
    {
        return Integer.MAX_VALUE;
    }
}
