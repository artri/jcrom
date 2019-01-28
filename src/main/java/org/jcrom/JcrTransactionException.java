package org.jcrom;

public class JcrTransactionException extends JcrRuntimeException {
	private static final long serialVersionUID = 8459320377311078893L;

	public JcrTransactionException() {
		super();
	}

	public JcrTransactionException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public JcrTransactionException(String message, Throwable cause) {
		super(message, cause);
	}

	public JcrTransactionException(String message) {
		super(message);
	}

	public JcrTransactionException(Throwable cause) {
		super(cause);
	}

}
