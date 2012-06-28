/**
 * 
 */
package com.cattsoft.collect.io.file.split;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.cattsoft.collect.io.file.utils.StringUtils;

/** 文件分割工具.
 * <blockquote>
 * <pre><code>
 * split(file)
 * 方法将文件分割成指定大小的数据文件
 * 
 * split(file, tag)
 * 方法将文件按行标识分割数据文件为指定大小文件
 * </code></pre>
 * </blockquote>
 * @author ChenXiaohong
 */
public class FileSplitter {
	/*** 缓冲区大小*/
	private int bufferSize = 1024;
	/*** 数据片段输出目录*/
	private String output;
	/*** 文件片段大小*/
	private long limit;
	/*** 文件内容分割行标识*/
	private String splitTag;
	/*** 分割完成后删除源数据文件*/
	private boolean delete;
	
	/**
	 * @param limit 文件分割片段长度
	 * @param output 片段文件输出目录
	 */
	public FileSplitter(long limit, String output) {
		this.limit = limit;
		this.output = output;
	}
	
	/**
	 * @param limit 文件分割片段长度
	 */
	public FileSplitter(long limit) {
		this(limit, null);
	}
	
	/** 按大小分割文件.
	 * 片段数据文件将自动写入到磁盘.
	 * @param path 数据文件路径
	 * @throws IOException 
	 */
	public List<String> split(String path) throws IOException {
		return split(path, null);
	}
	
	/** 按数据行标识分割文件.
	 * 片段数据文件将自动写入到磁盘.
	 * @param path 数据文件路径
	 * @param tag 行文件标识,为null时将默认使用文件长度控制分割
	 * @throws IOException 
	 */
	public List<String> split(String path, String tag) throws IOException {
		List<String> paths = new ArrayList<String>();
		File source = new File(path);
		List<FileBlock> parts = null;
		if(null != tag && !tag.isEmpty()) {
			parts = slice(source, tag);
		} else {
			parts = slice(source);
		}
		try {
			paths = writePart(source, parts);
			if(delete) {
				// 删除文件
				source.delete();
			}
		} finally {
			//
		}
		return paths;
	}
	
	/** 按数据行标识分割文件.
	 * @param source 源数据文件
	 * @param lineTag 行标记
	 * @return 文件片段列表
	 * @throws IOException
	 */
	public List<FileBlock> slice(File source, String lineTag) throws IOException {
		String prefix = source.getName().substring(0, source.getName().lastIndexOf("."));
        String suffix = source.getName().substring(source.getName().lastIndexOf("."));
        
		List<FileBlock> parts = new LinkedList<FileBlock>();
		BufferedRandomAccessFile raf = new BufferedRandomAccessFile(source, "r");
//		RandomAccessFile raf = new RandomAccessFile(source, "r");
		FileChannel channel = raf.getChannel();
		
		long offset = channel.position();
		long position = 0;
		int index = 0;
		try {
			// 优化效率,跳过部分数据
			channel.position((limit - bufferSize));
			String line;
			while((line = raf.readLine()) != null) {
				if(line.equals(lineTag)) {
					position = channel.position();
					if((position - offset) >= limit) {
						index++;
						// 片段文件名
						String tmppath = prefix + ".part" + index + suffix;
						parts.add(new FileBlock(source, tmppath, index, offset, position));
						offset = position;
						// 跳过数据片段
						long limit_offset = (offset + limit) - bufferSize;
						if(limit_offset < channel.size()) {
							channel.position(limit_offset);
						} else {
							channel.position(channel.size() - bufferSize);
						}
					}
				}
			}
			// 处理最后一片
			if(offset < channel.size()) {
				index++;
				// 片段文件名
				String tmppath = prefix + ".part" + index + suffix;
				parts.add(new FileBlock(source, tmppath, index, offset, channel.size()));
			}
			// 重置片段路径
			DecimalFormat df = new DecimalFormat(StringUtils.repeat("0", String.valueOf(parts.size()).length()));
			for (int i = 0; i < parts.size(); i++) {
				String path = prefix + "_p" + parts.size() +".part" + df.format((i+1)) + suffix;
				parts.get(i).setPath(path);
			}
		} catch (IOException e) {
			throw e;
		} finally {
			if(null != raf) {
				raf.close();
			}
			if(null != channel) {
				channel.close();
			}
		}
		return parts;
	}
	
	/** 按数据长度分割文件片段.
	 * @param source 数据文件
	 * @return 文件片段列表
	 */
	public List<FileBlock> slice(File source) {
		List<FileBlock> parts = new LinkedList<FileBlock>();
		long size = ((source.length() % limit == 0) ? (source.length() % limit)
				: ((source.length() / limit) + 1));
		DecimalFormat df = new DecimalFormat(StringUtils.repeat("0", String.valueOf(size).length()));
		long offset = -1;
		long length = -1;
		String prefix = source.getName().substring(0, source.getName().lastIndexOf("."));
        String suffix = source.getName().substring(source.getName().lastIndexOf("."));
		for (int i = 1; i <= size; i++) {
			// 片段文件名
			String tmppath = prefix + "_p" + size + ".part" + df.format(i) + suffix;
			// 偏移
			offset = (i - 1) * limit;
			// 长度
			length = i * limit;
			// 最后一块片段时读取所有长度
			if (i == size)
				length = source.length();
			parts.add(new FileBlock(source, tmppath, i, offset, length));
		}
		return parts;
	}
	
	/** 数据片段写入到文件.
	 * @param source 源数据文件
	 * @param parts 文件片段列表
	 * @throws IOException 写入出现错误时将抛出该错误
	 */
	public List<String> writePart(File source, List<FileBlock> parts) throws IOException {
		List<String> fileList = new LinkedList<String>();
		// 输出文件路径
		String outputpath = source.getParentFile().getPath();
		if(null != output) {
			// 设置输出目录
			outputpath = (output.trim().isEmpty() ? outputpath : output);
		}
		RandomAccessFile raf = new RandomAccessFile(source, "r");
		FileChannel infc = raf.getChannel();
		try {
			RandomAccessFile tmpraf = null;
			File outputFile = null;
			for (FileBlock part : parts) {
				FileChannel outfc = null;
				try {
					File partFile = new File(part.getPath());
					// 输出文件
					outputFile = new File(outputpath + File.separator + partFile.getName());
					// 文件存在时删除已存在的文件
					if(outputFile.exists()) {
						outputFile.delete();
					} else {
						// 创建目录
						if(outputFile.getParentFile() != null) {
							outputFile.getParentFile().mkdirs();
						}
					}
					// 创建新文件
					outputFile.createNewFile();
					
					//读写模式
					tmpraf = new RandomAccessFile(outputFile, "rw");
					outfc = tmpraf.getChannel();
					
					// 写入到文件
					infc.transferTo(part.getOffset(), part.getPosition(), outfc);
					// 添加到路径列表
					fileList.add(outputFile.getPath());
				} catch (IOException e) {
					throw new IOException("写入数据到文件"
							+ ((null != outputFile) ? outputFile.getPath()
									: part.getPath()) + "时出现错误!", e.getCause());
				} catch (Exception e) {
					throw e;
				} finally {
					if(null != outfc) {
						outfc.close();
					}
					if(null != tmpraf) {
						tmpraf.close();
					}
				}
			}
		} catch (Exception e) {
			throw new IOException(e);
		} finally {
			if(null != infc) {
				infc.close();
			}
			if(null != raf) {
				raf.close();
			}
		}
		return fileList;
	}
	
	/**
	 * @param bufferSize 数据缓冲大小
	 */
	public void setBufferSize(int bufferSize) {
		this.bufferSize = bufferSize;
	}
	
	public int getBufferSize() {
		return bufferSize;
	}
	
	/**
	 * @param output 输出目录
	 */
	public void setOutput(String output) {
		this.output = output;
	}
	
	public String getOutput() {
		return output;
	}
	
	/**
	 * @param limit 文件分割大小
	 */
	public void setLimit(long limit) {
		this.limit = limit;
	}
	
	public long getLimit() {
		return limit;
	}
	
	/**
	 * @param splitTag 内容分割标识
	 */
	public void setSplitTag(String splitTag) {
		this.splitTag = splitTag;
	}
	
	public String getSplitTag() {
		return splitTag;
	}
	
	/**
	 * @param delete 是否在分割完成后删除源文件
	 */
	public void setDelete(boolean delete) {
		this.delete = delete;
	}
	
	public boolean isDelete() {
		return delete;
	}
}
