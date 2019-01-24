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
package org.jcrom.loader;

import java.lang.reflect.Field;

import javax.jcr.Node;

import org.jcrom.engine.spi.JcrSessionImplementor;
import org.jcrom.util.NodeFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles lazy loading of single reference.
 * 
 * @author Olafur Gauti Gudmundsson
 * @author Nicolas Dos Santos
 */
class ReferenceLoader extends AbstractLazyLoader {

	private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceLoader.class);

    private final Class<?> objClass;
    private final Object parentObject;
    private final String nodePath;
    private final String propertyName;
    private final int depth;
    private final NodeFilter nodeFilter;
    private final Field field;

    ReferenceLoader(JcrSessionImplementor session, Class<?> objClass, Object parentObject, String nodePath, String propertyName, int depth, NodeFilter nodeFilter, Field field) {
    	super(session);
        this.objClass = objClass;
        this.parentObject = parentObject;
        this.nodePath = nodePath;
        this.propertyName = propertyName;
        this.depth = depth;
        this.nodeFilter = nodeFilter;
        this.field = field;
    }

    @Override
    protected Object doLoadObject(JcrSessionImplementor session) throws Exception {
    	LOGGER.debug("Lazy loading single reference for: {} {}", nodePath, propertyName);

        Node node = session.getNode(nodePath);
        return session.getMapper().createReferencedObject(field, node.getProperty(propertyName).getValue(), parentObject, objClass, depth, nodeFilter);
    }
}
