package org.jcrom.engine.spi;

import org.jcrom.SessionEventListener;

public interface SessionEventListenerManager extends SessionEventListener {
	void addListener(SessionEventListener... listeners);
}
