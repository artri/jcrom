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
import java.util.List;

import net.sf.cglib.proxy.Enhancer;

import org.jcrom.annotations.JcrChildNode;
import org.jcrom.annotations.JcrFileNode;
import org.jcrom.mapping.Mapper;
import org.jcrom.util.NodeFilter;

/**
 * Creates CGLIB proxies for lazy loading.
 *
 * @author Olafur Gauti Gudmundsson
 * @author Nicolas Dos Santos
 */
public final class ProxyFactory {

    private ProxyFactory() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T createChildNodeProxy(Class<T> c, Object parentObj, String containerPath, Mapper mapper, int depth, NodeFilter nodeFilter, boolean pathIsContainer) {
        ChildNodeLoader childNodeLoader = new ChildNodeLoader(c, parentObj, containerPath, mapper, depth, nodeFilter, pathIsContainer);
        return (T) Enhancer.create(c, childNodeLoader);
    }

    public static List<?> createChildNodeListProxy(Class<?> c, Object parentObj, String containerPath, Mapper mapper, int depth, NodeFilter nodeFilter, JcrChildNode jcrChildNode) {
        ChildNodeListLoader childNodeListLoader = new ChildNodeListLoader(c, parentObj, containerPath, mapper, depth, nodeFilter, jcrChildNode);
        return (List<?>) Enhancer.create(List.class, childNodeListLoader);
    }

    @SuppressWarnings("unchecked")
    public static <T> T createFileNodeProxy(Class<T> c, Object obj, String fileContainerPath, Mapper mapper, int depth, NodeFilter nodeFilter, JcrFileNode jcrFileNode) {
        FileNodeLoader fileNodeLoader = new FileNodeLoader(c, obj, fileContainerPath, mapper, depth, nodeFilter, jcrFileNode);
        return (T) Enhancer.create(c, fileNodeLoader);
    }

    public static List<?> createFileNodeListProxy(Class<?> c, Object obj, String fileContainerPath, Mapper mapper, int depth, NodeFilter nodeFilter, JcrFileNode jcrFileNode) {
        FileNodeListLoader fileNodeListLoader = new FileNodeListLoader(c, obj, fileContainerPath, mapper, depth, nodeFilter, jcrFileNode);
        return (List<?>) Enhancer.create(List.class, fileNodeListLoader);
    }

    @SuppressWarnings("unchecked")
    public static <T> T createReferenceProxy(Class<T> c, Object parentObject, String nodePath, String propertyName, Mapper mapper, int depth, NodeFilter nodeFilter, Field field) {
        ReferenceLoader refLoader = new ReferenceLoader(c, parentObject, nodePath, propertyName, mapper, depth, nodeFilter, field);
        return (T) Enhancer.create(c, refLoader);
    }

    public static List<?> createReferenceListProxy(Class<?> c, Object parentObject, String nodePath, String propertyName, Mapper mapper, int depth, NodeFilter nodeFilter, Field field) {
        ReferenceListLoader refListLoader = new ReferenceListLoader(c, parentObject, nodePath, propertyName, mapper, depth, nodeFilter, field);
        return (List<?>) Enhancer.create(List.class, refListLoader);
    }
}
