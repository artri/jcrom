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

/**
 * Enumeration of statuses in which a transaction facade ({@link org.jcrom.JcrTransaction}) might be.
 *
 */
public enum JcrTransactionStatus {
	/**
	 * The transaction has not yet been started.
	 */
	NOT_ACTIVE,
	/**
	 * The transaction has been started, but not yet completed.
	 */
	ACTIVE,
	/**
	 * The transaction has been completed successfully.
	 */
	COMMITTED,
	/**
	 * The transaction has been rolled back.
	 */
	ROLLED_BACK,
	/**
	 * The transaction has been marked for rollback only.
	 */
	MARKED_ROLLBACK,
	/**
	 * The transaction attempted to commit, but failed.
	 */
	FAILED_COMMIT,
	/**
	 * The transaction attempted to rollback, but failed.
	 */
	FAILED_ROLLBACK,
	/**
	 * Status code indicating a transaction that has begun the second
	 * phase of the two-phase commit protocol, but not yet completed
	 * this phase.
	 */
	COMMITTING,
	/**
	 *  Status code indicating a transaction that is in the process of
	 *  rolling back.
	 */
	ROLLING_BACK;

	public boolean isOneOf(JcrTransactionStatus... statuses) {
		for ( JcrTransactionStatus status : statuses ) {
			if ( this == status ) {
				return true;
			}
		}
		return false;
	}

	public boolean isNotOneOf(JcrTransactionStatus... statuses) {
		return !isOneOf( statuses );
	}

	public boolean canRollback() {
		return isOneOf(
				JcrTransactionStatus.ACTIVE,
				JcrTransactionStatus.FAILED_COMMIT,
				JcrTransactionStatus.MARKED_ROLLBACK
		);
	}
}
