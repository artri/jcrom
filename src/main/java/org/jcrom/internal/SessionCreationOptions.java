package org.jcrom.internal;

import org.jcrom.Connection;
import org.jcrom.FlushMode;

public interface SessionCreationOptions {

	boolean shouldAutoJoinTransactions();

	FlushMode getInitialSessionFlushMode();

	boolean shouldAutoClose();

	boolean shouldAutoClear();
	
	Connection getConnection();
}
