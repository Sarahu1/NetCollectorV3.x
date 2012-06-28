/**
 * 
 */
package com.cattsoft.collect.io.file.split;

import java.io.File;
import java.io.Serializable;

/** 文件分割数据片段.
 * @author ChenXiaohong
 */
public class FileBlock implements Serializable {
	private static final long serialVersionUID = 1L;
	/*** 原数据文件*/
	private File file;
	/*** 片段文件路径*/
	private String path;
	/*** 文件片段编号*/
	private int index;
	/*** 偏移位置*/
	private long offset;
	/*** 数据长度*/
	private long length;
	/*** 数据结束位置*/
	private long position;
	
	public FileBlock() {
		// 
	}
	
	/**
	 * @param file 原数据文件
	 * @param path 片段文件路径
	 * @param index 文件片段编号
	 * @param offset 偏移位置
	 * @param length 数据长度
	 */
	public FileBlock(File file, String path, int index, long offset, long length) {
		this.file = file;
		this.path = path;
		this.index = index;
		this.offset = offset;
		this.length = length;
		this.position = this.length - this.offset;
	}

	public File getFile() {
		return file;
	}

	public void setFile(File file) {
		this.file = file;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public long getOffset() {
		return offset;
	}

	public void setOffset(long offset) {
		this.offset = offset;
	}

	public long getLength() {
		return length;
	}

	public void setLength(long length) {
		this.length = length;
	}

	public long getPosition() {
		return position;
	}

	public void setPosition(int position) {
		this.position = position;
	}
}
