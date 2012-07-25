/**
 * 广播事件定义
 */
package com.cattsoft.collect.net.listener;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.EventListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cattsoft.collect.net.process.ProcessResult;

/**
 * 基本事件监听器.
 * 实现{@link EventListener}接口.
 * 已定义的事件列表:
 * <pre><blockquote><code>
 *{@link #begin()}
 *
 *{@link #afterExec(ProcessResult, int)}
 *
 *{@link #beforeExec(int)}
 *
 *{@link #finish()}
 *
 *{@link #afterExec(ProcessResult, int)}
 *
 *{@link #transport(String, boolean)}
 *
 *调用{@link #fireEvent(EventType, Object...)}方法发送事件</code></blockquote></pre>
 * @author ChenXiaohong
 */
public abstract class BaseListener implements EventListener {
	private final Logger logger = LoggerFactory.getLogger(getClass());
	/*** 任务完成事件标记*/
	public final static String DONE_TAG = "DONE";
	/*** 换行符*/
	protected final String line_separator = System.getProperty("line.separator", "\n");
	// 日期时间格式化
	/*** 日期 yyyy-MM-dd*/
	public static final SimpleDateFormat DATE_SDF = new SimpleDateFormat("yyyy-MM-dd");
	/*** 时间 HH:mm:ss*/
	public static final SimpleDateFormat TIME_SDF = new SimpleDateFormat("HH:mm:ss");
	/*** 日期时间 yyyy-MM-dd HH:mm:ss*/
	public static final SimpleDateFormat DATETIME_SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	/**
	 * 开始事件
	 */
	public void begin() {}

	/**
	 * @param index
	 */
	public void beforeExec(int index) {}

	/**
	 * @param result {@link ProcessResult}
	 * @param index 数据下标
	 */
	public void afterExec(ProcessResult result, int index) {}

	/**
	 * 完成
	 */
	public void finish() {}

	/**
	 * 完成事件之后
	 */
	public void afterFinish() {}


	/** 传输文件.
	 * @param path 数据文件路径
	 * @param backup 是否进行备份
	 */
	public void transport(String path, boolean backup) {}

	/** 特殊完成事件.
	 * 用于当一个采集周期完成时发送通知事件.
	 */
	public void complete() {}


	/**
	 * 广播事件
	 * @param event 事件名称
	 * @param args 参数列表
	 */
	public void fireEvent(EventType event, Object...args) {
		try {
			Method[] methods = getClass().getMethods();
			//查找方法
			for (Method method : methods) {
				if(method.getName().equals(event.name)) {
					method.invoke(this, args);
				}
			}
		} catch (Exception e) {
			//广播事件失败
			String exmsg = "Message:" + e.getMessage() + "." + (null != e.getCause()? "Cause:" + e.getCause().getMessage() : "");
			logger.error("Event broadcast failed! - event:{},params:{} - Error:" + exmsg, event.name, Arrays.toString(args));
		}
	}


	/**
	 * 事件类型.
	 * @author ChenXiaohong
	 */
	public enum EventType {
		/*** 开始*/
		BEGIN("begin"), 
		/*** 执行前*/
		BEFORE_EXECUTE("beforeExec"), 
		/*** 执行后*/
		AFTER_EXECUTE("afterExec"), 
		/*** 完成*/
		FINISH("finish"), 
		/*** 完成后*/
		AFTER_FINISH("afterFinish"),
		/*** 传输事件*/
		TRANSFER("transport"),
		/*** 特殊完成事件*/
		COMPLETE("complete");

		/**
		 * @param name 事件名称
		 */
		private EventType(String name) {
			this.name = name;
		}

		/*** 事件名称*/
		public final String name;
	}
}
