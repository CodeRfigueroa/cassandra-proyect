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

package com.impetus.kundera.persistence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.persistence.FlushModeType;
import javax.persistence.PostPersist;
import javax.persistence.PostRemove;
import javax.persistence.PostUpdate;
import javax.persistence.PrePersist;
import javax.persistence.PreRemove;
import javax.persistence.PreUpdate;
import javax.persistence.Query;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.impetus.kundera.KunderaException;
import com.impetus.kundera.client.Client;
import com.impetus.kundera.client.ClientResolver;
import com.impetus.kundera.client.ClientResolverException;
import com.impetus.kundera.graph.Node;
import com.impetus.kundera.graph.NodeLink;
import com.impetus.kundera.graph.NodeLink.LinkProperty;
import com.impetus.kundera.graph.ObjectGraph;
import com.impetus.kundera.graph.ObjectGraphBuilder;
import com.impetus.kundera.graph.ObjectGraphUtils;
import com.impetus.kundera.lifecycle.states.ManagedState;
import com.impetus.kundera.lifecycle.states.RemovedState;
import com.impetus.kundera.lifecycle.states.TransientState;
import com.impetus.kundera.metadata.KunderaMetadataManager;
import com.impetus.kundera.metadata.model.EntityMetadata;
import com.impetus.kundera.persistence.context.EventLog.EventType;
import com.impetus.kundera.persistence.context.FlushManager;
import com.impetus.kundera.persistence.context.FlushStack;
import com.impetus.kundera.persistence.context.MainCache;
import com.impetus.kundera.persistence.context.PersistenceCache;
import com.impetus.kundera.persistence.context.jointable.JoinTableData;
import com.impetus.kundera.persistence.context.jointable.JoinTableData.OPERATION;
import com.impetus.kundera.persistence.event.EntityEventDispatcher;
import com.impetus.kundera.property.PropertyAccessException;
import com.impetus.kundera.property.PropertyAccessorHelper;
import com.impetus.kundera.query.QueryResolver;

/**
 * The Class PersistenceDelegator.
 */
public class PersistenceDelegator
{

    /** The Constant log. */
    private static final Log log = LogFactory.getLog(PersistenceDelegator.class);

    /** The closed. */
    private boolean closed = false;

    /** The session. */
    private EntityManagerSession session;

    /** The client map. */
    private Map<String, Client> clientMap;

    /** The event dispatcher. */
    private EntityEventDispatcher eventDispatcher;

    /** The is relation via join table. */
    boolean isRelationViaJoinTable;

    private FlushModeType flushMode = FlushModeType.AUTO;

    private ObjectGraphBuilder graphBuilder;

    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // Whether a transaction is in progress
    private boolean isTransactionInProgress;

    private PersistenceCache persistenceCache;

    FlushManager flushManager = new FlushManager();

    private boolean enableFlush;

    /**
     * Instantiates a new persistence delegator.
     * 
     * @param session
     *            the session
     * @param persistenceUnits
     *            the persistence units
     */
    public PersistenceDelegator(EntityManagerSession session, PersistenceCache pc)
    {
        this.session = session;
        eventDispatcher = new EntityEventDispatcher();
        graphBuilder = new ObjectGraphBuilder(pc);
        this.persistenceCache = pc;
    }

    /***********************************************************************/
    /***************** CRUD Methods ****************************************/
    /***********************************************************************/

    /**
     * Writes an entity into Persistence cache. (Actual database write is done
     * while flushing)
     */
    public void persist(Object e)
    {
        // Invoke Pre Persist Events
        EntityMetadata metadata = getMetadata(e.getClass());
        getEventDispatcher().fireEventListeners(metadata, e, PrePersist.class);

        // Create an object graph of the entity object
        ObjectGraph graph = graphBuilder.getObjectGraph(e, new TransientState());

        // Call persist on each node in object graph
        Node node = graph.getHeadNode();

        lock.writeLock().lock();

        node.persist();
        if (node.isHeadNode())
        {
            // build flush stack.

            flushManager.buildFlushStack(node, EventType.INSERT);

            // TODO : push into action queue.
            // Action/ExecutionQueue/ActivityQueue :-> id, name, EndPoint,

            flush();

            // Add node to persistence context after successful flush.
            getPersistenceCache().getMainCache().addHeadNode(node);
        }
        lock.writeLock().unlock();
        graph.getNodeMapping().clear();
        graph = null;

        // Invoke Post Persist Events
        getEventDispatcher().fireEventListeners(metadata, e, PostPersist.class);
        log.debug("Data persisted successfully for entity : " + e.getClass());
    }

    /**
     * Finds an entity from persistence cache, if not there, fetches from
     * database. Nodes are added into persistence cache (if not already there)
     * as and when they are found from DB. While adding nodes to persistence
     * cache, a deep copy is added, so that found object doesn't refer to
     * managed entity in persistence cache.
     * 
     * @param entityClass
     *            Entity Class
     * @param primaryKey
     *            Primary Key
     * @return Entity Object for the given primary key
     * 
     */
    public <E> E find(Class<E> entityClass, Object primaryKey)
    {
        // Locking as it might read from persistence context.
        lock.readLock().lock();

        EntityMetadata entityMetadata = getMetadata(entityClass);

        if (entityMetadata == null)
        {
            throw new KunderaException("Unable to load entity metadata for :" + entityClass);
        }
        String nodeId = ObjectGraphUtils.getNodeId(primaryKey, entityClass);

        MainCache mainCache = (MainCache) getPersistenceCache().getMainCache();
        Node node = mainCache.getNodeFromCache(nodeId);

        // if node is not in persistence cache or is dirty, fetch from database
        if (node == null || node.isDirty())
        {

            node = new Node(nodeId, entityClass, new ManagedState(), getPersistenceCache());
            node.setClient(getClient(entityMetadata));
            node.setPersistenceDelegator(this);

            node.find();
        }

        lock.readLock().unlock();
        Object nodeData = node.getData();
        if (nodeData == null)
        {
            return null;
        }
        else
        {
            return (E) node.getData();
        }

    }

    /**
     * Retrieves a {@link List} of Entities for given Primary Keys
     * 
     * @param entityClass
     *            Entity Class
     * @param primaryKeys
     *            Array of Primary Keys
     * @see {@link PersistenceDelegator#find(Class, Object)}
     * @return List of found entities
     */
    public <E> List<E> find(Class<E> entityClass, Object... primaryKeys)
    {
        List<E> entities = new ArrayList<E>();
        Set pKeys = new HashSet(Arrays.asList(primaryKeys));
        for (Object primaryKey : pKeys)
        {
            entities.add(find(entityClass, primaryKey));
        }
        return entities;
    }

    /**
     * Retrieves {@link List} of entities for a given {@link Map} of embedded
     * column values. Purpose of this method is to provide functionality of
     * search based on columns inside embedded objects.
     * 
     * @param entityClass
     *            Entity Class
     * @param embeddedColumnMap
     *            Embedded column map values
     * @return List of found entities.
     */
    public <E> List<E> find(Class<E> entityClass, Map<String, String> embeddedColumnMap)
    {
        EntityMetadata entityMetadata = getMetadata(entityClass);

        List<E> entities = new ArrayList<E>();
        entities = getClient(entityMetadata).find(entityClass, embeddedColumnMap);

        return entities;
    }

    /**
     * Finds {@link List} of child entities who contain given
     * <code>entityId</code> as <code>joinColumnName</code>
     * 
     * @param childClass
     *            Class of child entity
     * @param entityId
     *            Entity ID of parent entity
     * @param joinColumnName
     *            Join Column Name
     * @return
     */
    public List<?> find(Class<?> childClass, Object entityId, String joinColumnName)
    {
        EntityMetadata childMetadata = getMetadata(childClass);
        List<?> entities = new ArrayList();
        Client childClient = getClient(childMetadata);

        entities = childClient.findByRelation(joinColumnName, (String) entityId, childClass);

        if (entities == null)
            return null;

        return entities;
    }

    /**
     * Removes an entity object from persistence cache
     */
    public void remove(Object e)
    {

        // Invoke Pre Remove Events
        EntityMetadata metadata = getMetadata(e.getClass());
        getEventDispatcher().fireEventListeners(metadata, e, PreRemove.class);

        // Create an object graph of the entity object
        ObjectGraph graph = graphBuilder.getObjectGraph(e, new ManagedState());

        Node node = graph.getHeadNode();

        // if (node.getParents() == null)
        // {
        // node.setHeadNode(true);
        // }

        lock.writeLock().lock();

        // TODO : push into action queue, get original end-point from
        // persistenceContext first!

        // Action/ExecutionQueue/ActivityQueue :-> id, name, EndPoint, changed
        // state

        // Change state of node, after successful flush processing.
        node.remove();

        // build flush stack.

        flushManager.buildFlushStack(node, EventType.DELETE);

        // Flush node.
        flush();

        lock.writeLock().unlock();

        // clear out graph
        graph.getNodeMapping().clear();
        graph = null;

        getEventDispatcher().fireEventListeners(metadata, e, PostRemove.class);
        log.debug("Data removed successfully for entity : " + e.getClass());

    }

    /**
     * Flushes Dirty objects in {@link PersistenceCache} to databases.
     */
    public void flush()
    {
        // Get flush stack from Flush Manager
        if (applyFlush())
        {
            FlushStack fs = flushManager.getFlushStack();

            // Flush each node in flush stack from top to bottom unit it's empty
            log.debug("Flushing following flush stack to database(s) (showing stack objects from top to bottom):\n"
                    + fs);

            if (fs != null)
            {
                while (!fs.isEmpty())
                {
                    Node node = fs.pop();

                    // Only nodes in Managed and Removed state are flushed, rest
                    // are
                    // ignored
                    if (node.isInState(ManagedState.class) || node.isInState(RemovedState.class))
                    {
                        EntityMetadata metadata = getMetadata(node.getDataClass());
                        node.setClient(getClient(metadata));

                        node.flush();

                        // Update Link value for all nodes attached to this one
                        Map<NodeLink, Node> parents = node.getParents();
                        Map<NodeLink, Node> children = node.getChildren();

                        if (parents != null && !parents.isEmpty())
                        {
                            for (NodeLink parentNodeLink : parents.keySet())
                            {
                                parentNodeLink.addLinkProperty(LinkProperty.LINK_VALUE,
                                        ObjectGraphUtils.getEntityId(node.getNodeId()));
                            }
                        }

                        if (children != null && !children.isEmpty())
                        {
                            for (NodeLink childNodeLink : children.keySet())
                            {
                                childNodeLink.addLinkProperty(LinkProperty.LINK_VALUE,
                                        ObjectGraphUtils.getEntityId(node.getNodeId()));
                            }
                        }
                    }

                }

                // TODO : This needs to be look for different
                // permutation/combination
                // Flush Join Table data into database
                Map<String, JoinTableData> joinTableDataMap = flushManager.getJoinTableDataMap();
                for (JoinTableData jtData : joinTableDataMap.values())
                {
                    EntityMetadata m = KunderaMetadataManager.getEntityMetadata(jtData.getEntityClass());
                    Client client = getClient(m);

                    if (OPERATION.INSERT.equals(jtData.getOperation()))
                    {
                        client.persistJoinTable(jtData);
                    }
                    else if (OPERATION.DELETE.equals(jtData.getOperation()))
                    {
                        for (Object pk : jtData.getJoinTableRecords().keySet())
                        {
                            client.deleteByColumn(jtData.getJoinTableName(), m.getIdColumn().getName(), pk);
                        }
                    }
                    jtData.setProcessed(true);
                }
                joinTableDataMap.clear(); // All Join table operation performed,
                                          // clear it.

            }

        }
    }

    /**
     * @return
     */
    private boolean applyFlush()
    {

        return flushMode.equals(FlushModeType.AUTO) ? true : enableFlush;
    }

    public <E> E merge(E e)
    {

        log.debug("Merging Entity : " + e);
        EntityMetadata m = getMetadata(e.getClass());

        // TODO: throw OptisticLockException if wrong version and
        // optimistic locking enabled

        // Fire PreUpdate events
        getEventDispatcher().fireEventListeners(m, e, PreUpdate.class);

        // Create an object graph of the entity object to be merged
        ObjectGraph graph = graphBuilder.getObjectGraph(e, new ManagedState());

        // Call merge on each node in object graph
        Node node = graph.getHeadNode();

        lock.writeLock().lock();
        // Change node's state after successful flush.

        // TODO : push into action queue, get original end-point from
        // persistenceContext first!

        // Action/ExecutionQueue/ActivityQueue :-> id, name, EndPoint, changed
        // state

        node.merge();

        // build flush stack.

        flushManager.buildFlushStack(node, EventType.UPDATE);

        flush();
        lock.writeLock().unlock();

        graph.getNodeMapping().clear();
        graph = null;

        // fire PreUpdate events
        getEventDispatcher().fireEventListeners(m, e, PostUpdate.class);

        return (E) node.getData();
    }

    /**
     * Gets the client.
     * 
     * @param m
     *            the m
     * @return the client
     */
    public Client getClient(EntityMetadata m)
    {

        //
        // // Persistence Unit used to retrieve client
        String persistenceUnit = m.getPersistenceUnit();
        //
        Client client = clientMap.get(persistenceUnit);
        if (client == null)
        {
            throw new ClientResolverException("No client configured for persistenceUnit" + persistenceUnit);
        }
        // // single persistence unit given and entity is annotated with '@'.
        // // validate persistence unit given is same
        //
        // // If client has already been created, return it, or create it and
        // put
        // // it into client map
        // if (clientMap == null || clientMap.isEmpty())
        // {
        // clientMap = new HashMap<String, Client>();
        // client = ClientResolver.discoverClient(persistenceUnit);
        // clientMap.put(persistenceUnit, client);
        //
        // }
        // else if (clientMap.get(persistenceUnit) == null)
        // {
        // client = ClientResolver.discoverClient(persistenceUnit);
        // clientMap.put(persistenceUnit, client);
        // }
        // else
        // {
        // client = clientMap.get(persistenceUnit);
        // }

        return client;
    }

    /**
     * Gets the event dispatcher.
     * 
     * @return the event dispatcher
     */
    private EntityEventDispatcher getEventDispatcher()
    {
        return eventDispatcher;
    }

    /**
     * Creates the query.
     * 
     * @param jpaQuery
     *            the jpa query
     * @return the query
     */
    public Query createQuery(String jpaQuery)
    {
        Query query = new QueryResolver().getQueryImplementation(jpaQuery, this);
        return query;
    }

    /**
     * Checks if is open.
     * 
     * @return true, if is open
     */
    public final boolean isOpen()
    {
        return !closed;
    }

    /**
     * Close.
     */
    public final void close()
    {
        eventDispatcher = null;

        // Close all clients created in this session
        if (clientMap != null && !clientMap.isEmpty())
        {
            for (Client client : clientMap.values())
            {
                client.close();
            }
            clientMap.clear();
            clientMap = null;
        }

        // TODO: Move all nodes tied to this EM into detached state

        closed = true;
    }

    public final void clear()
    {
        // Move all nodes tied to this EM into detached state
        flushManager.clearFlushStack();
        getPersistenceCache().clean();

    }

    /**
     * Gets the metadata.
     * 
     * @param clazz
     *            the clazz
     * @return the metadata
     */
    public EntityMetadata getMetadata(Class<?> clazz)
    {
        return KunderaMetadataManager.getEntityMetadata(clazz);
    }

    /**
     * Gets the id.
     * 
     * @param entity
     *            the entity
     * @param metadata
     *            the metadata
     * @return the id
     */
    public String getId(Object entity, EntityMetadata metadata)
    {
        try
        {
            return PropertyAccessorHelper.getId(entity, metadata);
        }
        catch (PropertyAccessException e)
        {
            throw new KunderaException(e);
        }

    }

    /**
     * Store.
     * 
     * @param id
     *            the id
     * @param entity
     *            the entity
     */
    public void store(Object id, Object entity)
    {
        session.store(id, entity);
    }

    /**
     * Store.
     * 
     * @param entities
     *            the entities
     * @param entityMetadata
     *            the entity metadata
     */
    public void store(List entities, EntityMetadata entityMetadata)
    {
        for (Object o : entities)
            session.store(getId(o, entityMetadata), o);
    }

    /**
     * Gets the reader.
     * 
     * @param client
     *            the client
     * @return the reader
     */
    public EntityReader getReader(Client client)
    {
        return client.getReader();
    }

    /**
     * @return the flushMode
     */
    public FlushModeType getFlushMode()
    {
        return flushMode;
    }

    /**
     * @param flushMode
     *            the flushMode to set
     */
    public void setFlushMode(FlushModeType flushMode)
    {
        this.flushMode = flushMode;
    }

    /**
     * @return the isTransactionInProgress
     */
    public boolean isTransactionInProgress()
    {
        return isTransactionInProgress;
    }

    /**
     * @return the persistenceCache
     */
    public PersistenceCache getPersistenceCache()
    {
        return persistenceCache;
    }

    /******************************* Transaction related methods ***********************************************/

    public void begin()
    {
        isTransactionInProgress = true;
    }

    public void commit()
    {
        if (!flushMode.equals(FlushModeType.AUTO))
        {
            enableFlush = true;
            flush();
            isTransactionInProgress = false;
            enableFlush = false;
            flushManager.commit();
            flushManager.clearFlushStack();
        }
    }

    public void rollback()
    {
        isTransactionInProgress = false;
        flushManager.rollback(this);
        flushManager.clearFlushStack();
        getPersistenceCache().clean();

    }

    public boolean getRollbackOnly()
    {
        return false;
    }

    public void setRollbackOnly()
    {

    }

    public boolean isActive()
    {
        return isTransactionInProgress;
    }

    /**
     * Pre load client specific to persistence unit.
     * 
     * @param persistenceUnit
     *            persistence unit.
     */

    void loadClient(String persistenceUnit)
    {
        if (clientMap == null)
        {
            clientMap = new HashMap<String, Client>();
        }

        if (!clientMap.containsKey(persistenceUnit))
        {
            clientMap.put(persistenceUnit, ClientResolver.discoverClient(persistenceUnit));
        }
    }

    /**
     * Returns map of client as delegate to entity manager.
     * 
     * @return clientMap client map
     */
    Map<String, Client> getDelegate()
    {
        return clientMap;
    }
}