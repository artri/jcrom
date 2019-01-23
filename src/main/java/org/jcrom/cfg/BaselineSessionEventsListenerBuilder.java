package org.jcrom.cfg;

import java.util.ArrayList;
import java.util.List;

import org.jcrom.SessionEventListener;

public class BaselineSessionEventsListenerBuilder {
	private boolean logSessionMetrics;
	
	public List<SessionEventListener> buildBaselineList() {
		List<SessionEventListener> list = new ArrayList<SessionEventListener>();
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
