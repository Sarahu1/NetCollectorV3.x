/**
 * 
 */
package com.cattsoft.collect.manage.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

import com.cattsoft.collect.manage.logging.LogManager;

/** 程序配置文件工具.
 * 提供对配置信息的读取保存
 * @author Xiaohong
 *
 */
public class ConfigUtils {
	private static Logger logger = LogManager.getLogger(ConfigUtils.class.getSimpleName());
	/*** 配置文件路径.可使用系统属性 app.config.path 配置 */
	private static String path;
	
	static {
		// 设置配置文件路径
		path = System.getProperty("app.config.path");
		if (null == path || "".equals(path)) {
			path = System.getProperty("java.io.tmpdir",
					System.getProperty("user.dir"))
					+ java.io.File.separator + "collect_config";
		}
	}
	
	/** 
	 * @return 配置信息
	 */
	public static Properties getProp() {
		Properties prop = new Properties();
		FileInputStream fis = null;
		try {
			File file = new File(path);
			if(!file.exists())
				file.createNewFile();
			fis = new FileInputStream(file);
			prop.load(fis);
		} catch (Exception e) {
			logger.severe("无法获取程序配置信息." + e.toString());
		} finally {
			if(null != fis) {
				try {
					fis.close();
				} catch (IOException e) {
				}
			}
		}
		return prop;
	}
	
	/** 保存配置
	 * @param prop
	 * @param comments 注释
	 */
	public static void store(Properties prop, String comments) {
		FileOutputStream fos = null;
		try {
			File file =  new File(path);
			if(!file.exists())
				file.createNewFile();
			fos = new FileOutputStream(file);
			prop.store(fos, comments);
			fos.flush();
		} catch (Exception e) {
			logger.severe("保存配置置信息失败!" + e.toString());
		} finally {
			if(null != fos) {
				try {
					fos.close();
				} catch (IOException e1) {
				}
			}
		}
	}
	
	/** 根据操作类型获取语句
	 * @param type 执行操作命令类型(start/stop/restart)
	 * @param def 默认值
	 * @return 命令行
	 */
	public static String getShell(String type, String def) {
		Properties prop = new Properties();
		try {
			File file = new File(getCatalog() + "/" + "shell.properties");
			FileInputStream fis = new FileInputStream(file);
			prop.load(fis);
			fis.close();
		} catch (Exception e) {
		}
		return prop.getProperty(type, def);
	}
	
	/**
	 * 重置配置
	 */
	public static void reset() {
		try {
			// 删除配置文件
			File file = new File(path);
			if(file.exists()) {
				file.delete();
			}
		} catch (Exception e) {
		}
	}
	

	/**
	 * @return 程序工作目录
	 */
	public static String getCatalog() {
		String catalog = System.getProperty("user.dir");
		try {
			java.io.File pathFile = new java.io.File(ConfigUtils.class.getProtectionDomain()
					.getCodeSource().getLocation().toURI()).getParentFile();
			if(pathFile.getPath().endsWith("lib")) {
				pathFile = pathFile.getParentFile();
			}
			catalog = pathFile.getCanonicalPath();
		} catch (Exception e1) {
		}
		return catalog;
	}
}
