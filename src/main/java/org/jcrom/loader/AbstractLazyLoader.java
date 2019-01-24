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

import net.sf.cglib.proxy.LazyLoader;

import org.jcrom.JcrMappingException;
import org.jcrom.engine.spi.JcrSessionImplementor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract class used by lazy loading classes.
 * 
 * @author Nicolas Dos Santos
 */
abstract class AbstractLazyLoader implements LazyLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLazyLoader.class);

    private final JcrSessionImplementor session;

    public AbstractLazyLoader(JcrSessionImplementor session) {
    	this.session = session;
    }

    @Override
    public final Object loadObject() throws JcrMappingException, Exception {
        return doLoadObject(session);
    }

    protected abstract Object doLoadObject(JcrSessionImplementor session) throws Exception;
}
