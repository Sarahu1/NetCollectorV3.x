/**
 * 
 */
package com.cattsoft.collect.io.file.icv;

import java.io.File;
import java.io.FileFilter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cattsoft.collect.io.file.utils.CommandType;
import com.cattsoft.collect.io.file.utils.FileUtils;

/** 数据完整性较验.
 * 针对文件合并服务检查数据完整性,
 * 检查通过时将发送通知服务.
 * @author 陈小鸿
 * @author chenxiaohong@mail.com
 * @since JDK1.6
 */
public class IntegrityCheckValue {
	private final Logger logger = LoggerFactory.getLogger(getClass().getName());
	/** 
	 * 通知服务延迟定时器
	 */
	private Timer task_timer = null;
	/**
	 * 服务执行延迟时长
	 */
	private long delay = 1000 * 60 * 10l;
	/**
	 * 任务是否已安排等待执行
	 */
	private boolean arranged = false;
	/**
	 * 服务
	 */
	private String service = null;
	/**
	 * 监测点列表.
	 */
	private String[] monitors = null;
	/**
	 * 各监测点所需命令列表.
	 */
	private String[] cmds = null;
	/**
	 * 完整性检查主目录.
	 * 目录组成 /日期/命令/..
	 */
	private String directory;
	/**
	 * 年月格式化
	 */
	private SimpleDateFormat sdf_month = new SimpleDateFormat("yyyyMM");
	
	/**
	 * @param home 检查目录
	 * @param notify 通知服务
	 * @param monitors 监测点列表
	 * @param cmds 命令列表
	 */
	public IntegrityCheckValue(String directory, String service, String[] monitors, String[] cmds) {
		this.directory = directory;
		this.service = service;
		this.monitors = monitors;
		this.cmds = cmds;
	}
	
	/** 执行完整性较验
	 * @param autoservice 是否自动安排事件通知服务.
	 * @return 是否通过
	 * @throws Exception 
	 */
	public synchronized boolean check(boolean autoservice) throws Exception {
		boolean pass = false;
		try {
			// 检查当月数据命令完整性
			if(checkCmd()) {
				// 检查各命令监测点数据完整性
				pass = checkMonitors();
			}
		} catch (Exception e) {
			logger.error("进行完整性较验时出现异常!检查失败:{}", e.getMessage());
			throw new Exception("进行数据完整性较验时出现异常!", e);
		}
		if(null != task_timer) {
			task_timer.cancel();
			task_timer.purge();
			if(arranged) {
				logger.info("既定任务已取消,数据验证通过后将重新开始计时");
				arranged = false;
			}
		}
		task_timer = new Timer("icv_notify_service");
		// 是否通过
		if(pass) {
			logger.info("数据完整性检查已完成:{}", (pass ? "通过" : "不通过"));
			// 自动通知服务
			if(autoservice) {
				try {
					task_timer.schedule(new TimerTask() {
						public void run() {
							try {
								arranged = false;
								// 通知服务
								notification();
								logger.info("通知服务已完成");
							} catch (Exception e) {
								logger.error("服务执行过程出现异常!通知失败!" + e.getMessage());
							} finally {
								// 完成后退出
								task_timer.cancel();
							}
						}
					}, delay);
					arranged = true;
				} catch (Exception e) {
					throw new IllegalStateException("安排计时通知服务失败!将无法进行通知!", e);
				}
				logger.info("系统已安排通知服务,将于[{}]分钟后执行.", (delay/60l/1000l));
			}
		} else {
			try {
				// 取消
				task_timer.cancel();
				task_timer.purge();
			} catch (Exception e) {
			}
		}
		return pass;
	}
	
	/** 
	 * 监测点完整性检查.
	 * 检查各监测点文件夹下是否存在数据文件.
	 * @param cmd 命令
	 * @return 是否通过
	 */
	// 临时变量,标记命令数据验证是否通过
	boolean tmp_flag = false;
	private boolean checkMonitors() {
		boolean pass = false;
		String yearMonth = sdf_month.format(System.currentTimeMillis());
		
		Map<String, String[]> missingMap = new HashMap<String, String[]>();
		
		for (String cmd : cmds) {
			// 已找到的监测点列表
			HashSet<String> foundMonitors = new HashSet<String>();
			// 缺少的监测点列表
			Set<String> missingMonitors = new HashSet<String>();
			
			CommandType cmdType = CommandType.decide(cmd);
			// 建立月份下命令文件目录
			File cmd_folder = new File(directory + File.separator + yearMonth + File.separator + cmd);
			// 判断是否存在该目录
			if(cmd_folder.exists()) {
				// 查找目录下所有监测点文件夹
				File[] cmd_monitors = FileUtils.listDirectorys(cmd_folder);
				// 监测点目录总数大于 0
				if(cmd_monitors.length == 0) {
					// 命令目录下无监测点目录
					// 添加所有缺失
					logger.error("命令[" + cmd + "]数据目录下尚无监测点数据");
					missingMonitors.addAll(Arrays.asList(monitors));
				}
				// 找到的监测点名称添加到列表
				for (File cmd_monitor : cmd_monitors) {
					final String monitor_name = cmd_monitor.getName();
					// 判断普通命令数据情况
					// 统计每个监测点目录下数据文件
					// 返回统计每个监测点目录下数据文件,统计数量
					tmp_flag = FileUtils.listFiles(cmd_monitor).length > 0;
					
					// 判断DIG+TRACE递归情况
					if(cmdType == CommandType.DIG_TRACE) {
						// 默认为通过
						tmp_flag = true;
						// 遍历所有子目录
						// 判断的出发点在于DIG+TRACE命令会出现多级数据
						// 所以需要判断该命令的每一级目录是否存在数据文件
						cmd_monitor.listFiles(new FileFilter() {
							// 递归级目录过滤
							public boolean accept(File pathrank) {
								// 各递归级数据文件过滤判断数量
								if (pathrank.isDirectory()
										&& FileUtils.listFiles(pathrank.getAbsolutePath()).length == 0) {
									logger.info("监测点["+monitor_name+"]DIG+TRACE命令递归级[" + pathrank.getName() + "]缺少数据文件");
									tmp_flag = false;
								}
								return pathrank.isDirectory();
							}
						});
					}
					// 通过后添加
					if(tmp_flag) {
						foundMonitors.add(cmd_monitor.getName());
					} else {
						missingMonitors.add(cmd_monitor.getName());
					}
				}
				// 比对需要的监测点名称是否存在
				for (String monitor : monitors) {
					if(!foundMonitors.contains(monitor)) {
						// 监测点文件不存在时添加到缺失列表
						missingMonitors.add(monitor);
					}
				}
				
				// 将命令缺失的监测点添加到Map进行打印提示
				if(missingMonitors.size() > 0) {
					missingMap.put(cmdType.type, missingMonitors.toArray(new String[]{}));
					pass = false;
				}
			} else {
				pass = false;
			}
		}
		// 数据缺失Map为空时表示验证通过
		pass = missingMap.size() == 0;
		if(!pass) {
			// 打印
			for (Map.Entry<String, String[]> missingEntry : missingMap.entrySet()) {
				logger.info("命令[" + missingEntry.getKey() + "]缺少以下监测点数据:"
						+ Arrays.toString(missingEntry.getValue()));
			}
		}
		logger.info("月份["+yearMonth+"]各监测点数据完整性检查完成:" + (pass ? "通过" : "不完整"));
		return pass;
	}
	
	/** 
	 * 命令完整性检查.
	 * @param monitor 监测点
	 * @return 是否通过
	 */
	private boolean checkCmd() {
		boolean pass = false;
		// 年月
		String yearMonth = sdf_month.format(System.currentTimeMillis());
		List<String> missingCmd = new ArrayList<String>();
		List<String> foundCmds = new ArrayList<String>();
		// 过滤目录
		File monthFolder = new File(directory + File.separator + yearMonth);
		
		if(!monthFolder.exists()) {
			logger.error("未找到月份[" + monthFolder.getName() + "]数据目录");
		} else {
			File[] cmd_folders = FileUtils.listDirectorys(monthFolder);
			// 添加到列表
			for (File cmd_folder : cmd_folders) {
				foundCmds.add(cmd_folder.getName());
			}
		}
		// 判断各命令完整性
		for (String cmd : cmds) {
			if(!foundCmds.contains(cmd)) {
				missingCmd.add(cmd);
				pass = false;
			}
		}
		pass = missingCmd.size() == 0;
		
		if(!pass) {
			logger.info("月份[{}]数据缺少以下命令数据:{}", yearMonth, missingCmd.toString());
		}
		logger.info("月份[{}]各命令数据完整性检查完成:{}", yearMonth, (pass ? "通过" : "不完整"));
		return pass;
	}
	
	/**
	 * 发送服务通知.
	 * <pre>
	 * HTTP:
	 * WS:
	 * Mapping:
	 * </pre>
	 */
	public synchronized void notification(Object...args) {
		// 类路径与方法之间使用符号 # 分隔,固定取2个长度
		String[] services = service.split("#", 2);
		// 类路径
		int pi = (pi = services[0].indexOf("(")) > -1 ? pi : services[0].length();
		String classpath = services[0].substring(0, pi).trim();
		try {
			// 装载类
			Class<?> cls = ClassLoader.getSystemClassLoader().loadClass(classpath);
			// 类实例化参数
			List<Class<?>> classParams = new LinkedList<Class<?>>();
			// 获取构造函数
			Constructor<?> construct = cls.getConstructor(classParams.toArray(new Class<?>[]{}));
			// 新建类实例
			Object instance = construct.newInstance();
			// 方法名称
			String methodName = services[1].substring(0, services[1].indexOf("(")).trim();
			// 方法参数类型获取
			List<Class<?>> methodParams = new LinkedList<Class<?>>();
			String[] paramsClass = services[1].substring(services[1].indexOf("(") + 1, services[1].indexOf(")")).split(",");
			for (String pc : paramsClass) {
				// 实例化各参数类型
				methodParams.add(Class.forName(pc.trim()));
			}
			// 获取方法
			Method calc = cls.getMethod(methodName, methodParams.toArray(new Class<?>[]{}));
			
			/************************/
			/** 循环调用各命令目录 **/
			/************************/
			// 查找各命令目录,DIG+TRACE需要查找各递归级数据目录
			// 执行通知方法
			
			String yearMonth = sdf_month.format(System.currentTimeMillis());
			for (String cmd : cmds) {
				// 确定命令类型
				CommandType cmdType = CommandType.decide(cmd);
				
				File cmdFolder = new File(directory + File.separator + yearMonth + File.separator + cmd);
				// 命令目录是否变动
				if(!cmdFolder.exists()) {
					logger.error("命令[{}]数据目录不存在或已移除,无法发送通知!", cmdType.type);
					continue;
				}
				
				// 查找监测点目录
				File[] cmd_monitors = FileUtils.listDirectorys(cmdFolder);
				
				// 监测点数据长度是否变动
				if(cmd_monitors.length == 0) {
					logger.error("命令[{}]数据目录下监测点列表为空,无法发送通知!", cmdType.type);
					continue;
				}
				
				// 遍历监测点并执行通知服务
				for (File monitor : cmd_monitors) {
					if (monitor.exists()) {
						// 是否为DIG+TRACE命令
						if(cmdType == CommandType.DIG_TRACE) {
							// DIG+TRACE 需调用每一级目录
							// 递归级目录
							File[] sub_ranks = FileUtils.listDirectorys(monitor.getPath());
							// 循环递归级
							for (File rank : sub_ranks) {
								if(rank.exists()) {
									logger.info("执行命令["+ cmdType.type +"]数据通知服务,递归级:"+ rank.getName() +",路径:" + monitor.getAbsolutePath());
									// 发送DIG+TRACE命令每一级目录
									calc.invoke(instance, cmdType.type, rank.getAbsolutePath());
								} else {
									logger.error("命令[" + cmdType.type + "],数据目录不存在,无法发送通知!");
								}
							}
						} else {
							logger.info("执行命令["+ cmdType.type +"]数据通知服务,路径:" + monitor.getAbsolutePath());
							// 发送各监测点数据通知服务
							calc.invoke(instance, cmdType.type, monitor.getAbsolutePath());
						}
					} else {
						logger.error("命令[" + cmdType.type + "]监测点[" + monitor.getName() + "]数据目录不存在!无法发送通知!");
					}
				}
			}
		} catch(ClassNotFoundException e) {
			logger.error("未找到服务类!无法发送通知服务!" + classpath);
			throw new IllegalStateException("未找到服务类!" + classpath, e);
		} catch (Exception e) {
			logger.error("通知服务出现异常!请检查服务!" + e.getMessage());
			throw new IllegalStateException("通知服务出现异常!", e);
		}
	}
	
	/**
	 * @param delay the delay to set
	 */
	public void setDelay(long delay) {
		this.delay = delay;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		IntegrityCheckValue icv = new IntegrityCheckValue(
				"E:\\CATTSOFT\\Ftp\\data\\collectdata3\\sync\\output\\",
				"com.cattsoft.cloud.Cloud#calc(java.lang.String, java.lang.String)",
				new String[] {"3"}, new String[] {"dig_trace", "ping"});
		try {
//			icv.check(true);
			icv.notification();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
