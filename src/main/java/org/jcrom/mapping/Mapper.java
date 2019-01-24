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
package org.jcrom.mapping;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.jcrom.JcrFile;
import org.jcrom.JcrSession;
import org.jcrom.annotations.JcrChildNode;
import org.jcrom.annotations.JcrFileNode;
import org.jcrom.util.NodeFilter;


/**
 * This class handles the heavy lifting of mapping a JCR node to a JCR entity object, and vice versa.
 *
 * @author Olafur Gauti Gudmundsson
 * @author Nicolas Dos Santos
 */
public interface Mapper {
	//TODO: move to MapperImplementor
	static final String DEFAULT_FIELDNAME = "fieldName";
	
	JcrSession getSession();
	
	List<?> getChildrenList(Class<?> childObjClass, Node childrenContainer, Object parentObj, int depth, NodeFilter nodeFilter, JcrChildNode jcrChildNode)
					throws ClassNotFoundException, InstantiationException, RepositoryException, IllegalAccessException, IOException;

    Object getSingleChild(Class<?> childObjClass, Node childNode, Object obj, int depth, NodeFilter nodeFilter) 
    		throws ClassNotFoundException, InstantiationException, RepositoryException, IllegalAccessException, IOException;
    
    JcrFile getSingleFile(Class<?> childObjClass, Node fileContainer, Object obj, JcrFileNode jcrFileNode, int depth, NodeFilter nodeFilter) 
    		throws ClassNotFoundException, InstantiationException, RepositoryException, IllegalAccessException, IOException;

    List<JcrFile> getFileList(Class<?> childObjClass, Node fileContainer, Object obj, JcrFileNode jcrFileNode, int depth, NodeFilter nodeFilter) 
    		throws ClassNotFoundException, InstantiationException, RepositoryException, IllegalAccessException, IOException;
    
    List<?> getReferenceList(Field field, String propertyName, Class<?> referenceObjClass, Node node, Object obj, int depth, NodeFilter nodeFilter) 
    		throws ClassNotFoundException, InstantiationException, RepositoryException, IllegalAccessException, IOException;
    
    Object createReferencedObject(Field field, Value value, Object obj, Class<?> referenceObjClass, int depth, NodeFilter nodeFilter) 
    		throws ClassNotFoundException, InstantiationException, RepositoryException, IllegalAccessException, IOException;
}
