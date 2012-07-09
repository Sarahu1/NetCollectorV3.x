/**
 * 
 */
package com.cattsoft.collect.io.net.ftp.client;

import java.io.Serializable;

/** FTP 上传任务.
 * @author 陈小鸿
 * @author chenxiaohong@mail.com
 *
 */
public class Task implements Serializable, Cloneable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * 本地文件路径.
	 */
	private String filepath;
	
	/**
	 * 服务器上传目录.
	 */
	private String uploadpath;
	
	/**
	 * 文件是否备份
	 */
	private boolean backup;
	
	/**
	 * 任务是否已完成.
	 */
	private boolean complete;
	/**
	 * 任务重试上传次数
	 */
	private int retries = 0;
	/**
	 * 任务最大错误次数
	 */
	private int maxRetries = 10;
	
	/**
	 * @param uploadpath 上传目录
	 * @param filepath 文件路径
	 */
	public Task(String uploadpath, String filepath, boolean backup) {
		this.filepath = filepath;
		this.uploadpath = uploadpath;
		this.backup = backup;
	}

	/**
	 * @return the complete
	 */
	public boolean isComplete() {
		return complete;
	}
	
	/**
	 * @param complete the complete to set
	 */
	public void setComplete(boolean complete) {
		if(!complete)
			retries++;
		this.complete = complete;
	}
	
	/**
	 * @return the backup
	 */
	public boolean isBackup() {
		return backup;
	}
	
	/**
	 * @return the filepath
	 */
	public String getFilepath() {
		return filepath;
	}
	
	/**
	 * @return the uploadpath
	 */
	public String getUploadpath() {
		return uploadpath;
	}
	
	/**
	 * @return the retries
	 */
	public int getRetries() {
		return retries;
	}
	
	/**
	 * @return the maxRetries
	 */
	public int getMaxRetries() {
		return maxRetries;
	}
	
	/**
	 * @param maxRetries the maxRetries to set
	 */
	public void setMaxRetries(int maxRetries) {
		this.maxRetries = maxRetries;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	public Object clone() throws CloneNotSupportedException {
		return super.clone();
	}
}
