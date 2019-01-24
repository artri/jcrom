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
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.jcrom.FlushMode;
import org.jcrom.JcrMappingException;
import org.jcrom.JcrRuntimeException;
import org.jcrom.Session;
import org.jcrom.SessionEventListener;
import org.jcrom.SessionFactory;
import org.jcrom.Transaction;
import org.jcrom.annotations.JcrNode;
import org.jcrom.callback.JcromCallback;
import org.jcrom.engine.spi.SessionFactoryImplementor;
import org.jcrom.engine.spi.SessionImplementor;
import org.jcrom.mapping.Mapper;
import org.jcrom.type.TypeHandler;
import org.jcrom.util.NodeFilter;
import org.jcrom.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of a Session.
 * <p/>
 * Exposes two interfaces:<ul>
 * <li>{@link org.jcrom.Session} to the application</li>
 * </ul>
 * <p/>
 * This class is not thread-safe.
 */
public class SessionImpl implements SessionImplementor {
	private static final long serialVersionUID = -8254588340258340383L;
	private static final Logger LOGGER = LoggerFactory.getLogger(SessionImpl.class);
	
	private transient SessionFactoryImpl sessionFactory;
	private String uuid;
	private FlushMode flushMode;
	
	private Transaction currentJcrTransaction;
	private boolean waitingForAutoClose;
	private boolean closed;
	
	private boolean autoClear;
	private boolean autoClose;
	private transient boolean disallowOutOfTransactionUpdateOperations;
	private transient boolean discardOnClose;
	
	
	private javax.jcr.Session jcrSession;
	
	public SessionImpl(SessionFactoryImpl sessionFactory, SessionCreationOptions options) {
		this.sessionFactory = sessionFactory;
		this.flushMode = FlushMode.AUTO;
		this.autoClear = options.shouldAutoClear();
		this.autoClose = options.shouldAutoClose();
		this.disallowOutOfTransactionUpdateOperations = !sessionFactory.getSessionFactoryOptions().isAllowOutOfTransactionUpdateOperations();
		this.discardOnClose = sessionFactory.getSessionFactoryOptions().isReleaseResourcesOnCloseEnabled();
		
		// getTransactionCoordinator().pulse();
	}

	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	//~~~~~~~~~~ SharedSessionContract
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.Session#JcrRuntimeException()
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
	 * @see org.jcrom.Session#isOpened()
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

	protected void checkSessionFactoryOpen() {
		if (!getSessionFactory().isOpened()) {
			LOGGER.debug("Forcing Session closed as SessionFactory has been closed");
			setClosed();
		}
	}
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.Session#isClosed()
	 */
	@Override
	public boolean isClosed() {
		return closed || sessionFactory.isClosed();
	}
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.Session#beginTransaction()
	 */	
	@Override
	public Transaction beginTransaction() {
		checkOpen();
		
		Transaction transaction = getTransaction();
		transaction.begin();
		return transaction;
	}
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.Session#getTransaction()
	 */	
	@Override
	public Transaction getTransaction() {
		return accessTransaction();
	}
	
	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	//~~~~~~~~~~ Session 	
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.Session#getSessionFactory()
	 */	
	@Override
	public SessionFactoryImplementor getSessionFactory() {
		return this.sessionFactory;
	}

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
	 * @see org.jcrom.Session#isDirty()
	 */
	@Override
	public boolean isDirty() throws JcrRuntimeException {
		checkOpen();
		LOGGER.debug("Checking session dirtiness");
		if (actionQueue.areInsertionsOrDeletionsQueued() ) {
			log.debug( "Session dirty (scheduled updates and insertions)" );
			return true;
		}
		DirtyCheckEvent event = new DirtyCheckEvent( this );
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
	 * @see org.jcrom.Session#addEventListeners()
	 */		
	@Override
	public void addEventListeners(SessionEventListener... listeners) {
		getEventListenerManager().addListener( listeners );
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
	public Transaction accessTransaction() {	
		if (null == this.currentJcrTransaction) {
			this.currentJcrTransaction = new TransactionImpl(this);
		}
		
		if (!isClosed() || (waitingForAutoClose && getSessionFactory().isOpened())) {
			LOGGER.warn("transactionCoordinator.pulse() not implemented yet");
			//transactionCoordinator.pulse();
		}
		return this.currentJcrTransaction;
	}

	protected Transaction getCurrentTransaction() {
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
			throw new TransactionRequiredException( "no transaction is in progress" );
		}
	}
	
	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    
	public Mapper getMapper() {
        return getSessionFactory().getMapper();
    }
	
	@Override
	public Node getRootNode() throws RepositoryException {
		return jcrSession.getRootNode();
	}
	
	@Override
	public Node getNode(String absolutePath) throws RepositoryException {
        // special case, add directly to the root node
        return absolutePath.equals("/") ? jcrSession.getRootNode() : jcrSession.getRootNode().getNode(relativePath(absolutePath));
	}
	
	@Override
	public Node getNodeById(String id) throws RepositoryException {
		return jcrSession.getNodeByIdentifier(id);
	}	
	
	@Override
	public NodeIterator getNodes(String absolutePath) throws RepositoryException {
        // special case, add directly to the root node
        return absolutePath.equals("/") ? jcrSession.getRootNode().getNodes() : jcrSession.getRootNode().getNodes(relativePath(absolutePath));
    }
	
	@Override
	public boolean hasNode(String referencePath) throws RepositoryException {
		return jcrSession.getRootNode().hasNode(relativePath(referencePath));
	}
	
	@Override
	public Value createValue(String path) throws RepositoryException {
		return jcrSession.getValueFactory().createValue(path);
	}
	
	@Override
	public Value createValue(Node node) throws RepositoryException {
		return jcrSession.getValueFactory().createValue(node);
	}
	
	@Override
	public Value createValue(Node node, boolean weak) throws RepositoryException {
		return jcrSession.getValueFactory().createValue(node, weak);
	}
	
    /**
     * Creates a valid JCR node name from the String supplied, by
     * replacing all non-alphanumeric chars.
     * 
     * @param str the input String
     * @return a valid JCR node name for the String
     */
    public static String createValidName(String str) {
        return replaceNonAlphanumeric(str, '_');
    }
    
    public static String relativePath(String absolutePath) {
        if (absolutePath.charAt(0) == '/') {
            return absolutePath.substring(1);
        } else {
            return absolutePath;
        }
    }
    
    /**
     * Replaces occurences of non-alphanumeric characters with a
     * supplied char. A non-alphanumeric character at the beginning or end
     * is replaced with ''.
     */
    public static String replaceNonAlphanumeric(String str, char subst) {
        StringBuffer ret = new StringBuffer(str.length());
        char[] testChars = str.toCharArray();
        char lastChar = 'A';
        for (int i = 0; i < testChars.length; i++) {
            if (Character.isLetterOrDigit(testChars[i]) || testChars[i] == '.' || testChars[i] == ':') {
                ret.append(testChars[i]);
                lastChar = testChars[i];
            } else if (i > 0 && (i + 1) != testChars.length && lastChar != subst) {
                ret.append(subst);
                lastChar = subst;
            }
        }
        return ret.toString();
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
        if (!getMapper().isDynamicInstantiation() && !getMapper().isMapped(entityClass)) {
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
