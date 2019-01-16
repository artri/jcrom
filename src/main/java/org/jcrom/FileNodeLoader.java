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

import javax.jcr.Node;
import javax.jcr.Session;

import org.jcrom.annotations.JcrFileNode;
import org.jcrom.util.NodeFilter;
import org.jcrom.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles lazy loading of single file node.
 * 
 * @author Olafur Gauti Gudmundsson
 * @author Nicolas Dos Santos
 */
public class FileNodeLoader extends AbstractLazyLoader {

	private static final Logger LOGGER = LoggerFactory.getLogger(FileNodeLoader.class);

    private final Class<?> objectClass;
    private final String fileContainerPath;
    private final Object parentObject;
    private final JcrFileNode jcrFileNode;
    private final int depth;
    private final NodeFilter nodeFilter;

    FileNodeLoader(Class<?> objectClass, Object parentObject, String fileContainerPath, Session session, Mapper mapper, int depth, NodeFilter nodeFilter, JcrFileNode jcrFileNode) {
        super(session, mapper);
        this.objectClass = objectClass;
        this.parentObject = parentObject;
        this.jcrFileNode = jcrFileNode;
        this.fileContainerPath = fileContainerPath;
        this.depth = depth;
        this.nodeFilter = nodeFilter;
    }

    @Override
    protected Object doLoadObject(Session session, Mapper mapper) throws Exception {
    	LOGGER.debug("Lazy loading file list for: {} ", fileContainerPath);
    	
        Node fileContainer = PathUtils.getNode(fileContainerPath, session);
        return mapper.getFileNodeMapper().getSingleFile(objectClass, fileContainer, parentObject, jcrFileNode, depth, nodeFilter, mapper);
    }
}
