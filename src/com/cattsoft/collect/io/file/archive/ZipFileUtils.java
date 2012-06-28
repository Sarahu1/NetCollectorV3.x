/**
 * 
 */
package com.cattsoft.collect.io.file.archive;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** 文件压缩.
 * 提供对单个文件和文件夹的压缩功能.
 * @author ChenXiaohong
 */
public class ZipFileUtils {
	public static void zip(String source, String to) throws IOException {
		zip(source, to, null);
	}
	
	/** 创建压缩文件
	 * @param source 源文件路径
	 * @param to 压缩文件路径
	 * @param comment 文件备注
	 * @throws IOException 
	 */
	public static void zip(String source, String to, String comment) throws IOException {
		File sourceFile = new File(source);
		File toFile = new File(to);
		if(!toFile.exists()) {
			if(null != toFile.getParentFile()) {
				toFile.getParentFile().mkdirs();
			}
			if(toFile.isDirectory()) {
				toFile.mkdirs();
			}
		}
		//获取文件或目录下所有子文件/目录
		File[] files = getSubFiles(sourceFile);
		
		FileOutputStream fos = null;
		//创建压缩文件输出流
		ZipOutputStream zos = null;
		
		FileInputStream fis = null;
		InputStream is= null;
		try {
			fos = new FileOutputStream(to);
			zos = new ZipOutputStream(fos);
			// 最强压缩级别
			zos.setLevel(Deflater.BEST_COMPRESSION);
			ZipEntry zipentry = null;
			for (File file : files) {
				zipentry = new ZipEntry(sourceFile.isDirectory() ? getAbsFileName(source, file) : file.getName());
				zipentry.setSize(file.length());
				zipentry.setTime(file.lastModified());
				
				zos.putNextEntry(zipentry);
				//写入数据
				int len = -1;
				byte[] buffer = new byte[1024];
				try {
					fis = new FileInputStream(file);
					is = new BufferedInputStream(fis);
					while ((len = is.read(buffer, 0, 1024)) != -1) {
						zos.write(buffer, 0, len);
					}
				} catch (IOException e) {
					throw e;
				} finally {
					if(null != is)
						is.close();
					if(null != fis)
						fis.close();
					zos.closeEntry();
				}
			}
			// 写入备注
			if(null != comment)
				zos.setComment(comment);
			zos.finish();
		} catch (IOException e) {
			throw e;
		} finally {
			if(null != zos)
				zos.close();
			if(null != fos)
				fos.close();
		}
	}
	
	/** 测试压缩文件.
	 * 测试文件数据是否正确
	 * @param filename 文件路径名称
	 * @throws Exception
	 */
	public static void test(String filename) throws Exception {
		ZipFile zip = null;
		try {
			zip = new ZipFile(filename);
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while(entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if(entry.isDirectory()) {
					// 
				} else {
					entry.getCrc();
				}
			}
		} catch (Exception e) {
			throw e;
		} finally {
			if(null != zip) {
				try {
					zip.close();
				} catch (Exception e2) {
				}
			}
		}
	}
	
	/** 读取压缩文件注释.
	 * @param filename 文件路径名称
	 * @return 注释信息
	 * @throws Exception 
	 */
	public static String extractZipComment(String filename) throws Exception {
		String retStr = null;
		FileInputStream in = null;
		try {
			File file = new File(filename);
			// 测试ZIP文件是否存在错误
			test(file.getAbsolutePath());
			int fileLen = (int) file.length();
			in = new FileInputStream(file);

			/*
			 * The whole ZIP comment (including the magic byte sequence) MUST
			 * fit in the buffer otherwise, the comment will not be recognized
			 * correctly
			 * 
			 * You can safely increase the buffer size if you like
			 */
			byte[] buffer = new byte[Math.min(fileLen, 8192)];
			int len;

			in.skip(fileLen - buffer.length);

			if ((len = in.read(buffer)) > 0) {
				retStr = getZipCommentFromBuffer(buffer, len);
			}

			in.close();
		} catch (Exception e) {
			throw e;
		} finally {
			if(null != in) {
				try {
					in.close();
				} catch (IOException e) {
				}
			}
		}
		return retStr;
	}

	private static String getZipCommentFromBuffer(byte[] buffer, int len) {
		byte[] magicDirEnd = { 0x50, 0x4b, 0x05, 0x06 };
		int buffLen = Math.min(buffer.length, len);
		// Check the buffer from the end
		for (int i = buffLen - magicDirEnd.length - 22; i >= 0; i--) {
			boolean isMagicStart = true;
			for (int k = 0; k < magicDirEnd.length; k++) {
				if (buffer[i + k] != magicDirEnd[k]) {
					isMagicStart = false;
					break;
				}
			}
			if (isMagicStart) {
				// Magic Start found!
				int commentLen = buffer[i + 20] + buffer[i + 21] * 256;
				int realLen = buffLen - i - 22;
//				System.out.println("ZIP comment found at buffer position "
//						+ (i + 22) + " with len=" + commentLen + ", good!");
				if (commentLen != realLen) {
					commentLen = realLen;
//					System.out
//							.println("WARNING! ZIP comment size mismatch: directory says len is "
//									+ commentLen
//									+ ", but file ends after "
//									+ realLen + " bytes!");
				}
				String comment = new String(buffer, i + 22, Math.min(
						commentLen, realLen));
				return comment;
			}
		}
//		System.out.println("ERROR! ZIP comment NOT found!");
		return null;
	}	
	
	/** 获取目录下的所有文件和文件夹
	 * @param file 目录
	 * @return 文件列表
	 */
	private static File[] getSubFiles(File file) {
		List<File> files = new ArrayList<File>();
		if(file.isDirectory()) {
			File[] tmpFiles = file.listFiles();
			for (File tmpFile : tmpFiles) {
				if (tmpFile.isFile()) {
					files.add(tmpFile);
				} else {
					Collections.addAll(files, getSubFiles(tmpFile));
				}
			}
		} else {
			Collections.addAll(files, file);
		}
		return files.toArray(new File[files.size()]);
	}
	
	/**
	 * @param source 源压缩文件
	 * @param out 解压路径,默认将解压到当前路径
	 * @throws IOException 
	 */
	public static String[] unZip(String source, String out) throws IOException {
		List<String> unzipfiles = new LinkedList<String>();
		File toFile = new File(out);
		if(!toFile.exists()) {
			if(null != toFile.getParentFile()) {
				toFile.getParentFile().mkdirs();
			}
			toFile.mkdirs();
		}
		java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(source);
		//获取zip文件所有条目
		Enumeration<?> zipentrys = zipFile.entries();
		
		ZipEntry zipentry = null;
		//循环枚举
		while(zipentrys.hasMoreElements()) {
			zipentry = (ZipEntry) zipentrys.nextElement();
			if(zipentry.isDirectory()) {
				//创建目录
				new File(toFile.getPath() + "/" + zipentry.getName()).mkdirs();
				continue;
			}
			//根据解压目录生成文件名称
			File fileName = getRealFileName(toFile.getPath(), zipentry.getName());
			// 写入数据
			FileOutputStream fos = new FileOutputStream(fileName);
			OutputStream os = new BufferedOutputStream(fos);
			InputStream is = new BufferedInputStream(
					zipFile.getInputStream(zipentry));
			int len = 0;
			byte[] buffer = new byte[1024];// 1k
			while ((len = is.read(buffer, 0, 1024)) != -1) {
				os.write(buffer, 0, len);
			}
			is.close();
			os.close();
			fos.close();
			
			unzipfiles.add(fileName.getPath());
		}
		zipFile.close();
		// 返回
		return unzipfiles.toArray(new String[unzipfiles.size()]);
	}
	
	/**
	 * 给定根目录，返回另一个文件名的相对路径，用于zip文件中的路径.
	 * 
	 * @param baseDir java.lang.String 根目录
	 * @param realFileName java.io.File 实际的文件名
	 * @return 相对文件名
	 */
	private static String getAbsFileName(String baseDir, File realFileName) {
		File real = realFileName;
		File base = new File(baseDir);
		String ret = real.getName();
		while (true) {
			real = real.getParentFile();
			if (real == null)
				break;
			if (real.equals(base))
				break;
			else
				ret = real.getName() + "/" + ret;
		}
		return ret;
	}
	
	/** 根据目录获取文件名称
	 * @param baseDir 目录
	 * @param absFileName 文件名
	 * @return 文件
	 */
	private static File getRealFileName(String baseDir, String absFileName){
		String[] dirs = absFileName.split("/");
		File ret = new File(baseDir);
		if (dirs.length > 0) {
			for (int i = 0; i < dirs.length - 1; i++) {
				ret = new File(ret, dirs[i]);
			}
			if (!ret.exists())
				ret.mkdirs();
			ret = new File(ret, dirs[dirs.length - 1]);
			return ret;
		}
		return ret;
	}
}
