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

import javax.transaction.Synchronization;


public interface Transaction {

	/**
	 * Start a resource transaction.
	 * 
	 * @throws IllegalStateException - if isActive() is true
	 */
	void begin();
	
	/**
	 * Commit the current resource transaction, writing any unflushed changes to the repository.
	 * 
	 * @throws IllegalStateException - if isActive() is false
	 * @throws RollbackException - if the commit fails
	 */
	void commit();
	
	/**
	 * Roll back the current resource transaction.
	 * 
	 * @throws IllegalStateException - if isActive() is false
	 * @throws PersistenceException - if an unexpected error condition is encountered
	 */
	void rollback();
	
	/**
	 * Indicate whether a resource transaction is in progress.
	 * 
	 * @return boolean indicating whether transaction is in progress
	 * 
	 * @throws PersistenceException - if an unexpected error condition is encountered
	 */
	boolean isActive();
	
	/**
	 * Determine whether the current resource transaction has been marked for rollback.
	 * 
	 * @return boolean indicating whether the transaction has been marked for rollback
	 * 
	 * @throws IllegalStateException - if isActive() is false
	 */
	boolean getRollbackOnly();
	
	/**
	 * Mark the current resource transaction so that the only possible outcome of the transaction 
	 * is for the transaction to be rolled back.
	 * 
	 * @throws IllegalStateException - if isActive() is false
	 */
	void setRollbackOnly();
	
	/**
	 * Get the current local status of this transaction.
	 * <p/>
	 * This only accounts for the local view of the transaction status.  In other words it does not check the status
	 * of the actual underlying transaction.
	 *
	 * @return The current local status.
	 */
	TransactionStatus getStatus();

	/**
	 * Register a user synchronization callback for this transaction.
	 *
	 * @param synchronization The Synchronization callback to register.
	 *
	 * @throws JcrRuntimeException Indicates a problem registering the synchronization.
	 */
	void registerSynchronization(Synchronization synchronization) throws JcrRuntimeException;

	/**
	 * Set the transaction timeout for any transaction started by a subsequent call to {@link #begin} on this instance.
	 *
	 * @param seconds The number of seconds before a timeout.
	 */
	void setTimeout(int seconds);

	/**
	 * Retrieve the transaction timeout set for this transaction.  A negative indicates no timeout has been set.
	 *
	 * @return The timeout, in seconds.
	 */
	int getTimeout();
	
	/**
	 * Make a best effort to mark the underlying transaction for rollback only.
	 */
	default void markRollbackOnly() {
		setRollbackOnly();
	}	
}
