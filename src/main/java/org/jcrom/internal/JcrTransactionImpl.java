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

import javax.transaction.Synchronization;

import org.jcrom.JcrRuntimeException;
import org.jcrom.JcrSession;
import org.jcrom.JcrTransaction;
import org.jcrom.JcrTransactionException;
import org.jcrom.JcrTransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JcrTransactionImpl implements JcrTransaction {
	private static final Logger LOGGER = LoggerFactory.getLogger(JcrTransactionImpl.class);
	
	private JcrSession session;
	//private JcrTransactionStatus status = JcrTransactionStatus.NOT_ACTIVE;
	
	public JcrTransactionImpl(JcrSession session) {
		this.session = session;
	}

	@Override
	public void begin() {
		if (!session.isOpened()) {
			throw new IllegalStateException("Can not begin transaction on closed Session");
		}
		LOGGER.trace("Begin transaction");
		
		// this.transactionDriverControl.begin();
		throw new UnsupportedOperationException("Not implemented yet");
	}

	@Override
	public void commit() {
		if (!isActive(true)) {
			// allow MARKED_ROLLBACK to propagate through to transactionDriverControl
			// the boolean passed to isActive indicates whether MARKED_ROLLBACK should be
			// considered active
			//
			// essentially here we have a transaction that is not active and
			// has not been marked for rollback only
			throw new IllegalStateException( "Transaction not successfully started" );			
		}
		LOGGER.trace("Commit transaction");
		
		// internalGetTransactionDriverControl().commit();
		throw new UnsupportedOperationException("Not implemented yet");
	}

	@Override
	public void rollback() {
		JcrTransactionStatus status = getStatus();
		if ( status == JcrTransactionStatus.ROLLED_BACK || status == JcrTransactionStatus.NOT_ACTIVE ) {
			// Allow rollback() calls on completed transactions, just no-op.
			LOGGER.debug( "rollback() called on an inactive transaction" );
			return;
		}

		if ( !status.canRollback() ) {
			throw new JcrTransactionException( "Cannot rollback transaction in current status [" + status.name() + "]" );
		}

		LOGGER.trace("rollback transaction");

		if ( status != JcrTransactionStatus.FAILED_COMMIT || allowFailedCommitToPhysicallyRollback() ) {
			//internalGetTransactionDriverControl().rollback();
		}
		
		throw new UnsupportedOperationException("Not implemented yet");
	}

	@Override
	public boolean isActive() {
		return isActive(true);
	}

	@Override
	public boolean isActive(boolean isMarkedRollbackConsideredActive) throws JcrRuntimeException {
//		if (transactionDriverControl == null ) {
//			if (session.isOpened()) {
//				transactionDriverControl = transactionCoordinator.getTransactionDriverControl();
//			} else {
//				return false;
//			}
//		}
//		return transactionDriverControl.isActive(isMarkedForRollbackConsideredActive);
		return false;
	}

	@Override
	public void registerSynchronization(Synchronization synchronization) throws JcrRuntimeException {
//		this.transactionCoordinator.getLocalSynchronizations().registerSynchronization(synchronization);
	}

	@Override
	public boolean getRollbackOnly() {
//		if ( !isActive() ) {
//			if ( jpaCompliance.isJpaTransactionComplianceEnabled() ) {
//				throw new IllegalStateException(
//						"JPA compliance dictates throwing IllegalStateException when #getRollbackOnly " +
//								"is called on non-active transaction"
//				);
//			}
//		}
//
//		return getStatus() == JcrTransactionStatus.MARKED_ROLLBACK;
		
		return getStatus() == JcrTransactionStatus.MARKED_ROLLBACK;
	}

	@Override
	public void setRollbackOnly() {
//		if (!isActive()) {
//			// Since this is the JPA-defined one, we make sure the txn is active first
//			// so long as compliance (JpaCompliance) has not been defined to disable
//			// that check - making this active more like Hibernate's #markRollbackOnly
//			if (jpaCompliance.isJpaTransactionComplianceEnabled() ) {
//				throw new IllegalStateException(
//						"JPA compliance dictates throwing IllegalStateException when #setRollbackOnly " +
//								"is called on non-active transaction"
//				);
//			}
//			else {
//				LOGGER.debug( "#setRollbackOnly called on a not-active transaction" );
//			}
//		}
//		else {
//			markRollbackOnly();
//		}
	}

	@Override
	public JcrTransactionStatus getStatus() {
//		if (transactionDriverControl == null) {
//			if (session.isOpened()) {
//				transactionDriverControl = transactionCoordinator.getTransactionDriverControl();
//			}
//			else {
//				return JcrTransactionStatus.NOT_ACTIVE;
//			}
//		}
//		return transactionDriverControl.getStatus();
		
		return JcrTransactionStatus.NOT_ACTIVE;
	}

	@Override
	public void setTimeout(int seconds) {
		
	}

	@Override
	public int getTimeout() {
		return 0;
	}

	@Override
	public void markRollbackOnly() {
//		if (isActive()) {
//			internalGetTransactionDriverControl().markRollbackOnly();
//		}
	}

	protected boolean allowFailedCommitToPhysicallyRollback() {
		return false;
	}	
}
