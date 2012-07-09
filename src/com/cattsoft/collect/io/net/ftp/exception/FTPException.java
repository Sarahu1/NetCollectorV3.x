/**
 * 
 */
package com.cattsoft.collect.io.net.ftp.exception;

/**
 * @author 陈小鸿
 * @author chenxiaohong@mail.com
 *
 */
public class FTPException extends Exception {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * 默认定义异常
	 */
	public FTPException() {
		super();
	}
	
	/**
	 * @param message 异常描述信息
	 */
	public FTPException(String message) {
		super(message);
	}
	
	/**
	 * @param message 异常描述信息
	 * @param cause 异常原因
	 */
	public FTPException(String message, Throwable cause) {
		super(message, cause);
	}
	
	/**
	 * @param cause 异常原因
	 */
	public FTPException(Throwable cause) {
		super(cause);
	}
	
}
