/**
 * 
 */
package com.cattsoft.collect.io.utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.configuration.Configuration;

/** 配置管理工具
 * @author ChenXiaohong
 *
 */
public class ConfigUtils {
	private static final Logger logger = Logger.getLogger(ConfigUtils.class.getName());
	
	/** 加载文件到系统属性配置
	 * System.getProperties() - Properties
	 * @param args 参数列表
	 * @param def 默认配置文件名称
	 */
	public static void loadJvmProperty(String[] args, String def) {
		// 查找参数配置
		String config = null;
		try {
			for (int i = 0; i < args.length; i++) {
				if ("-config".equals(args[i])) {
					config = args[i + 1];
				}
			}
			// 查找JVM属性配置
			config = System.getProperty(def, config);
		} catch (Exception e) {
			System.out.println("Usage:-config xxx.properties");
			System.exit(-1);
		}
		// 加载配置
		if (null != config && !"".equals(config)) {
			// 加载
			try {
				FileInputStream fis = new FileInputStream(config);
				// 加载到系统属性
				System.getProperties().load(fis);
				logger.info("属性文件("+config+")已加载");
			} catch (FileNotFoundException e) {
				System.err.println("配置文件(" + config + ")不存在!");
				System.exit(-1);
			} catch (Exception e) {
				System.err.println("配置文件加载失败!" + e.getMessage());
				System.exit(-1);
			}
		} else {
			logger.info("未找到配置文件");
		}
	}
	
	/**
	 * 
	 */
	public static Map<String, Map<String, String>> toMap(Configuration config) {
		return null;
	}
}
