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

import javax.jcr.Binary;
import javax.jcr.RepositoryException;

import org.jcrom.util.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * This data provider directly maps the Binary of the JCR repository.
 * <p/>
 * This allow to get a fresh InputStream on each getInputstream call.
 * <p/>
 * This is also useful to know that this DataProvider should not be used to update a Node.
 * <p/>
 * User: Antoine Mischler <antoine@dooapp.com>
 * Date: 12/08/2014
 * Time: 10:39
 */
public class BinaryValueJcrDataProvider implements JcrDataProvider {
	private static final long serialVersionUID = -6268171189540017504L;

	private static final Logger LOGGER = LoggerFactory.getLogger(BinaryValueJcrDataProvider.class);
	
    private final Binary binary;

    /**
     * Constructor to directly maps the Binary of the JCR repository.
     * <p/>
     * This allow to get a fresh InputStream on each getInputstream call.
     * <p/>
     * This is also useful to know that this DataProvider should not be used to update a Node (cf. {@link #isPersisted()}).
     * @param binary Binary object
     */    
    public BinaryValueJcrDataProvider(Binary binary) {
        this.binary = binary;
    }

    @Override
    public TYPE getType() {
        return TYPE.STREAM;
    }

    @Override
    public boolean isFile() {
        return false;
    }

    @Override
    public boolean isBytes() {
        return false;
    }

    @Override
    public boolean isStream() {
        return true;
    }

    @Override
    public File getFile() {
        return null;
    }

    @Override
    public byte[] getBytes() {
        return new byte[0];
    }

    @Override
    public InputStream getInputStream() {
        try {
            return binary.getStream();
        } catch (RepositoryException e) {
        	LOGGER.error(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public void writeToFile(File destination) throws IOException {
        if (getType() != TYPE.STREAM) {
        	throw new UnsupportedOperationException("Type is not supported: " + getType());
        }
        FileUtils.write(getInputStream(), destination);
    }

    @Override
    public long getContentLength() {
        try {
            return binary.getSize();
        } catch (RepositoryException e) {
        	LOGGER.error(e.getMessage(), e);
        }
        return -1;
    }
    
    @Override
    public boolean isPersisted() {
        return binary != null;
    }
}