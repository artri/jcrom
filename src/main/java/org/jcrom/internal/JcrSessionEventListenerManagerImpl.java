package org.jcrom.internal;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jcrom.JcrSessionEventListener;
import org.jcrom.engine.spi.JcrSessionEventListenerManager;

public class JcrSessionEventListenerManagerImpl implements JcrSessionEventListenerManager, Serializable {
	private static final long serialVersionUID = 1209177132634783827L;
	
	private List<JcrSessionEventListener> listenerList;

	/**
	 * (non-Javadoc)
	 * @see org.jcrom.engine.spi.JcrSessionEventListenerManager#addListener(org.jcrom.JcrSessionEventListener[])
	 */
	@Override
	public void addListener(JcrSessionEventListener... listeners) {
		if (null == listenerList) {
			listenerList = new ArrayList<>();
		}
		Collections.addAll(listenerList, listeners);
	}
	
	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSessionEventListener#transactionCompletion(boolean)
	 */
	@Override
	public void transactionCompletion(boolean successful) {
		if (null == listenerList) {
			return;
		}
		
		for (JcrSessionEventListener listener : listenerList) {
			listener.transactionCompletion(successful);
		}
	}

	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSessionEventListener#jcrConnectionAcquisitionStart()
	 */
	@Override
	public void jcrConnectionAcquisitionStart() {
		if (null == listenerList) {
			return;
		}
		
		for (JcrSessionEventListener listener : listenerList) {
			listener.jcrConnectionAcquisitionStart();
		}
	}

	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSessionEventListener#jcrConnectionAcquisitionEnd()
	 */
	@Override
	public void jcrConnectionAcquisitionEnd() {
		if (null == listenerList) {
			return;
		}
		
		for (JcrSessionEventListener listener : listenerList) {
			listener.jcrConnectionAcquisitionEnd();
		}
	}

	/**
	 *  (non-Javadoc)
	 * @see org.jcrom.JcrSessionEventListener#jcrConnectionReleaseStart()
	 */
	@Override
	public void jcrConnectionReleaseStart() {
		if (null == listenerList) {
			return;
		}
		
		for (JcrSessionEventListener listener : listenerList) {
			listener.jcrConnectionReleaseStart();
		}
	}

	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSessionEventListener#jcrConnectionReleaseEnd()
	 */
	@Override
	public void jcrConnectionReleaseEnd() {
		if (null == listenerList) {
			return;
		}
		
		for (JcrSessionEventListener listener : listenerList) {
			listener.jcrConnectionReleaseEnd();
		}
		
	}

	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSessionEventListener#jcrExecuteStatementStart()
	 */
	@Override
	public void jcrExecuteStatementStart() {
		if (null == listenerList) {
			return;
		}
		
		for (JcrSessionEventListener listener : listenerList) {
			listener.jcrExecuteStatementStart();
		}
		
	}

	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSessionEventListener#jcrExecuteStatementEnd()
	 */
	@Override
	public void jcrExecuteStatementEnd() {
		if (null == listenerList) {
			return;
		}
		
		for (JcrSessionEventListener listener : listenerList) {
			listener.jcrExecuteStatementEnd();
		}
	}

	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSessionEventListener#flushStart()
	 */
	@Override
	public void flushStart() {
		if (null == listenerList) {
			return;
		}
		
		for (JcrSessionEventListener listener : listenerList) {
			listener.flushStart();
		}
	}

	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSessionEventListener#flushEnd(int, int)
	 */
	@Override
	public void flushEnd(int numberOfEntities, int numberOfCollections) {
		if (null == listenerList) {
			return;
		}
		
		for (JcrSessionEventListener listener : listenerList) {
			listener.flushEnd(numberOfEntities, numberOfCollections);
		}
	}

	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSessionEventListener#dirtyCalculationStart()
	 */
	@Override
	public void dirtyCalculationStart() {
		if (null == listenerList) {
			return;
		}
		
		for (JcrSessionEventListener listener : listenerList) {
			listener.dirtyCalculationStart();
		}
	}

	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSessionEventListener#dirtyCalculationEnd(boolean)
	 */
	@Override
	public void dirtyCalculationEnd(boolean dirty) {
		if (null == listenerList) {
			return;
		}
		
		for (JcrSessionEventListener listener : listenerList) {
			listener.dirtyCalculationEnd(dirty);
		}
	}

	/**
	 * (non-Javadoc)
	 * @see org.jcrom.JcrSessionEventListener#end()
	 */
	@Override
	public void end() {
		if (null == listenerList) {
			return;
		}
		
		for (JcrSessionEventListener listener : listenerList) {
			listener.end();
		}
	}
}
