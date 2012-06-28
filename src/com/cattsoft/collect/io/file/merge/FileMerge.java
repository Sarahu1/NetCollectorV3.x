/**
 * 
 */
package com.cattsoft.collect.io.file.merge;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 数据文件合并.
 * 将多个文件按序号进行合并
 * @author ChenXiaohong
 */
public class FileMerge {
	private static final Logger logger = LoggerFactory.getLogger(FileMerge.class);
	
	/** 
	 * @param output 输出目录
	 * @param files 需要进行合并的文件列表
	 * @throws IOException 合并出现异常时将抛出错误
	 */
	public static void merge(File output, File[] files) throws IOException {
		String[] filepaths = new String[files.length];
		for (int i = 0; i < files.length; i++) {
			filepaths[i] = files[i].getPath();
		}
		merge(output.getPath(), filepaths);
	}
	
	/**
	 * @param outpath 输出文件路径
	 * @param deleteold 删除已有旧文件
	 * @param files  文件列表
	 * @throws IOException
	 */
	public static void merge(String outpath, boolean deleteold, String...files) throws IOException {
		String[] fileblocks = sort(files);
		File outFile = new File(outpath);
		// 判断文件是否存在
		if(outFile.exists()) {
			if(deleteold) {
				// 删除旧文件
				outFile.delete();
			}
		} else {
			if(outFile.getParentFile() != null) {
				outFile.getParentFile().mkdirs();
			}
			outFile.createNewFile();
		}
		
		// 输出通道
		RandomAccessFile outraf = null;
		FileChannel outchannel = null;
		try {
			outraf = new RandomAccessFile(outFile, "rw");
			outchannel = outraf.getChannel();
			// 循环文件列表,合并文件
			// 定位到文件末尾
			outchannel.position(outraf.length());
			for (String block : fileblocks) {
				// 输入通道
				FileInputStream infis = null;
				FileChannel inchannel = null;
				try {
					infis = new FileInputStream(block);
					inchannel = infis.getChannel();

					// 追加到文件
					inchannel.transferTo(0, inchannel.size(), outchannel);
					logger.info("File("+block+") completion of the merger to " + outFile.getAbsolutePath());
				} catch (IOException e) {
					throw new IOException("合并数据到文件"
							+ ((null != outFile) ? outFile.getPath() : outpath)
							+ "时出现错误!", e.getCause());
				} catch (Exception e) {
					throw new IOException(e.getCause());
				} finally {
					// 关闭
					if (null != inchannel) {
						inchannel.close();
					}
					if (null != infis) {
						infis.close();
					}
				}
				// 删除
				File blockFile = new File(block);
				blockFile.delete();
			}
			logger.info("current merge task has been completed");
		} catch (Exception e) {
			throw new IOException(e);
		} finally {
			if(null != outchannel) {
				outchannel.close();
			}
			if(null != outraf) {
				outraf.close();
			}
		}
	}
	
	/**
	 * @param outpath 输出目录
	 * @param files 文件列表
	 * @throws IOException 
	 */
	public static void merge(String outpath, String...files) throws IOException {
		merge(outpath, false, files);
	}
	
	/** 按分块数排序
	 * @param files 文件块
	 * @return 已排列的文件块名称
	 */
	public static String[] sort(String...files) {
		// 取总part数
		int blockcount = getPartCount(files[0]);
		if(blockcount > files.length) {
			// 缺少文件块时抛出错误
			throw new IllegalArgumentException("Missing file block.");
		}
		String[] partfiles = new String[blockcount];
		Pattern partPattern = Pattern.compile("\\.part(\\d+)\\.");
		Matcher matcher = null;
		for (int i = 0; i < files.length; i++) {
			matcher = partPattern.matcher(files[i]);
			if(matcher.find()) {
				int index = Integer.parseInt(matcher.group(1)) - 1;
				partfiles[index] = files[i];
			} else {
				partfiles[i] = files[i];
			}
		}
		return partfiles;
	}
	
	/**
	 * 取文件总块数.
	 */
	public static int getPartCount(String name) {
		//"_p10.part01.zip"
		Matcher matcher = Pattern.compile("_p(\\d+)\\.part").matcher(name);
		if(matcher.find()) {
			return Integer.parseInt(matcher.group(1));
		}
		return 1;
	}
}