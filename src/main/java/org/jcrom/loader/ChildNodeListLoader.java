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

import org.jcrom.Session;
import org.jcrom.annotations.JcrChildNode;
import org.jcrom.mapping.Mapper;
import org.jcrom.util.NodeFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles lazy loading of child node lists.
 * 
 * @author Olafur Gauti Gudmundsson
 * @author Nicolas Dos Santos
 */
class ChildNodeListLoader extends AbstractLazyLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChildNodeListLoader.class);

    private final Class<?> objectClass;
    private final Object parentObject;
    private final String containerPath;
    private final int depth;
    private final NodeFilter nodeFilter;
    private final JcrChildNode jcrChildNode;

    ChildNodeListLoader(Class<?> objectClass, Object parentObject, String containerPath, Mapper mapper, int depth, NodeFilter nodeFilter, JcrChildNode jcrChildNode) {
        super(mapper);
        this.objectClass = objectClass;
        this.parentObject = parentObject;
        this.containerPath = containerPath;
        this.depth = depth;
        this.nodeFilter = nodeFilter;
        this.jcrChildNode = jcrChildNode;
    }

    @Override
    protected Object doLoadObject(Session session, Mapper mapper) throws Exception {
    	LOGGER.debug("Lazy loading children list for: {}", containerPath);

    	Node childrenContainer = session.getNode(containerPath);
        return mapper.getChildrenList(objectClass, childrenContainer, parentObject, depth, nodeFilter, jcrChildNode);
    }

}