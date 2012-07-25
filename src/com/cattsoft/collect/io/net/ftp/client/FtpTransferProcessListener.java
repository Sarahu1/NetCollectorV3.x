/**
 * 
 */
package com.cattsoft.collect.io.net.ftp.client;

/**
 * @author 陈小鸿
 * @author chenxiaohong@mail.com
 *
 */
public abstract class FtpTransferProcessListener {
	/**
	 * 是否传输完成
	 */
	private boolean complete = false;;
	
	// 文件传输类型
	public static final int PUT = 0;
	public static final int GET = 1;
	public static final int UNKNOWN = -1;
	
	// 文件传输完成类型
	/*** 文件已存在 */
	public static final int COMPLETE_EXISTS = 100;
	/*** 断点续传 */
	public static final int COMPLETE_HTTP = 100;
	/*** 全新上传 */
	public static final int COMPLETE_NEW = 100;
	
	/**
	 * 当前文件传输类型(上传(0)/下载(1)/未知(-1))
	 */
	public int type = -1;
	
	/*** 传输完成类型 */
	public int completeType = COMPLETE_NEW;
	  
	/** 传输进度更新.
	 * @param step 进度
	 */
	public abstract void process(long step);

	/** 已传输的数据总数
	 * @param transferred 已传输
	 */
	public void transferred(long transferred) {
		// 
	}
	
	/**
	 * 传输完成
	 */
	public void complete() {
		complete = true;
	}

	/**
	 * @return the complete
	 */
	public boolean isComplete() {
		return complete;
	}
	
	/**
	 * @param type the type to set
	 */
	public void setType(int type) {
		this.type = type;
	}
	
	/**
	 * @return the type
	 */
	public int getType() {
		return type;
	}
	
	/**
	 * @return the completeType
	 */
	public int getCompleteType() {
		return completeType;
	}
	
	/**
	 * @param completeType the completeType to set
	 */
	public void setCompleteType(int completeType) {
		this.completeType = completeType;
	}
}
