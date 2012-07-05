/**
 * 
 */
package com.cattsoft.collect.io.utils;

import java.io.File;
import java.io.FileFilter;

/** 提供简单地文件操作
 * @author 陈小鸿
 * @author chenxiaohong@mail.com
 *
 */
public class FileUtils {
	/** 查找目录下所有文件
	 * @param path 目录路径 
	 * @return 文件列表
	 */
	public static File[] listFiles(String pathname) {
		return listFiles(new File(pathname), 2);
	}
	
	public static File[] listFiles(File pathname) {
		return listFiles(pathname, 2);
	}
	
	/**
	 * @param pathname
	 * @param type 4: DIRECTORY, 2: FILE
	 * @return
	 */
	public static File[] listFiles(File pathname, final int type) {
		if(pathname.exists()) {
			return pathname.listFiles(new FileFilter() {
				public boolean accept(File pathname) {
					if(type == 4) {
						return pathname.isDirectory();
					} else {
						return pathname.isFile();
					}
				}
			});
		} else {
			return new File[]{};
		}
	}
	
	/**
	 * 计算文件MD5值.
	 * @param file 待计算文件
	 */
	public static String md5sum(File file) {
		String md5 = null;
		
		
		
		return md5;
	}
	
	/**
	 * 计算文件MD5值.
	 * @param pathname 文件路径
	 */
	public static String md5sum(String pathname) {
		return md5sum(new File(pathname));
	}
	
	/** 查找目录下所有子目录
	 * @param path 目录路径 
	 * @return 子目录列表
	 */
	public static File[] listDirectorys(String pathname) {
		return listFiles(new File(pathname), 4);
	}
	
	public static File[] listDirectorys(File pathname) {
		return listFiles(pathname, 4);
	}
}
