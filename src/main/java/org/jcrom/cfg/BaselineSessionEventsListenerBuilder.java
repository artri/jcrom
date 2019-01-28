package org.jcrom.cfg;

import java.util.ArrayList;
import java.util.List;

import org.jcrom.JcrSessionEventListener;

public class BaselineSessionEventsListenerBuilder {
	private boolean logSessionMetrics;
	
	public List<JcrSessionEventListener> buildBaselineList() {
		List<JcrSessionEventListener> list = new ArrayList<JcrSessionEventListener>();
		if (logSessionMetrics && StatisticalLoggingSessionEventListener.isLoggingEnabled()) {
			list.add(new StatisticalLoggingSessionEventListener());
		}
		if ( autoListener != null ) {
			try {
				list.add( autoListener.newInstance() );
			}
			catch (Exception e) {
				throw new HibernateException("Unable to instantiate specified auto SessionEventListener : " + autoListener.getName(), e);
			}
		}
		return list;
	}
}
