/**
 * This file is part of the JCROM project.
 * Copyright (C) 2008-2019 - All rights reserved.
 * Authors: Olafur Gauti Gudmundsson, Nicolas Dos Santos
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jcrom.internal;

import java.io.IOException;
import java.io.Serializable;
import java.util.Calendar;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.query.QueryManager;

import org.jcrom.AnnotationReader;
import org.jcrom.FlushMode;
import org.jcrom.JcrMappingException;
import org.jcrom.JcrRuntimeException;
import org.jcrom.JcrSession;
import org.jcrom.JcrSessionEventListener;
import org.jcrom.JcrSessionFactory;
import org.jcrom.JcrTransaction;
import org.jcrom.JcrTransactionException;
import org.jcrom.annotations.JcrNode;
import org.jcrom.callback.JcromCallback;
import org.jcrom.engine.spi.JcrSessionFactoryImplementor;
import org.jcrom.engine.spi.JcrSessionImplementor;
import org.jcrom.mapping.Mapper;
import org.jcrom.mapping.MapperImplementor;
import org.jcrom.type.TypeHandler;
import org.jcrom.util.NodeFilter;
import org.jcrom.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of a Session.
 * <p/>
 * Exposes two interfaces:<ul>
 * <li>{@link org.jcrom.JcrSession} to the application</li>
 * </ul>
 * <p/>
 * This class is not thread-safe.
 */
public class JcrSessionImpl implements JcrSessionImplementor {
	private static final long serialVersionUID = -8254588340258340383L;
	private static final Logger LOGGER = LoggerFactory.getLogger(JcrSessionImpl.class);
	
	private transient JcrSessionFactoryImpl sessionFactory;
	private String uuid;
	private FlushMode flushMode;
	
	private JcrTransaction currentJcrTransaction;
	private boolean waitingForAutoClose;
	private boolean closed;
	
	private boolean autoClear;
	private boolean autoClose;
	private transient boolean disallowOutOfTransactionUpdateOperations;
	private transient boolean discardOnClose;
	
	private transient JcrSessionEventListenerManagerImpl sessionEventListenerManager;
	
	private javax.jcr.Session rawSession;
	
	public JcrSessionImpl(JcrSessionFactoryImpl sessionFactory, SessionCreationOptions options) {
		this.sessionFactory = sessionFactory;
		this.flushMode = FlushMode.AUTO;
		this.autoClear = options.shouldAutoClear();
		this.autoClose = options.shouldAutoClose();
		this.disallowOutOfTransactionUpdateOperations = !sessionFactory.getOptions().isAllowOutOfTransactionUpdateOperations();
		this.discardOnClose = sessionFactory.getOptions().isReleaseResourcesOnCloseEnabled();
		
		this.sessionEventListenerManager = new JcrSessionEventListenerManagerImpl();
		// getTransactionCoordinator().pulse();
	}

	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	//~~~~~~~~~~ SharedSessionContract
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSession#JcrRuntimeException()
	 */	
	@Override
	public void close() throws JcrRuntimeException {
		if (closed && !waitingForAutoClose) {
			LOGGER.trace("Already closed");
			return;
		}
		
		LOGGER.trace("Closing session {}", getUUID());
		
		if (null != currentJcrTransaction) {
			currentJcrTransaction.invalidate();
		}
		
		setClosed();
	}
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSession#isOpened()
	 */		
	@Override
	public boolean isOpened() {
		checkSessionFactoryOpen();
		pulseTransactionCoordinator();
		try {
			return !isClosed();
		} catch(JcrRuntimeException e) {
			throw e;
		}
	}
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSession#isConnected()
	 */
	@Override
	public boolean isConnected() {
		throw new UnsupportedOperationException("Not implemented yet");
	}

	protected void checkSessionFactoryOpen() {
		if (!getSessionFactory().isOpened()) {
			LOGGER.debug("Forcing Session closed as SessionFactory has been closed");
			setClosed();
		}
	}
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSession#isClosed()
	 */
	@Override
	public boolean isClosed() {
		return closed || sessionFactory.isClosed();
	}
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSession#beginTransaction()
	 */	
	@Override
	public JcrTransaction beginTransaction() {
		checkOpen();
		
		JcrTransaction transaction = getTransaction();
		transaction.begin();
		return transaction;
	}
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSession#getTransaction()
	 */	
	@Override
	public JcrTransaction getTransaction() {
		return accessTransaction();
	}
	
	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	//~~~~~~~~~~ Session

	@Override
	public void flush() throws JcrRuntimeException {
		checkOpen();
		doFlush();
	}

	@Override
	public void clear() {
		checkOpen();

		// Do not call checkTransactionSynchStatus() here -- if a delayed
		// afterCompletion exists, it can cause an infinite loop.
		pulseTransactionCoordinator();

		try {
			internalClear();
		}
		catch (RuntimeException e) {
			throw new JcrRuntimeException(e);
		}
	}
	
	@Override
	public void setFlushMode(FlushMode flushMode) {
		checkOpen();
		this.flushMode = flushMode;
	}

	@Override
	public FlushMode getFlushMode() {
		return flushMode;
	}
		
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSession#isDirty()
	 */
	@Override
	public boolean isDirty() throws JcrRuntimeException {
		checkOpen();
		LOGGER.debug("Checking session dirtiness");
		if (actionQueue.areInsertionsOrDeletionsQueued() ) {
			LOGGER.debug( "Session dirty (scheduled updates and insertions)" );
			return true;
		}
		DirtyCheckEvent event = new DirtyCheckEvent(this);
		for (DirtyCheckEventListener listener : listeners(EventType.DIRTY_CHECK)) {
			listener.onDirtyCheck(event);
		}
		return event.isDirty();
	}

	@Override
	public boolean isDefaultReadOnly() {
		return persistenceContext.isDefaultReadOnly();
	}

	@Override
	public void setDefaultReadOnly(boolean defaultReadOnly) {
		persistenceContext.setDefaultReadOnly( defaultReadOnly );
	}
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSession#addEventListeners()
	 */		
	@Override
	public void addEventListeners(JcrSessionEventListener... listeners) {
		getEventListenerManager().addListener(listeners);
	}	
	
	private <T> Iterable<T> listeners(EventType<T> type) {
		return eventListenerGroup(type).listeners();
	}

	private <T> EventListenerGroup<T> eventListenerGroup(EventType<T> type) {
		return getFactory().getServiceRegistry().getService( EventListenerRegistry.class ).getEventListenerGroup(type);
	}
	
	// not for internal use:
	@Override
	public Serializable getIdentifier(Object object) throws JcrRuntimeException {
		checkOpen();
		checkTransactionSynchStatus();
		if (object instanceof HibernateProxy) {
			LazyInitializer li = ((HibernateProxy) object).getHibernateLazyInitializer();
			if (li.getSession() != this) {
				throw new TransientObjectException("The proxy was not associated with this session");
			}
			return li.getIdentifier();
		}
		else {
			EntityEntry entry = persistenceContext.getEntry(object);
			if ( entry == null ) {
				throw new TransientObjectException("The instance was not associated with this session");
			}
			return entry.getId();
		}
	}	
	
	@Override
	public boolean isReadOnly(Object entityOrProxy) {
		checkOpen();
//		return persistenceContext.isReadOnly(entityOrProxy);
		LOGGER.warn("persistenceContext.isReadOnly not implemented yet");
		return false;
	}

	@Override
	public void setReadOnly(Object entity, boolean readOnly) {
		checkOpen();
		//persistenceContext.setReadOnly(entity, readOnly);
		LOGGER.warn("persistenceContext.setReadOnly not implemented yet");
	}

	@Override
	public JcrSessionEventListenerManagerImpl getEventListenerManager() {
		return sessionEventListenerManager;
	}
	
	/**
	 * @return the uuid
	 */
	public String getUUID() {
		if (null == this.uuid) {
			// lazy initialization
			this.uuid = UUID.randomUUID().toString();
		}
		return uuid;
	}

	@Override
	public boolean isOpenOrWaitingForAutoClose() {
		return !isClosed() || waitingForAutoClose;
	}
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.engine.spi.JcrSessionImplementor#checkOpen()
	 */
	@Override
	public void checkOpen()  {
		checkOpen(true);
	}

	@Override
	public void checkOpen(boolean markForRollbackIfClosed) {
		if (isClosed()) {
			if (markForRollbackIfClosed /*&& transactionCoordinator.isTransactionActive()*/ ) {
				markForRollbackOnly();
			}
			throw new IllegalStateException("Session is closed");
		}
	}
	
	protected void setClosed() {
		closed = true;
		waitingForAutoClose = false;
		cleanupOnClose();
	}
	
	protected void cleanupOnClose() {
		// nothing to do in base impl, here for SessionImpl hook
	}

	private void internalClear() {
//		persistenceContext.clear();
//		actionQueue.clear();
//
//		final ClearEvent event = new ClearEvent(this);
//		for (ClearEventListener listener : listeners(EventType.CLEAR) ) {
//			listener.onClear( event );
//		}
	}

	@Override
	public void markForRollbackOnly() {
		try {
			accessTransaction().markRollbackOnly();
		} catch (Exception ignore) { }
	}
	
	@Override
	public long getTransactionStartTimestamp() {
		//return getCacheTransactionSynchronization().getCurrentTransactionStartTimestamp();
		throw new UnsupportedOperationException("Not implemented yet");
	}
	
	@Override
	public boolean isTransactionInProgress() {
		if (waitingForAutoClose) {
			return getSessionFactory().isOpened() /*&& transactionCoordinator.isTransactionActive()*/;
		}
		return !isClosed() /*&& transactionCoordinator.isTransactionActive()*/;
	}
	
	@Override
	public JcrTransaction accessTransaction() {	
		if (null == this.currentJcrTransaction) {
			this.currentJcrTransaction = new JcrTransactionImpl(this);
		}
		
		if (!isClosed() || (waitingForAutoClose && getSessionFactory().isOpened())) {
			LOGGER.warn("transactionCoordinator.pulse() not implemented yet");
			//transactionCoordinator.pulse();
		}
		return this.currentJcrTransaction;
	}

	protected JcrTransaction getCurrentTransaction() {
		return this.currentJcrTransaction;
	}
	
	protected void pulseTransactionCoordinator() {
		if (!isClosed()) {
			LOGGER.warn("transactionCoordinator.pulse() not implemented yet");
			//transactionCoordinator.pulse();
		}
	}
	
	private void doFlush() {
		checkTransactionNeeded();
		pulseTransactionCoordinator();

		try {
			if (persistenceContext.getCascadeLevel() > 0 ) {
				throw new JcrRuntimeException("Flush during cascade is dangerous");
			}

			FlushEvent flushEvent = new FlushEvent(this);
			for (FlushEventListener listener : listeners(EventType.FLUSH) ) {
				listener.onFlush( flushEvent );
			}
		} catch (RuntimeException e) {
			throw new JcrRuntimeException(e.getMessage(), e);
		}
	}
	
	private void checkTransactionNeeded() {
		if (disallowOutOfTransactionUpdateOperations && !isTransactionInProgress() ) {
			throw new JcrTransactionException("no transaction is in progress");
		}
	}
	
	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSession#getSessionFactory()
	 */	
	@Override
	public JcrSessionFactoryImplementor getSessionFactory() {
		return this.sessionFactory;
	}
	
	
	/** (non-Javadoc)
	 * @see org.jcrom.engine.spi.JcrSessionImplementor#isCleanNames()
	 */
	@Override
	public boolean isCleanNames() {
		return getSessionFactory().getOptions().isCleanNames();
	}

	/** (non-Javadoc)
	 * @see org.jcrom.engine.spi.JcrSessionImplementor#isDynamicInstantiation()
	 */
	@Override
	public boolean isDynamicInstantiation() {
		return getSessionFactory().getOptions().isDynamicInstantiation();
	}

    @Override
    public String getCleanName(String name) {
        if (name == null) {
            throw new JcrMappingException("Node name is null");
        }
        
        return isCleanNames() ? PathUtils.createValidName(name) : name;
    }
    
	/** (non-Javadoc)
	 * @see org.jcrom.JcrSession#getTypeHandler()
	 */
	@Override
	public TypeHandler getTypeHandler() {
		return getSessionFactory().getTypeHandler();
	}

	/** (non-Javadoc)
	 * @see org.jcrom.engine.spi.JcrSessionImplementor#getAnnotationReader()
	 */
	@Override
	public AnnotationReader getAnnotationReader() {
		return getSessionFactory().getAnnotationReader();
	}

	public MapperImplementor getMapper() {
        return getSessionFactory().getMapper();
    }
	
    public String getName(Object object) throws JcrMappingException {
        try {
            return getMapper().getNodeName(object);
        } catch (IllegalAccessException e) {
            throw new JcrMappingException("Could not get node name from object", e);
        }
    }
	
    public String getPath(Object object) throws JcrMappingException {
        try {
            return getMapper().getNodePath(object);
        } catch (IllegalAccessException e) {
            throw new JcrMappingException("Could not get node path from object", e);
        }
    }

    public Object getParentObject(Object childObject) throws JcrMappingException {
        try {
            return getMapper().getParentObject(childObject);
        } catch (IllegalAccessException e) {
            throw new JcrMappingException("Could not get parent object with Annotation JcrParentNode from child object", e);
        }
    }

    public String getChildContainerPath(Object childObject, Object parentObject, Node parentNode) {
        try {
            return getMapper().getChildContainerNodePath(childObject, parentObject, parentNode);
        } catch (IllegalAccessException e) {
            throw new JcrMappingException("Could not get child object with Annotation @JcrChildNode and with the type '" + childObject.getClass() + "' from parent object", e);
        } catch (RepositoryException e) {
            throw new JcrMappingException("Could not get child object with Annotation @JcrChildNode and with the type '" + childObject.getClass() + "' from parent object", e);
        }
    }
    
    public void setBaseVersionInfo(Object object, String name, Calendar created) throws JcrMappingException {
        try {
            getMapper().setBaseVersionInfo(object, name, created);
        } catch (IllegalAccessException e) {
            throw new JcrMappingException("Could not set base version info on object", e);
        }
    }
		
	/** (non-Javadoc)
	 * @see org.jcrom.JcrSession#getQueryManager()
	 */
	@Override
	public QueryManager getQueryManager() throws RepositoryException {
		return rawSession.getWorkspace().getQueryManager();
	}
	
	/**
	 *  (non-Javadoc)
	 * @see org.jcrom.JcrSession#move(java.lang.String, java.lang.String)
	 */
	@Override
	public void move(String srcAbsPath, String destAbsPath) throws RepositoryException {
		rawSession.move(srcAbsPath, destAbsPath);
	}

	/**
	 *  (non-Javadoc)
	 * @see org.jcrom.JcrSession#save()
	 */
	@Override
	public void save() throws RepositoryException {
		rawSession.save();
	}

	@Override
	public Node getRootNode() throws RepositoryException {
		return rawSession.getRootNode();
	}
	
	@Override
	public Node getNode(String absolutePath) throws RepositoryException {
        // special case, add directly to the root node
        return PathUtils.isRootPath(absolutePath) ? rawSession.getRootNode() 
        		: rawSession.getRootNode().getNode(PathUtils.relativePath(absolutePath));
	}
	
	@Override
	public Node getNodeById(String id) throws RepositoryException {
		return rawSession.getNodeByIdentifier(id);
	}	
	
	@Override
	public NodeIterator getNodes(String absolutePath) throws RepositoryException {
        // special case, add directly to the root node
        return PathUtils.isRootPath(absolutePath) ? rawSession.getRootNode().getNodes() 
        		: rawSession.getRootNode().getNodes(PathUtils.relativePath(absolutePath));
    }
	
	@Override
	public boolean hasNode(String referencePath) throws RepositoryException {
		return rawSession.getRootNode().hasNode(PathUtils.relativePath(referencePath));
	}
	
	@Override
	public Value createValue(String path) throws RepositoryException {
		return rawSession.getValueFactory().createValue(path);
	}
	
	@Override
	public Value createValue(Node node) throws RepositoryException {
		return rawSession.getValueFactory().createValue(node);
	}
	
	@Override
	public Value createValue(Node node, boolean weak) throws RepositoryException {
		return rawSession.getValueFactory().createValue(node, weak);
	}
	    
    /**
     * Maps the node supplied to an instance of the entity class. Loads all child nodes, to infinite depth.
     * 
     * @param entityClass the class of the entity to be instantiated from the node (in the case of dynamic instantiation, the instance class may be read from the document, but will be cast to this class)
     * @param node the JCR node from which to create the object
     * @return an instance of the JCR entity class, mapped from the node
     * @throws JcrMappingException
     */
    public <T> T fromNode(Class<T> entityClass, Node node) throws JcrMappingException {
        return fromNode(entityClass, node, null);
    }

    /**
     * Maps the node supplied to an instance of the entity class.
     * 
     * @param entityClass the class of the entity to be instantiated from the node (in the case of dynamic instantiation, the instance class may be read from the document, but will be cast to this class)
     * @param node the JCR node from which to create the object
     * @param nodeFilter the NodeFilter to apply when loading child nodes and references
     * @return an instance of the JCR entity class, mapped from the node
     * @throws JcrMappingException
     */
    @SuppressWarnings("unchecked")
    public <T> T fromNode(Class<T> entityClass, Node node, NodeFilter nodeFilter) throws JcrMappingException {
        if (!isDynamicInstantiation() && !getMapper().isMapped(entityClass)) {
            throw new JcrMappingException("Trying to map to an unmapped class: " + entityClass.getName());
        }
        try {
            return (T) getMapper().fromNodeWithParent(entityClass, node, nodeFilter);
        } catch (ClassNotFoundException e) {
            throw new JcrMappingException("Could not map Object from node", e);
        } catch (InstantiationException e) {
            throw new JcrMappingException("Could not map Object from node", e);
        } catch (RepositoryException e) {
            throw new JcrMappingException("Could not map Object from node", e);
        } catch (IllegalAccessException e) {
            throw new JcrMappingException("Could not map Object from node", e);
        } catch (IOException e) {
            throw new JcrMappingException("Could not map Object from node", e);
        } finally {
        	getMapper().clearHistory();
        }
    }

    /**
     * Maps the entity supplied to a JCR node, and adds that node as a child to the parent node supplied.
     * 
     * @param parentNode the parent node to which the entity node will be added
     * @param entity the entity to be mapped to the JCR node
     * @return the newly created JCR node
     * @throws JcrMappingException
     */
    public Node addNode(Node parentNode, Object entity) throws JcrMappingException {
        return addNode(parentNode, entity, null);
    }

    /**
     * Maps the entity supplied to a JCR node, and adds that node as a child to the parent node supplied.
     * 
     * @param parentNode the parent node to which the entity node will be added
     * @param entity the entity to be mapped to the JCR node
     * @param mixinTypes an array of mixin type that will be added to the new node
     * @return the newly created JCR node
     * @throws JcrMappingException
     */
    public Node addNode(Node parentNode, Object entity, String[] mixinTypes) throws JcrMappingException {
        return addNode(parentNode, entity, mixinTypes, null);
    }

    /**
     * Maps the entity supplied to a JCR node, and adds that node as a child to the parent node supplied.
     * 
     * @param parentNode the parent node to which the entity node will be added
     * @param entity the entity to be mapped to the JCR node
     * @param mixinTypes an array of mixin type that will be added to the new node
     * @param action callback object that specifies the Jcrom actions: 
     *     <ul>
     *       <li>{@link JcromCallback#doAddNode(Node, String, JcrNode, Object)},</li>
     *       <li>{@link JcromCallback#doAddMixinTypes(Node, String[], JcrNode, Object)},</li>
     *       <li>{@link JcromCallback#doAddClassNameToProperty(Node, JcrNode, Object)},</li>
     *       <li>{@link JcromCallback#doComplete(Object, Node)},</li>
     *     </ul>
     * @return the newly created JCR node
     * @throws JcrMappingException
     * @since 2.1.0
     */
    public Node addNode(Node parentNode, Object entity, String[] mixinTypes, JcromCallback action) throws JcrMappingException {
        if (!getMapper().isMapped(entity.getClass())) {
            throw new JcrMappingException("Trying to map an unmapped class: " + entity.getClass().getName());
        }
        try {
            return getMapper().addNode(parentNode, entity, mixinTypes, action);
        } catch (RepositoryException e) {
            throw new JcrMappingException("Could not create node from object", e);
        } catch (IllegalAccessException e) {
            throw new JcrMappingException("Could not create node from object", e);
        } catch (IOException e) {
            throw new JcrMappingException("Could not create node from object", e);
        } finally {
        	getMapper().clearHistory();
        }
    }

    /**
     * Update an existing JCR node with the entity supplied.
     * 
     * @param node the JCR node to be updated
     * @param entity the entity that will be mapped to the existing node
     * @return the updated node
     * @throws JcrMappingException
     */
    public Node updateNode(Node node, Object entity) throws JcrMappingException {
        return updateNode(node, entity, null, null);
    }

    /**
     * Update an existing JCR node with the entity supplied.
     * 
     * @param node the JCR node to be updated
     * @param entity the entity that will be mapped to the existing node
     * @param nodeFilter the NodeFilter to apply when updating child nodes and references
     * @return the updated node
     * @throws JcrMappingException
     */
    public Node updateNode(Node node, Object entity, NodeFilter nodeFilter) throws JcrMappingException {
        return updateNode(node, entity, nodeFilter, null);
    }

    /**
     * Update an existing JCR node with the entity supplied.
     * 
     * @param node the JCR node to be updated
     * @param entity the entity that will be mapped to the existing node
     * @param nodeFilter the NodeFilter to apply when updating child nodes and references
     * @param action callback object that specifies the Jcrom actions: 
     *     <ul>
     *       <li>{@link JcromCallback#doUpdateClassNameToProperty(Node, JcrNode, Object)},</li>
     *       <li>{@link JcromCallback#doMoveNode(Node, Node, String, JcrNode, Object)},</li>
     *       <li>{@link JcromCallback#doComplete(Object, Node)},</li>
     *     </ul>
     * @return the updated node
     * @throws JcrMappingException
     * @since 2.1.0
     */
    public Node updateNode(Node node, Object entity, NodeFilter nodeFilter, JcromCallback action) throws JcrMappingException {

        if (!getMapper().isMapped(entity.getClass())) {
            throw new JcrMappingException("Trying to map an unmapped class: " + entity.getClass().getName());
        }
        try {
            return getMapper().updateNode(node, entity, nodeFilter, action);
        } catch (RepositoryException e) {
            throw new JcrMappingException("Could not update node from object", e);
        } catch (IllegalAccessException e) {
            throw new JcrMappingException("Could not update node from object", e);
        } catch (IOException e) {
            throw new JcrMappingException("Could not update node from object", e);
        } finally {
        	getMapper().clearHistory();
        }
    }
   
    public void logNodeInfos(Node node) throws RepositoryException {
    	if (!LOGGER.isInfoEnabled()) {
    		return;
    	}
        for (PropertyIterator iter = node.getProperties(); iter.hasNext();) {
            Property p = iter.nextProperty();
            if (p.isMultiple()) {
                for (Value value : p.getValues()) {
                    LOGGER.info("{} = {}", p.getName(), value.getString());
                }
            } else {
            	LOGGER.info("{} = {}", p.getName(), p.getValue().getString());
            }
        }
        for (PropertyIterator iter = node.getReferences(); iter.hasNext();) {
            Property p = iter.nextProperty();
            if (p.isMultiple()) {
                for (Value value : p.getValues()) {
                	LOGGER.info("{} = {}", p.getName(), value.getString());
                }
            } else {
            	LOGGER.info("{} = {}", p.getName(), p.getValue().getString());
            }
        }
        for (PropertyIterator iter = node.getWeakReferences(); iter.hasNext();) {
            Property p = iter.nextProperty();
            if (p.isMultiple()) {
                for (Value value : p.getValues()) {
                	LOGGER.info("{} = {}", p.getName(), value.getString());
                }
            } else {
            	LOGGER.info("{} = {}", p.getName(), p.getValue().getString());
            }
        }
    }	
}
