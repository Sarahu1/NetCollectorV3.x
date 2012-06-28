/**
 * 
 */
package com.cattsoft.collect.net.main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.FileSystemXmlApplicationContext;

/**
 * 程序启动主类.
 * 通过{@link #main(String[])}方法使用Spring加载配置文件的方式启动程序,
 * 默认加载程序conf目录下的collect_config.xml配置文件,
 * 可通过{@link #main(String[] args)}中的args参数中添加-config xxx_config.xml指定配置文件
 * @author ChenXiaohong
 * @since JDK1.6
 */
public class CollectionEngine {
	private static final Logger logger = LoggerFactory.getLogger("application_engin");
	
	/**
	 * 主main方法
	 * args数组中添加["-config","xxx_config.xml"]指定配置文件
	 * -monitor x 指定监测点编号.如已设置,将以之前设置的为准
	 * @param args 运行参数.
	 */
	public static void main(String[] args) {
		String config = System.getProperty("config", "conf/collect_config.xml");
		// 设置应用程序标签,缺省设置为java
		System.setProperty("Label", System.getProperty("Label", "java"));
		try {
			for (int i = 0; i < args.length; i++) {
				// 配置文件路径设置
				if("-config".equalsIgnoreCase(args[i])) {
					config = args[i + 1];
				}
				// 监测点配置(默认从shell中获取)
				if("-monitor".equalsIgnoreCase(args[i])) {
					System.setProperty("monitor", System.getProperty("monitor", args[i + 1]));
				}
			}
		} catch (Exception e) {
			System.err.println("Usage:-config conf/collect_config.xml");
			logger.error("Usage:-config conf/collect_config.xml");
		}
		try {
			logger.debug("application configuration file:{}", config);
			new FileSystemXmlApplicationContext(config);
			logger.debug("the process has successfully started.");
			// 监测点编号
			String monitor = System.getProperty("monitor", "unknow");
			logger.debug("current monitor number:{}", monitor);
		} catch (Exception e) {
			String msg = e.getMessage();
			if(null != e.getCause())
				msg = e.getCause().getMessage();
			System.err.println("Process failed to start! " + msg);
			logger.error("Process failed to start! {}", msg);
			// 异常退出
			System.err.println("System quit unexpectedly!");
			logger.error("System quit unexpectedly!");
			System.exit(-1);
		}
	}
}