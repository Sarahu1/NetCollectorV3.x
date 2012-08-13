/**
 * 
 */
package com.cattsoft.collect.net.reader;

import java.io.File;
import java.io.FileReader;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cattsoft.collect.net.listener.BaseListener;

/**
 * @author ChenXiaohong
 *
 */
public class BasicFileReader extends BaseListener {
	private final Logger logger = LoggerFactory.getLogger(getClass());
	/*** 数据断点记录*/
	protected int skip = -1;
	/*** 数据读取完成标记*/
	protected static String DONE_TAG = "DONE";
	/*** 数据文件路径*/
	protected final String filePath;
	/*** 断点记录文件路径*/
	protected final String skipPath;
	/*** 忽略注释,如果为true, 以<code>#</code>号开头的行将被忽略 */
	protected boolean ignoreAnnotate = true;
	/*** 是否删除数据文件*/
	protected boolean deleteFile = false;
	/*** 是否删除断点记录文件*/
	protected boolean deleteSkipFile = false;
	/**
	 * @param filePath 数据文件路径
	 */
	public BasicFileReader(String filePath) {
		this(filePath, null);
	}

	/**
	 * @param filePath 数据文件路径
	 * @param skipPath 断点记录文件路径
	 */
	public BasicFileReader(String filePath, String skipPath) {
		this.filePath = filePath;
		this.skipPath = skipPath;
		setSkip();
	}

	/**
	 * 读取所有数据行
	 * @return 行数组
	 */
	public List<String> readLines() {
		List<String> lines = new LinkedList<String>();
		FileReader reader = null;
		File file = new File(filePath);
		if(!file.exists()) {
			//数据文件不存在
			logger.error("Data file does not exist!");
			return lines;
		}
		//skip 小于0时表示数据文件处理已完成
		if(skip > -1) {
			try {
				reader = new FileReader(file);
				LineReader lineReader = new LineReader(reader);
				String line = null;
				//按行读取数据
				int curr_skip = 0;
				while((line = lineReader.readLine()) != null) {
					//当前行大于skip值且数据不为空,添加到数据列表
					if((++curr_skip) > skip && !line.isEmpty()) {
						if(isIgnoreAnnotate() && line.trim().startsWith("#"))
							continue;
						lines.add(line);
					}
				}
			} catch (Exception e) {
				//读取数据文件失败
				logger.error("Failed to read data files!{}", e.getMessage());
			} finally {
				try {
					if(null != reader)
						reader.close();
				} catch (Exception e2) {
					//
				}
			}
			// 由于记录读取器的特殊性,需在数据读取之后就将记录文件删除
			//删除数据文件处理
			if(deleteSkipFile) {
				logger.info("Delete data breakpoint log file:{} - {}", file.getPath(), file.delete()? "Successfully" : "Failure");
			}
		}
		// 返回记录数据
		return lines;
	}

	/**
	 * 读取断点记录文件.
	 */
	public void setSkip() {
		//断点行初始设置为0
		skip = 0;
		if(null == skipPath)
			return;
		File file = new File(skipPath);
		if(file.exists()) {
			//读取首行数据并设置断点记录
			FileReader reader = null;
			try {
				reader = new FileReader(file);
				LineReader lineReader = new LineReader(reader);
				String line = lineReader.readLine();
				if(!DONE_TAG.equals(line)) {
					//设置值
					skip = Integer.parseInt(line);
				} else {
					skip = -1;
				}
			} catch (Exception e) {
				//断点读取记录数据读取失败
				logger.error("Breakpoint read failed to read the recorded data!{}", e.getMessage());
			} finally {
				try {
					if(null != reader)
						reader.close();
				} catch (Exception e) {
					//
				}
			}
			// 删除断点记录文件
			if(deleteSkipFile) {
				logger.info("Delete data breakpoint log file:{} - {}", file.getPath(), file.delete()? "Successfully" : "Failure");
			}
		} else {
			//未找到断点读取记录文件
			logger.debug("Not found the breakpoint to read the file:{}", skipPath);
		}
	} 

	/**
	 * @return 当前断点行
	 */
	public int getSkip() {
		return skip;
	}
	
	public String getFilePath() {
		return filePath;
	}

	/**
	 * 更新数据完成标记
	 * @param tag 标记值
	 */
	public void setDoneTag(String tag) {
		DONE_TAG = tag;
	}
	
	/**
	 * @param ignoreAnnotate the ignoreAnnotate to set
	 */
	public void setIgnoreAnnotate(boolean ignoreAnnotate) {
		this.ignoreAnnotate = ignoreAnnotate;
	}
	
	/**
	 * @return the ignoreAnnotate
	 */
	public boolean isIgnoreAnnotate() {
		return ignoreAnnotate;
	}

	/**
	 * @param deleteFile 是否删除数据文件
	 */
	public void setDeleteFile(boolean deleteFile) {
		this.deleteFile = deleteFile;
	}

	/**
	 * @param deleteSkipFile 删除断点记录文件
	 */
	public void setDeleteSkipFile(boolean deleteSkipFile) {
		this.deleteSkipFile = deleteSkipFile;
	}
}