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
package org.jcrom.engine.spi;

import org.jcrom.AnnotationReader;
import org.jcrom.JcrSession;
import org.jcrom.JcrSessionFactory;
import org.jcrom.JcrTransaction;
import org.jcrom.mapping.MapperImplementor;
import org.jcrom.type.TypeHandler;

public interface JcrSessionImplementor extends JcrSession {
	//===== SharedSessionContractImplementor =====
	JcrSessionEventListenerManager getEventListenerManager();
	
	/**
	 * Obtain the identifier associated with this session.
	 * 
	 * @return The identifier associated with this session, or {@code null}
	 */
	String getUUID();

	/**
	 * Checks whether the session is closed.  Provided separately from
	 * {@link #isOpen()} as this method does not attempt any JTA synchronization
	 * registration, where as {@link #isOpen()} does; which makes this one
	 * nicer to use for most internal purposes.
	 *
	 * @return {@code true} if the session is closed; {@code false} otherwise.
	 */
	boolean isClosed();
	
	/**
	 * Checks whether the session is open or is waiting for auto-close
	 *
	 * @return {@code true} if the session is closed or if it's waiting for auto-close; {@code false} otherwise.
	 */
	boolean isOpenOrWaitingForAutoClose();
	
	/**
	 * Performs a check whether the Session is open, and if not:<ul>
	 *     <li>marks current transaction (if one) for rollback only</li>
	 *     <li>throws an IllegalStateException (JPA defines the exception type)</li>
	 * </ul>
	 */
	void checkOpen();
	
	/**
	 * Performs a check whether the Session is open, and if not:<ul>
	 *     <li>if {@code markForRollbackIfClosed} is true, marks current transaction (if one) for rollback only</li>
	 *     <li>throws an IllegalStateException (JPA defines the exception type)</li>
	 * </ul>
	 */
	void checkOpen(boolean markForRollbackIfClosed);
	
	/**
	 * Marks current transaction (if one) for rollback only
	 */
	void markForRollbackOnly();
	
	/**
	 * A "timestamp" at or before the start of the current transaction.
	 *
	 * @apiNote This "timestamp" need not be related to timestamp in the Java Date/millisecond
	 * sense.  It just needs to be an incrementing value.  See
	 * {@link CacheTransactionSynchronization#getCurrentTransactionStartTimestamp()}
	 */
	long getTransactionStartTimestamp();
	
	/**
	 * Does this <tt>Session</tt> have an active transaction
	 * or is there a JTA transaction in progress?
	 */
	boolean isTransactionInProgress();

	/**
	 * Provides access to the underlying transaction or creates a new transaction if
	 * one does not already exist or is active.  This is primarily for internal or
	 * integrator use.
	 *
	 * @return the transaction
     */
	JcrTransaction accessTransaction();
	
	//===== SessionImplementor =====
	@Override
	JcrSessionFactoryImplementor getSessionFactory();
	
	boolean isCleanNames();
	boolean isDynamicInstantiation();
	
	String getCleanName(String name);
	
	/**
	 * Convenience access to the {@link TypeHandler} associated with this session's {@link SessionFactory}.
	 * <p/>
	 * Equivalent to calling {@link #getSessionFactory()}.{@link SessionFactory#getTypeHandler getTypeHandler()}
	 *
	 * @return The {@link TypeHandler} associated with this session's {@link JcrSessionFactory}
	 */
	TypeHandler getTypeHandler();
	AnnotationReader getAnnotationReader();
	MapperImplementor getMapper();
}
