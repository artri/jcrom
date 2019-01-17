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
package org.jcrom.context;

import java.io.Serializable;

import javax.jcr.Session;

import org.jcrom.JcrRuntimeException;

public interface CurrentSessionContext extends Serializable {
	
	/**
	 * Retrieve the current session according to the scoping defined
	 * by this implementation.
	 * 
	 * @return The current session.
	 * @throws JcrRuntimeException Typically indicates an issue locating or creating the current session.
	 */
	public Session getCurrentSession() throws JcrRuntimeException;
}
