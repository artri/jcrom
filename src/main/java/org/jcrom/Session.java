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
package org.jcrom;

import java.io.Closeable;
import java.io.Serializable;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;

public interface Session extends Serializable, AutoCloseable, Closeable {
	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	//~~~~~~~~~~ SharedSessionContract
	
	/**
	 * End this <tt>Session</tt> by releasing the JCR connection and cleaning up.
	 * 
	 * @throws JcrRuntimeException Indicates an issue closing the factory.
	 */
	void close() throws JcrRuntimeException;	
	
	/**
	 * Check if the session is still open.
	 *
	 * @return boolean
	 */	
	boolean isOpened();

	/**
	 * Check if the session is currently connected.
	 *
	 * @return boolean
	 */
	boolean isConnected();

	/**
	 * Begin a unit of work and return the associated {@link Transaction} object.  If a new underlying transaction is
	 * required, begin the transaction.  Otherwise continue the new work in the context of the existing underlying
	 * transaction.
	 *
	 * @return a Transaction instance
	 *
	 * @see #getTransaction
	 */
	Transaction beginTransaction();

	/**
	 * Get the {@link Transaction} instance associated with this session.  The concrete type of the returned
	 * {@link Transaction} object is determined by the {@code hibernate.transaction_factory} property.
	 *
	 * @return a Transaction instance
	 */
	Transaction getTransaction();
	
	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	//~~~~~~~~~~ Session 
	
	/**
	 * Get the session factory which created this session.
	 *
	 * @return The session factory.
	 * @see SessionFactory
	 */
	SessionFactory getSessionFactory();
	
	/**
	 * Force this session to flush. Must be called at the end of a
	 * unit of work, before committing the transaction and closing the
	 * session (depending on {@link #setFlushMode(FlushMode)},
	 * {@link Transaction#commit()} calls this method).
	 * <p/>
	 * <i>Flushing</i> is the process of synchronizing the underlying persistent
	 * store with persistable state held in memory.
	 *
	 * @throws JcrRuntimeException Indicates problems flushing the session or talking to the repository.
	 */
	void flush() throws JcrRuntimeException;
	
	/**
	 * Completely clear the session. Evict all loaded instances and cancel all pending
	 * saves, updates and deletions.
	 */
	void clear();
	
	/**
	 * Set the flush mode for this session.
	 * <p/>
	 * The flush mode determines the points at which the session is flushed.
	 * <i>Flushing</i> is the process of synchronizing the underlying persistent
	 * store with persistable state held in memory.
	 * <p/>
	 * For a logically "read only" session, it is reasonable to set the session's
	 * flush mode to {@link FlushMode#MANUAL} at the start of the session (in
	 * order to achieve some extra performance).
	 *
	 * @param flushMode the new flush mode
	 */
	void setFlushMode(FlushMode flushMode);

	/**
	 * Get the current flush mode for this session.
	 *
	 * @return The flush mode
	 */
	FlushMode getFlushMode();
	
	/**
	 * Does this session contain any changes which must be synchronized with
	 * the repository?  In other words, would any DML operations be executed if
	 * we flushed this session?
	 *
	 * @return True if the session contains pending changes; false otherwise.
	 * @throws JcrRuntimeException could not perform dirtying checking
	 */
	boolean isDirty() throws JcrRuntimeException;
	
	/**
	 * Will entities and proxies that are loaded into this session be made read-only by default?
	 *
	 * To determine the read-only/modifiable setting for a particular entity or proxy:
	 * @see Session#isReadOnly(Object)
	 *
	 * @return true, loaded entities/proxies will be made read-only by default; 
	 *         false, loaded entities/proxies will be made modifiable by default. 
	 */
	boolean isDefaultReadOnly();
	
	/**
	 * Change the default for entities and proxies loaded into this session
	 * from modifiable to read-only mode, or from modifiable to read-only mode.
	 *
	 * Read-only entities are not dirty-checked and snapshots of persistent
	 * state are not maintained. Read-only entities can be modified, but
	 * changes are not persisted.
	 *
	 * When a proxy is initialized, the loaded entity will have the same
	 * read-only/modifiable setting as the uninitialized
	 * proxy has, regardless of the session's current setting.
	 *
	 * To change the read-only/modifiable setting for a particular entity
	 * or proxy that is already in this session:
	 * @see Session#setReadOnly(Object,boolean)
	 *
	 *
	 * @param readOnly true, the default for loaded entities/proxies is read-only;
	 *                 false, the default for loaded entities/proxies is modifiable
	 */
	void setDefaultReadOnly(boolean readOnly);	
	
	/**
	 * Add one or more listeners to the Session
	 *
	 * @param listeners The listener(s) to add
	 */
	void addEventListeners(SessionEventListener... listeners);
	
	/**
	 * Return the identifier value of the given entity as associated with this
	 * session.  An exception is thrown if the given entity instance is transient
	 * or detached in relation to this session.
	 *
	 * @param object a persistent instance
	 * @return the identifier
	 * @throws TransientObjectException if the instance is transient or associated with
	 * a different session
	 */
	Serializable getIdentifier(Object object);
	
	/**
	 * Is the specified entity or proxy read-only?
	 *
	 * To get the default read-only/modifiable setting used for
	 * entities and proxies that are loaded into the session:
	 * @see org.jcrom.Session#isDefaultReadOnly()
	 *
	 * @param entityOrProxy an entity or proxy
	 * @return {@code true} if the entity or proxy is read-only, {@code false} if the entity or proxy is modifiable.
	 */
	boolean isReadOnly(Object entityOrProxy);

	/**
	 * Set an unmodified persistent object to read-only mode, or a read-only
	 * object to modifiable mode. In read-only mode, no snapshot is maintained,
	 * the instance is never dirty checked, and changes are not persisted.
	 *
	 * If the entity or proxy already has the specified read-only/modifiable
	 * setting, then this method does nothing.
	 * 
	 * To set the default read-only/modifiable setting used for
	 * entities and proxies that are loaded into the session:
	 * @see org.jcrom.Session#setDefaultReadOnly(boolean)
	 * 
	 * @param entityOrProxy an entity or proxy
	 * @param readOnly {@code true} if the entity or proxy should be made read-only; {@code false} if the entity or
	 * proxy should be made modifiable
	 */
	void setReadOnly(Object entityOrProxy, boolean readOnly);
	
	/*
	 * Convenience access to the {@link TypeHelper} associated with this session's {@link SessionFactory}.
	 * <p/>
	 * Equivalent to calling {@link #getSessionFactory()}.{@link SessionFactory#getTypeHelper getTypeHelper()}
	 *
	 * @return The {@link TypeHelper} associated with this session's {@link SessionFactory}
	 */
	//TypeHelper getTypeHelper();
	
	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	
	Node getRootNode() throws RepositoryException;
	Node getNode(String absolutePath) throws RepositoryException;
	Node getNodeById(String id) throws RepositoryException;
	NodeIterator getNodes(String absolutePath) throws RepositoryException;
	
	boolean hasNode(String referencePath) throws RepositoryException;
	
	Value createValue(String path) throws RepositoryException;
	Value createValue(Node node) throws RepositoryException;
	Value createValue(Node node, boolean weak) throws RepositoryException;
	//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~	
}
