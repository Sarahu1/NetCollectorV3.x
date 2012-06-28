/**
 * 
 */
package com.cattsoft.collect.net.report;

import java.io.BufferedOutputStream;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cattsoft.collect.io.file.utils.CommandType;
import com.cattsoft.collect.net.listener.BaseListener;
import com.cattsoft.collect.net.process.ProcessResult;
import com.cattsoft.collect.net.process.ProcessRunner;

/** 系统状态报告收集.
 * 定时收集程序运行状态,Socket通道上传到控制中心.
 * @author ChenXiaohong
 *
 */
public class SystemReportGather extends BaseListener implements Runnable {
	private final Logger logger = LoggerFactory.getLogger(getClass());
	/*** 上报主机*/
	private String[] hosts;
	/*** 主机端口*/
	private int port;
	/*** 睡眠间隔(s)*/
	private long sleep = 3 * 60 * 1000;
	/*** 备用睡眠间隔时长,未处在活跃状态时,状态数据延缓 */
	private long alternateSleep = 10 * 60 * 1000;
	/*** 运行状态标识*/
	private boolean flag = true;
	/*** 时间格式化*/
	private SimpleDateFormat time_sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	/*** 当前命令执行状态*/
	public Map<String, Map<String, String>> status = new HashMap<String, Map<String, String>>();
	/*** 命令执行状态更新超时时长(分)*/
	private int statusTimeout = 30;
	/*** 程序状态获取命令 */
	private String[] cmd = null;
	/*** 程序退出监控 */
	private Thread shutdownHook;
	/*** 是否采集所有命令都已执行完成 */
	private boolean allDone = true;
	
	/**
	 * @param hosts 主机列表
	 * @param port 端口
	 */
	public SystemReportGather(String[] hosts, int port) {
		this(hosts, port, null);
	}
	
	/**
	 * @param hosts 主机列表
	 * @param port 端口
	 * @param cmd 命令行
	 */
	public SystemReportGather(String[] hosts, int port, String[] cmd) {
		this.hosts = hosts;
		this.port = port;
		if(null == cmd || cmd.length == 0) {
			// Label 为程序标签,由Shell指定
			cmd = new String[]{"/bin/sh", "-c", "ps aux | grep " + System.getProperty("Label", "java")};
		}
		setCmd(cmd);
		// 添加程序退出处理
		shutdownHook = new Thread(new Runnable() {
			public void run() {
				// 上报程序停止信息
				reported("kill", 1000 * 10);
				Runtime.getRuntime().removeShutdownHook(shutdownHook);
			}
		});
		// 添加进程意外退出监听
		Runtime.getRuntime().addShutdownHook(shutdownHook);
	}
	
	/* 线程调度方法
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		logger.info("System status information reported to the thread has been started.");
		// 收集报告
		while(flag) {
			// 程序启动后上报状态信息报告
			try {
				// 上报运行状态信息
				reported("normal", 1000 * 90);
			} catch (Exception e) {
				logger.error("程序运行信息报告上报失败!{}", e.toString());
			}
			// 睡眠
			try {
				// 根据程序情况,处理睡眠间隔时长
				Thread.sleep((allDone ? alternateSleep : sleep));
			} catch (InterruptedException e) {
				//
			}
		}
		logger.info("system status information reported to the thread has exited.");
	}
	
	/**
	 * @param appStatus 当前程序状态(normal/stop)
	 * @param timeout 连接超时时长
	 */
	private void reported(String appStatus, int timeout) {
		SystemReport report = new SystemReport();
		report.append("monitor", System.getProperty("monitor", "unknow"));
		String catalog = System.getProperty("user.dir");
		try {
			java.io.File pathFile = new java.io.File(getClass().getProtectionDomain()
					.getCodeSource().getLocation().toURI()).getParentFile();
			if(pathFile.getPath().endsWith("lib")) {
				pathFile = pathFile.getParentFile();
			}
			catalog = pathFile.getCanonicalPath();
		} catch (Exception e1) {
			logger.error("获取应用程序目录出现错误!{}", e1.toString());
		}
		report.append("catalog", catalog);
		report.append("type", System.getProperty("type", "unknow"));
		report.append("time", time_sdf.format(System.currentTimeMillis()));
		report.append("app_status", appStatus);
		String status_info = "状态获取";
		try {
			// 运行命令,获取程序运行状态信息
			// USER       PID %CPU %MEM    VSZ   RSS TTY      STAT START   TIME COMMAND
			ProcessRunner runner = new ProcessRunner(cmd);
			status_info = runner.run();
			if(runner.getExitValue() != 0) {
				logger.error("access to information on the program state is an error!");
			}
		} catch (Exception e) {
			logger.error("failed to get system status information!{}", e.toString());
		}
		report.append("status", status_info);
		// 命令执行状态
		report.append("command", mapToString(status));
		// 循环主机列表,上报状态信息
		for (String host : hosts) {
			Socket socket = null;
			try {
				socket = new Socket();
				// 超时设置为90秒
				socket.setSoTimeout(timeout);
				socket.connect(new InetSocketAddress(host, port), (timeout));
				if(socket.isConnected()) {
					BufferedOutputStream out = new BufferedOutputStream(
							socket.getOutputStream());
					// 发送状态信息
					out.write(report.getReport().getBytes());
					out.flush();
					out.close();
				}
			} catch (ConnectException e) {
				// logger.info("can not connect to host({}), the status is not reported", host);
			} catch (UnknownHostException e) {
				logger.info("the host({}) service is not open, the status is not reported.{}", host, e);
			} catch (SocketTimeoutException e) {
				logger.error("timeout when the connection data services({})!{}", host, e);
			} catch (Exception e) {
				logger.error("reported to the state to the server({}) error!{}", host, e);
			} finally {
				try {
					if (null != socket) {
						socket.close();
						socket = null;
					}
				} catch (Exception e) {
					//
				}
			}
		}
	}
	
	/**
	 * @param sleep 上报睡眠间隔
	 */
	public void setSleep(long sleep) {
		this.sleep = sleep;
	}
	
	public void setFlag(boolean flag) {
		this.flag = flag;
	}
	
	public void setAlternateSleep(long alternateSleep) {
		this.alternateSleep = alternateSleep;
	}
	
	/** 设置系统信息获取命令
	 * @param cmd 
	 */
	public void setCmd(String[] cmd) {
		String pid = getPid();
		for (int i = 0; i < cmd.length; i++) {
			// 替换监测点编号
			cmd[i] = cmd[i].replace("$monitor", System.getProperty("monitor", "unknow"));
			// 替换程序Label
			cmd[i] = cmd[i].replace("$label", System.getProperty("Label", "java"));
			// 替换进程编号
			cmd[i] = cmd[i].replace("$pid", pid);
		}
		this.cmd = cmd;
	}
	
	/**
	 * 获取当前进程编号
	 */
	private String getPid() {
		String pid = "";
		try {
			pid = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
		} catch (Exception e) {
		}
		if(null == pid || "".equals(pid)) {
			// 读取文件
			java.io.BufferedReader br = null;
			java.io.FileReader fr = null;
			try {
				fr = new java.io.FileReader(System.getProperty("service.file", "service.file"));
				br = new java.io.BufferedReader(fr);
				// 读取一行
				if(br.ready()) {
					pid = br.readLine().trim();
				}
			} catch (Exception e) {
				//
			} finally {
				try {
					if(fr != null)
						fr.close();
					if(br != null)
						br.close();
				} catch (Exception e2) {
				}
			}
		}
		return pid;
	}
	
	public void afterExec(ProcessResult result, int index) {
		super.afterExec(result, index);
		try {
			String command_name = Arrays.toString(result.getTemplate());
			try {
				// 命令类型
				command_name = CommandType.decide(command_name).type;
			} catch (Exception e) {
			}
			Map<String, String> command_status = status.get(command_name);
			if(null == command_status) {
				command_status = new HashMap<String, String>();
				// 时间间隔(秒)
				command_status.put("update", "0");
			}
			// 命令总数
			command_status.put("size", String.valueOf(result.getCommand().size()));
			// 命令进度
			command_status.put("progress", index < result.getCommand().size() ? String.valueOf(index) : "DONE");
			// 时间戳
			command_status.put("status_date", time_sdf.format(System.currentTimeMillis()));
			// 添加到列表
			status.put(command_name, command_status);
			
			// 更新所有命令更新间隔时长(秒)
			Iterator<Map.Entry<String, Map<String, String>>> iterator = status.entrySet().iterator();
			while(iterator.hasNext()) {
				Map.Entry<String, Map<String, String>> entry = iterator.next();
				long sec = 0l;
				try {
					java.util.Date update = time_sdf.parse(entry.getValue().get("status_date"));
					// 计算数据更新时间与现在时间秒差
					sec = (System.currentTimeMillis() - update.getTime()) / (1000l);
					// 删除超过指定时长未更新的数据
					if ((sec / 60l) >= statusTimeout) {
						iterator.remove();
					}
					entry.getValue().put("update", String.valueOf(sec));
				} catch (Exception e) {
					// 无法更新时从删除
					iterator.remove();
				}
			}
			// 在此处判断是否所有命令都已执行完成,progrss为DONE.调整上报睡眠间隔时长
			allDone = "DONE".equalsIgnoreCase(command_status.get("progress"));
			// 第一次数据更新,及时手动更新一次
			// 采集完成时立即更新一次报告
			if (index == 1
					|| (index + result.getCommand().getSkip() - 1) == result
							.getCommand().getSkip() || allDone) {
				// 上报运行状态信息
				try {
					// 使用线程,防止方法阻塞
					new Thread(new Runnable() {
						public void run() {
							reported("normal", 1000 * 90);
						}
					}, "system_report_update").start();
				} catch (Exception e) {
				}
			}
		} catch (Exception e) {
			logger.error("转换命令状态数据时出现异常!{}", e.toString());
		}
	}

	/** 将Map转换为特定格式字符串
	 * @param map
	 * @return 字符串
	 */
	private String mapToString(Map<String, Map<String, String>> map) {
		StringBuilder stringBuilder = new StringBuilder();
		try {
			Map<String, String> input_map = new HashMap<String, String>();
			// 转换Map值为String类型
			for (Map.Entry<String, Map<String, String>> entry : map.entrySet()) {
				StringBuffer value = new StringBuffer();
				value.append("size:").append(entry.getValue().get("size")).append(",");
				value.append("progress:").append(entry.getValue().get("progress")).append(",");
				value.append("update:").append(entry.getValue().get("update"));
				input_map.put(entry.getKey(), value.toString());
			}
			// 转换Map为String
			for (String key : input_map.keySet()) {
				if (stringBuilder.length() > 0) {
					stringBuilder.append("&");
				}
				String value = input_map.get(key);
				stringBuilder.append((key != null ? key : ""));
				stringBuilder.append("=");
				stringBuilder.append(value != null ? value : "");
			}
		} catch (Exception e) {
			logger.error("将状态Map转换为字符串形式时出现异常!{}", e.toString());
		}
		return stringBuilder.toString();
	}
	
	/**
	 * @param statusTimeout 命令状态超时时长
	 */
	public void setStatusTimeout(int statusTimeout) {
		this.statusTimeout = statusTimeout;
	}
}

class SystemReport implements Serializable {
	private static final long serialVersionUID = 1L;
	/*** 报文内容 */
	StringBuffer sb = null;
	
	String line = System.getProperty("line.separator", "\n");
	
	public SystemReport() {
		sb = new StringBuffer();
	}
	
	public void append(String key, String value) {
		sb.append(key).append(":").append(value.trim()).append(line);
	}
	
	/** 
	 * @return 报文内容
	 */
	public String getReport() {
		return sb.toString();
	}
}
