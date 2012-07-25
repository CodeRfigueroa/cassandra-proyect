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
package com.impetus.kundera.db;

/**
 * The Class RelationHolder.
 * 
 * @author vivek.mishra
 */
public class RelationHolder
{

    /** The relation name. */
    private String relationName;

    /** The relation value. */
    private String relationValue;

    /**
     * Instantiates a new relation holder.
     * 
     * @param relationName
     *            the relation name
     * @param relationValue
     *            the relation value
     */
    public RelationHolder(String relationName, String relationValue)
    {
        this.relationName = relationName;
        this.relationValue = relationValue;
    }

    /**
     * Gets the relation name.
     * 
     * @return the relationName
     */
    public String getRelationName()
    {
        return relationName;
    }

    /**
     * Gets the relation value.
     * 
     * @return the relationValue
     */
    public String getRelationValue()
    {
        return relationValue;
    }

}
