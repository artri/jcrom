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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.jcrom.util.io.FileUtils;

/**
 * A simple implementation of the JcrDataProvider interface.
 * Developers can implement their own data provider if advanced or custom
 * functionality is needed.
 * 
 * <p>Thanks to Robin Wyles for adding content length.</p>
 * 
 * @author Olafur Gauti Gudmundsson
 * @author Nicolas Dos Santos
 */
public class JcrDataProviderImpl implements JcrDataProvider {
	private static final long serialVersionUID = -2659176341502450654L;
	
	private final TYPE type;
    private final byte[] bytes;
    private final File file;
    private final InputStream inputStream;
    private final long contentLength;

    public JcrDataProviderImpl(byte[] bytes) {
        this.type = TYPE.BYTES;
        this.bytes = new byte[bytes.length];
        System.arraycopy(bytes, 0, this.bytes, 0, bytes.length);
        this.file = null;
        this.inputStream = null;
        this.contentLength = bytes.length;
    }

    public JcrDataProviderImpl(File file) {
        this.type = TYPE.FILE;
        this.file = file;
        this.bytes = null;
        this.inputStream = null;
        this.contentLength = file.length();
    }

    public JcrDataProviderImpl(InputStream inputStream) {
        this(inputStream, -1);
    }

    public JcrDataProviderImpl(InputStream inputStream, long length) {
        this.type = TYPE.STREAM;
        this.inputStream = inputStream;
        this.bytes = null;
        this.file = null;
        this.contentLength = length;
    }

    @Override
    public boolean isBytes() {
        return type == TYPE.BYTES;
    }

    @Override
    public boolean isFile() {
        return type == TYPE.FILE;
    }

    @Override
    public boolean isStream() {
        return type == TYPE.STREAM;
    }

    @Override
    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public TYPE getType() {
        return type;
    }

    @Override
    public void writeToFile(File destination) throws IOException {
        if (getType() == TYPE.BYTES) {
            FileUtils.write(getBytes(), destination);
        } else if (getType() == TYPE.STREAM) {
        	FileUtils.write(getInputStream(), destination);
        } else if (getType() == TYPE.FILE) {
        	FileUtils.write(getFile(), destination);
        }
    }

    @Override
    public long getContentLength() {
        return contentLength;
    }

    @Override
    public boolean isPersisted() {
        return false;
    }
}
