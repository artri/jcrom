package org.jcrom.engine.spi;

import org.jcrom.JcrSessionEventListener;

public interface JcrSessionEventListenerManager extends JcrSessionEventListener {
	void addListener(JcrSessionEventListener... listeners);
}