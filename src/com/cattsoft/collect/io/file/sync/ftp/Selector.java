/**
 * 
 */
package com.cattsoft.collect.io.file.sync.ftp;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

/** 文件选择器.
 * 根据文件名称进行选择
 * 使用正则表达式对符合约定的文件进行选择
 * @author 陈小鸿
 * @date 2012-6-14
 */
public class Selector {
	/*** 文件选择路径 */
	private String path;
	
	/**
	 * 构造函数
	 */
	public Selector(String path) {
		if(null == path) {
			throw new IllegalArgumentException("文件夹路径不能为空");
		}
		this.path = path;
	}
	
	/**
	 * 选择符合约定的文件.
	 * @return 返回符合约定规则的文件路径列表
	 */
	public String[] select(FileFilter filter) {
		List<String> paths = new ArrayList<String>();
		File files = new File(path);
		// 是否为目录
		if(files.isDirectory()) {
			// 循环文件列表
			for (File file : files.listFiles(filter)) {
				// 匹配规则添加到列表
				paths.add(file.getAbsolutePath());
			}
		}
		return paths.toArray(new String[paths.size()]);
	}
	
	/**
	 * @param path the path to set
	 */
	public void setPath(String path) {
		this.path = path;
	}
	
	/**
	 * @return the path
	 */
	public String getPath() {
		return path;
	}
	
}
