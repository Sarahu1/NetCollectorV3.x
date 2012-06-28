/**
 * 
 */
package com.cattsoft.collect.net.writer;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cattsoft.collect.io.file.split.FileSplitter;
import com.cattsoft.collect.net.actuator.Actuator;
import com.cattsoft.collect.net.listener.WriterListener;
import com.cattsoft.collect.net.process.ProcessResult;

/**
 * 缓存数据写入监听器.
 * 使用缓存区减少文件写入次数.
 * 调用{@link #begin()}后数据将重新定向(文件名在原文件名后追加[_下标],不包含中括号)
 * @author ChenXiaohong
 *
 */
public class BufferedWriterListener extends WriterListener {
	private Logger logger = LoggerFactory.getLogger(getClass());
	/*** 当前写入器数据文件是否需要进行传输*/
	private boolean transfer = false;
	/*** 数据传输完成后是否删除本地数据文件.默认删除*/
	private boolean backup = true;
	/*** 当前命令执行器.由写入器触发{@link #transfer(String)}事件,传输数据文件*/
	private Actuator actuator = null;
	/*** 文件分割工具,为null时数据文件不进行分割 */
	private FileSplitter spliter;

	/**
	 * @param path 数据文件路径
	 * @param expression 数据内容表达式 
	 */
	public BufferedWriterListener(String path, String expression) {
		super(path, expression);
	}

	/**
	 * @param path 数据文件路径
	 */
	public BufferedWriterListener(String path) {
		this(path, null);
	}

	public void flush() {
		super.flush();
		FileWriter writer = null;
		try {
			if(cache.size() == 0) {
				logger.info("cache data size is empty, do not write to file{}.", path);
				return;
			}
			writer = new FileWriter(path, append);
			for (String line : cache) {
				writer.write(line);
			}
			cache.clear();
		} catch (Exception e) {
			//写入数据时出现错误
			logger.error("An error occurred while writing data!{}", e.getMessage());
		} finally {
			try {
				if(null != writer) {
					writer.flush();
					writer.close();
				}
			} catch (Exception e2) {
				//刷新数据缓冲区失败!可能造成数据丢失,请检查
				logger.error("Failed to refresh the data buffer and may cause data loss, please check:{}", e2.getMessage());
			}
		}
	}

	public void afterExec(ProcessResult result, int index) {
		if(!isEnabled())
			return;
		//使用克隆的对象,不修改结果本身
		ProcessResult clone = result.fakeClone();
		super.afterExec(clone, index);
		if(null == clone)
			return;
		//是否已到达最大文件重定向次数
		if(maxFrequency == 0 || this.getFrequency() <= this.getMaxFrequency()) {
			for (String pro_result : clone.getResult()) {
				//处理写入器自身内容修饰表达式,添加数据到缓存区
				String new_result = expressionProcess(pro_result, result.getParams());
				cache.add(new_result);
			}
		}
	}
	
	public void finish() {
		super.finish();
		if(transfer) {
			if(null != actuator) {
				java.util.List<String> paths = new ArrayList<String>();
				// 添加到上传列表
				paths.add(this.path);
				// 是否需要分割
				if(null != spliter) {
					try {
						// 判断文件是否符合分割长度限制
						if(new java.io.File(this.path).length() > spliter.getLimit()) {
							logger.info("Data file({}) is being split..", this.path);
							// 分割数据文件
							paths = spliter.split(this.path, spliter.getSplitTag());
						}
					} catch (IOException e) {
						logger.error("Partitioning the data file({}), an error occurs, " +
								"the file will be transmitted directly.", this.path);
					}
				}
				// 发布上传通知事件
				for (String path : paths) {
					actuator.fireEvent(EventType.TRANSFER, path, isBackup());
				}
			} else {
				logger.error("Not set the actuator, data file failed to transfer!path:{}", path);
			}
		}
	}

	/**
	 * @param transfer 数据文件是否传输
	 */
	public void setTransfer(boolean transfer) {
		this.transfer = transfer;
	}

	/**
	 * @return 是否传输
	 */
	public boolean isTransfer() {
		return transfer;
	}

	/**
	 * @param actuator 当前命令执行器
	 */
	public void setActuator(Actuator actuator) {
		this.actuator = actuator;
	}

	/**
	 * @return 当前执行器类
	 */
	public Actuator getActuator() {
		return actuator;
	}

	/**
	 * @param spliter
	 *            文件分割工具
	 */
	public void setSpliter(FileSplitter spliter) {
		this.spliter = spliter;
	}

	public FileSplitter getSpliter() {
		return spliter;
	}
	
	public boolean isBackup() {
		return backup;
	}
	
	/**
	 * @param backup 传输完成后是否备份本地数据文件
	 */
	public void setBackup(boolean backup) {
		this.backup = backup;
	}
}
