/**
 * 
 */
package com.cattsoft.collect.net.writer;

import java.io.FileWriter;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cattsoft.collect.net.listener.WriterListener;
import com.cattsoft.collect.net.process.ProcessResult;

/**
 * 数据处理进度记录监听器.
 * 监听数据处理进度并写入到文件.
 * 
 * 当命令执行器需要运行为周期性任务时,应设置 delete 属性为true,在进度完成时删除记录文件.
 * 否则当命令在第一次任务完成后,进度为完成状态,将导致以后的周期性任务不会运行.
 * @author ChenXiaohong
 */
public class ProgressWriterListener extends WriterListener {
	private final Logger logger = LoggerFactory.getLogger(getClass());
	/**
	 * @param path 记录文件保存路径
	 */
	public ProgressWriterListener(String path) {
		this(path, "{result}{newline}");
	}

	/**
	 * @param path 进度文件保存路径
	 * @param expression 进度数据表达式
	 */
	public ProgressWriterListener(String path, String expression) {
		super(path, expression);
	}

	public void flush() {
		super.flush();
		FileWriter writer = null;
		try {
			writer = new FileWriter(path, false);
			if(cache.size() > 0) {
				//写入缓存中最后行
				writer.write(cache.get(cache.size() - 1));
				//清空缓存
			}
			cache.clear();
		} catch (IOException e) {
			//数据记录写入失败
			logger.error("Data record is written to fail!{}", e.getMessage());
		} finally {
			try {
				if(null != writer) {
					//刷新缓冲区数据
					writer.flush();
					writer.close();
				}
			} catch (Exception e2) {
				//刷新数据缓冲区失败!可能造成数据丢失,请检查
				logger.error("Failed to refresh the data buffer and may cause data loss, please check.{}", e2.getMessage());
			}
		}
	}

	public void finish() {
		// 添加完成标记
		cache.add(DONE_TAG);
		super.finish();
	}

	public void afterExec(ProcessResult result, int index) {
		result = process(result);
		//适配器处理结果集数据
		cache.add(expressionProcess(String.valueOf(index)));
		super.afterExec(result, index);
	}
}
