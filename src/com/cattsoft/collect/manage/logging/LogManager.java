/**
 * 
 */
package com.cattsoft.collect.manage.logging;

import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/** 日志工具获取
 * @author 陈小鸿
 * @author chenxiaohong@mail.com
 *
 */
public class LogManager {
	private static java.util.logging.FileHandler handler = null;
	
	static {
		try {
			handler = new java.util.logging.FileHandler();
			handler.setFormatter(new SimpleFormatter());
		} catch (Exception e) {
		}
	}
	
	/**
	 * @param name
	 * @return
	 */
	public static Logger getLogger(String name) {
		Logger logger = Logger.getLogger(name);
		try {
			if(null != handler) {
				logger.addHandler(handler);
			}
		} catch (Exception e) {
			logger.severe("获取日志工具出现异常!可能无法正常输出日志");
		}
		return logger;
	}
}
