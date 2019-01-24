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
 * Represents a flushing strategy. The flush process synchronizes
 * database state with session state by detecting state changes
 * and executing SQL statements.
 *
 * @see JcrSession#setFlushMode(FlushMode)
 *
 * @author Gavin King
 */
public enum FlushMode {
	/**
	 * The {@link Session} is only ever flushed when {@link Session#flush}
	 * is explicitly called by the application. This mode is very
	 * efficient for read only transactions.
	 */
	MANUAL(0),

	/**
	 * The {@link Session} is flushed when {@link Transaction#commit}
	 * is called.
	 */
	COMMIT(5),

	/**
	 * The {@link Session} is sometimes flushed before query execution
	 * in order to ensure that queries never return stale state. This
	 * is the default flush mode.
	 */
	AUTO(10),

	/**
	 * The {@link Session} is flushed before every query. This is
	 * almost always unnecessary and inefficient.
	 */
	ALWAYS(20);

	private final int level;

	private FlushMode(int level) {
		this.level = level;
	}

	/**
	 * Checks to see if {@code this} flush mode is less than the given flush mode.
	 *
	 * @param other THe flush mode value to be checked against {@code this}
	 *
	 * @return {@code true} indicates {@code other} is less than {@code this}; {@code false} otherwise
	 */
	public boolean lessThan(FlushMode other) {
		return this.level < other.level;
	}
}
