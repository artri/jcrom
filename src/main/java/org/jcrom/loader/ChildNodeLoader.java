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

import javax.jcr.Node;

import org.jcrom.engine.spi.JcrSessionImplementor;
import org.jcrom.util.NodeFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles lazy loading of single child node.
 * 
 * @author Olafur Gauti Gudmundsson
 * @author Nicolas Dos Santos
 */
class ChildNodeLoader extends AbstractLazyLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChildNodeLoader.class);

    private final Class<?> objectClass;
    private final Object parentObject;
    private final String containerPath;
    private final int depth;
    private final NodeFilter nodeFilter;
    private final boolean pathIsContainer;

    ChildNodeLoader(JcrSessionImplementor session, Class<?> objectClass, Object parentObject, String containerPath, int depth, NodeFilter nodeFilter) {
        this(session, objectClass, parentObject, containerPath, depth, nodeFilter, true);
    }

    ChildNodeLoader(JcrSessionImplementor session, Class<?> objectClass, Object parentObject, String containerPath, int depth, NodeFilter nodeFilter, boolean pathIsContainer) {
    	super(session);
        this.objectClass = objectClass;
        this.parentObject = parentObject;
        this.containerPath = containerPath;
        this.depth = depth;
        this.nodeFilter = nodeFilter;
        this.pathIsContainer = pathIsContainer;
    }

    @Override
    protected Object doLoadObject(JcrSessionImplementor session) throws Exception {
    	LOGGER.debug("Lazy loading single child for: {}", containerPath);
    	
        Node node;
        if (pathIsContainer) {
            node = session.getNode(containerPath).getNodes().nextNode();
        } else {
            node = session.getNode(containerPath);
        }
        return session.getMapper().getSingleChild(objectClass, node, parentObject, depth, nodeFilter);
    }
}
