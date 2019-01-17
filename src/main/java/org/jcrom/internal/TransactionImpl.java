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

import org.jcrom.Session;
import org.jcrom.Transaction;
import org.jcrom.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionImpl implements Transaction {
	private static final Logger LOGGER = LoggerFactory.getLogger(TransactionImpl.class);
	
	private Session session;
	private TransactionStatus status = TransactionStatus.NOT_ACTIVE;
	
	public TransactionImpl(Session session) {
		this.session = session;
	}

	@Override
	public void begin() {
		LOGGER.trace("Begin transaction");
	}

	@Override
	public void commit() {
		LOGGER.trace("Commit transaction");
	}

	@Override
	public void rollback() {
		LOGGER.trace("rollback transaction");
	}

	@Override
	public boolean isActive() {
		return false;
	}

	@Override
	public boolean getRollbackOnly() {
		return false;
	}

	@Override
	public void setRollbackOnly() {
		
	}

	@Override
	public TransactionStatus getStatus() {
		return status;
	}

	@Override
	public void setTimeout(int seconds) {
		
	}

	@Override
	public int getTimeout() {
		return 0;
	}

}
