/**
 * 
 */
package com.cattsoft.collect.net.process;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 命令执行器. 执行命令并返回输出结果. 调用{@link #run()}方法运行
 * 
 * @author ChenXiaohong
 */
public class ProcessRunner {
	private final Logger logger = LoggerFactory.getLogger(getClass());
	/***系统名称*/
	private static final String os_name;
	/***行分割符*/
	private static final String line_separator;
	/***进程生成器*/
	private final ProcessBuilder processBuilder;
	/***当前进程*/
	private Process process;
	/***退出状态(0表示正常退出)*/
	private int exitValue = -1;
	
	static {
		os_name = System.getProperty("os.name", "linux").toLowerCase();
		line_separator = System.getProperty("line.separator", "\r");
	}
	
	/**
	 * @param directory 命令工作目录
	 * @param cmd 命令行
	 */
	public ProcessRunner(String directory, String[] cmd) {
		processBuilder = new ProcessBuilder(cmd);
		// 合并错误输出流
		processBuilder.redirectErrorStream(true);
		if(null != directory) {
			//设置命令工作目录
			File file = new File(directory);
			if(file.exists()) {
				processBuilder.directory(new File(directory));
			}
		}
	}

	/**
	 * @param cmd 命令行
	 */
	public ProcessRunner(String[] cmd) {
		this(null, cmd);
	}

	/**
	 * @return 命令运行结果
	 */
	public String run() {
		//结果
		StringBuffer result = new StringBuffer();
		BufferedReader reader = null;
		try {
			//启动进程
			process = processBuilder.start();
			//GBK 编码确保中文输出
			reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
			//读取输出内容
			String line;
			while((line = reader.readLine()) != null) {
				result.append(line).append(line_separator);
			}
			//等待退出,获取状态
			exitValue = process.waitFor();
		} catch (IOException e) {
			//数据读取错误或命令有误,请检查
			logger.error("The data read error or command error, please check:{}",e.getMessage());
		} catch (InterruptedException e) {
			//命令运行期间出现超时或线程中断会引发该错误,可以不做处理
			//logger.error("Command to run overtime:{}", processBuilder.command());
		} finally {
			try {
				if(null != reader)
					reader.close();
				if(null != process)
					process.destroy();
			} catch (Exception e2) {
			}
		}
		return result.toString();
	}
	
	/**
	 * @return 运行状态.
	 * 0:正常
	 */
	public int getExitValue() {
		return exitValue;
	}
	
	/**
	 * @return 当前系统名称
	 */
	public static String getOsName() {
		return os_name;
	}
}
