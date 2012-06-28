/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 1999-2009, QOS.ch. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package com.cattsoft.collect.io.file.merge;

import java.io.File;
import java.io.FilenameFilter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cattsoft.collect.io.file.archive.ZipFileUtils;

public class FileBlockFilter {
	public static String extactCommand(String blockname) {
		String command = "";
		// 命令行
		Matcher matcher = Pattern.compile("^\\d+_m\\d+_([\\w\\_]+)_result").matcher(blockname);
		try {
			if(matcher.find()) {
				command = matcher.group(1);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return command;
	}
	
	public static String extactDate(String blockname) {
		// 月份
		String date = null;
		Matcher matcher = null;
		try {
			//年月
			matcher = Pattern.compile("^(\\d{2})").matcher(blockname);
			matcher.find();
			date = matcher.group(1);
		} catch (Exception e) {
			// 由文件名前缀获取日期
			// 未获取到时使用系统日期
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy"+File.separator+"MM");
			date = sdf.format(System.currentTimeMillis());
		}
		return date;
	}
	
	public static String extactMonitor(String blockname) {
		String monitor = "";
		// 采集点
		Matcher matcher = Pattern.compile("_m(\\d+)_").matcher(blockname);
		try {
			if(matcher.find()) {
				monitor = matcher.group(1);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return monitor;
	}
	
	/** 挑选数据文件.
	 * @param files
	 * @return 文件列表
	 */
	public static File[] select(Queue<File> files) {
		// 循环文件列表,筛选文件
		List<File> select = new LinkedList<File>();
		
		for (File file : files) {
			if(file.isDirectory())
				continue;
			int partCount = FileMerge.getPartCount(file.getName());
			// 存在其它分卷
			if(partCount > 1) {
				// 查找所有分卷
				String matcherRegx = file.getName();
				matcherRegx = matcherRegx.replaceFirst("^\\d+", "^\\\\d+").replaceFirst(
						"\\.part\\d+\\.zip", "\\\\.part\\\\d+\\\\.zip");
				Pattern partPattern = Pattern.compile(matcherRegx);
				for (File matchFile : files) {
					if(partPattern.matcher(matchFile.getName()).matches()) {
						select.add(matchFile);
					}
				}
				/**********************/
				/*** 检查分卷完整性 ***/
				/*** 检测数据完整性 ***/
				/**********************/
				// 标记检查是否通过
				boolean pass = (select.size() == partCount);
				// 分卷完整,测试文件数据正确性
				if(pass) {
					for (File select_file : select) {
						// 只测试ZIP压缩文件
						if(select_file.getName().endsWith(".zip")) {
							try {
								// 测试
								ZipFileUtils.test(select_file.getPath());
							} catch (Exception e) {
								// 出错表示文件无法通过测试
								// 可能文件已损坏或正在使用
								pass = false;
								break;
							}
						}
					}
				}
				if(pass) {
					// 通过时中断循环,返回选择列表
					break;
				} else {
					// 未通过时清除选择列表,等待下次选择
					// 数据不完整,清除
					files.removeAll(select);
					select.clear();
					continue;
				}
			} else {
				try {
					// 测试
					ZipFileUtils.test(file.getPath());
					select.add(file);
					break;
				} catch (Exception e) {
					// 清空,查找其它文件
					files.removeAll(select);
					select.clear();
				}
			}
		}
		files.removeAll(select);
		//: 筛选完成
		return select.toArray(new File[select.size()]);
	}

	public static void sortFileArrayByName(File[] fileArray) {
		Arrays.sort(fileArray, new Comparator<File>() {
			public int compare(File o1, File o2) {
				String o1Name = o1.getName();
				String o2Name = o2.getName();
				return (o1Name.compareTo(o2Name));
			}
		});
	}

	public static void reverseSortFileArrayByName(File[] fileArray) {
		Arrays.sort(fileArray, new Comparator<File>() {
			public int compare(File o1, File o2) {
				String o1Name = o1.getName();
				String o2Name = o2.getName();
				return (o2Name.compareTo(o1Name));
			}
		});
	}

	public static String afterLastSlash(String sregex) {
		int i = sregex.lastIndexOf('/');
		if (i == -1) {
			return sregex;
		} else {
			return sregex.substring(i + 1);
		}
	}

	static public boolean isEmptyDirectory(File dir) {
		if (!dir.isDirectory()) {
			throw new IllegalArgumentException("[" + dir
					+ "] must be a directory");
		}
		String[] filesInDir = dir.list();
		if (filesInDir == null || filesInDir.length == 0) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Return the set of files matching the stemRegex as found in 'directory'. A
	 * stemRegex does not contain any slash characters or any folder seperators.
	 * 
	 * @param file
	 * @param stemRegex
	 * @return filelist
	 */
	public static File[] filesInFolderMatchingStemRegex(File file,
			final String stemRegex) {

		if (file == null) {
			return new File[0];
		}
		if (!file.exists() || !file.isDirectory()) {
			return new File[0];
		}
		File[] matchingFileArray = file.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.matches(stemRegex);
			}
		});
		return matchingFileArray;
	}

	static public int extractCounter(File file, final String stemRegex) {
		Pattern p = Pattern.compile(stemRegex);
		String lastFileName = file.getName();

		Matcher m = p.matcher(lastFileName);
		if (!m.matches()) {
			throw new IllegalStateException("The regex [" + stemRegex
					+ "] should match [" + lastFileName + "]");
		}
		String counterAsStr = m.group(1);
		int counter = new Integer(counterAsStr).intValue();
		return counter;
	}

	public static String slashify(String in) {
		return in.replace('\\', '/');
	}

	public static void removeEmptyParentDirectories(File file,
			int recursivityCount) {
		// we should never go more than 3 levels higher
		if (recursivityCount >= 3) {
			return;
		}
		File parent = file.getParentFile();
		if (parent.isDirectory() && FileBlockFilter.isEmptyDirectory(parent)) {
			parent.delete();
			removeEmptyParentDirectories(parent, recursivityCount + 1);
		}
	}
}
