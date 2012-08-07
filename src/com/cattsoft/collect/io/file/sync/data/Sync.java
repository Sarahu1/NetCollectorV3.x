
package com.cattsoft.collect.io.file.sync.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.listeners.JobListenerSupport;

import com.cattsoft.collect.io.file.sync.data.job.ExportJob;
import com.cattsoft.collect.io.net.ftp.FTP;
import com.cattsoft.collect.io.utils.ConfigUtils;

/**
 * @author ChenXiaohong
 * @usage java -Dsync.config=export.properties -Djava.ext.dirs=lib -classpath .:.
 *             com.cattsoft.collect.io.file.sync.data.Sync
 * @since JDK1.6
 */
public class Sync {
	private final static Logger logger = Logger.getLogger("sync_data");
	/*** 程序退出监听.*/
	private Thread shutdownHook;
	/*** FTP 客户端 */
	private FTP ftp;
	/*** 任务调度器 */
	private Scheduler scheduler;
	
	private static final SimpleDateFormat date_sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	/**
	 * 默认构造.
	 * 配置将由系统属性决定.
	 * @throws ConfigurationException 
	 */
	public Sync() throws ConfigurationException {
		this(new PropertiesConfiguration(System.getProperty("sync.config", "export.properties")));
	}
	
	/** 通过配置方式加载数据导出任务.
	 *  配置将转换为Map类型
	 * @param config 任务配置
	 */
	public Sync(Configuration config) {
		this(toTaskMap(config), getFtpServers(config));
	}
	
	/** 通过Map配置方式加载数据导出任务.
	 * Map 键值为任务名称, 值为Map类型,存储配置
	 * @param task 任务配置列表
	 * @param servers FTP 上传服务器列表(数组,包含服务器地址, 用户名, 密码, 目录)
	 */
	public Sync(Map<String, Map<String, String>> task, List<String[]> servers) {
		// 程序退出处理
		shutdownHook = new Thread(new Runnable() {
			public void run() {
				try {
					// 停止任务调度器
					scheduler.shutdown(true);
				} catch (Exception e) {
					logger.severe("任务调度器关闭出现异常!" + e.getMessage());
				}
				Runtime.getRuntime().removeShutdownHook(shutdownHook);
			}
		}, "sync_hook");
		Runtime.getRuntime().addShutdownHook(shutdownHook);
		// 任务调度配置
		try {
			scheduler= StdSchedulerFactory.getDefaultScheduler() ;
			
			// 任务监听
			scheduler.addGlobalJobListener(new JobListenerSupport() {
				@Override
				public String getName() {
					return "job_listener";
				}
				
				@Override
				public void jobWasExecuted(JobExecutionContext context,
						JobExecutionException jobException) {
					// 任务执行完成
					// 当前任务触发器是否已失效, 没有下一次触发时间
					if(null == context.getTrigger().getNextFireTime()) {
						try {
							logger.info("触发器 " + context.getTrigger().getGroup() + " - " + context.getTrigger().getName() + " 已失效, 移除");
							scheduler.unscheduleJob(context.getTrigger().getName(), context.getTrigger().getGroup());
						} catch (SchedulerException e) {
							logger.severe("移除触发器(group:"+ context.getTrigger().getGroup() + ",name:" + context.getTrigger().getName() +")时出现异常!" + e.getMessage());
						}
					}
					
					// 当前任务错误重试次数
					int try_freq = 1;
					try {
						try_freq = context.getJobDetail().getJobDataMap().getInt("job_retries_count");
					} catch (Exception e) {
					}
					// 是否出现异常
					if((null != jobException) && (null == context.getResult())) {
						logger.severe("任务 "+context.getJobDetail().getName()+" 执行失败!正在添加临时任务以进行重试..");
						// 增加任务临时触发器, 3 分钟后执行
						Date trigger_time = new Date(System.currentTimeMillis() + (1000 * 10));
						Trigger trigger = new SimpleTrigger(context.getJobDetail().getName() + "_temp_trigger", null, trigger_time);
						trigger.setJobName(context.getJobDetail().getName());
						trigger.setJobGroup(context.getJobDetail().getGroup());
						try {
							// 添加临时任务, 判断执行时间是否超出任务自身触发时间, 否则添加
							if(trigger_time.before(scheduler.getTrigger(context.getJobDetail().getName() + "_trigger", Scheduler.DEFAULT_GROUP).getNextFireTime())) {
								scheduler.scheduleJob(trigger);
								logger.info("临时任务添加成功, 将在以下时间重试:" + date_sdf.format(trigger_time));
							} else {
								logger.info("临时任务执行时间超出任务自身下次触发时间, 抛弃");
							}
						} catch (Exception e) {
							logger.severe("临时任务添加失败!" + e.getMessage());
						}
						try_freq ++;
					} else {
						// 任务执行成功
						logger.info("任务 "+ context.getJobDetail().getName() +" 执行成功, 数据文件:" + context.getResult());
						// TODO 添加到上传任务列表
					}
					// 更新重试次数
					context.getJobDetail().getJobDataMap().put("job_retries_count", try_freq);
					super.jobWasExecuted(context, jobException);
				}
			});
			
			// 数据库配置
			Map<String, String> database_map = new HashMap<String, String>();
			database_map.put("driver", System.getProperty("export.db.driver"));
			database_map.put("url", System.getProperty("export.db.url"));
			database_map.put("username", System.getProperty("export.db.username"));
			database_map.put("password", System.getProperty("export.db.password"));
			
			// 根据配置文件, 创建多个数据导出任务
			int jobs = 0; // 任务数统计
			for (Map.Entry<String, Map<String, String>> entry : task.entrySet()) {
				String jobname = entry.getKey().replace(".", "_");
				// 任务实例
				JobDetail job = new JobDetail(jobname + "_job", jobname + "_group", ExportJob.class);
				// durability, 指明任务就算没有绑定Trigger仍保留在Quartz的JobStore中
				job.setDurability(true);
				
				// 实例参数传入
				job.getJobDataMap().putAll(entry.getValue());
				job.getJobDataMap().putAll(database_map);
				// 以键值为export.ftp.servers的形式将FTP列表添加到任务实例参数中
				job.getJobDataMap().put("export.ftp.servers", servers);
				// 加入一个任务到Quartz中, 等待后面再绑定Trigger
				// 此接口中的JobDetail的durability必须为true  
				scheduler.addJob(job, false);
				
				// 任务调度触发
				Trigger trigger = new CronTrigger(job.getName() + "_trigger", null, entry.getValue().get("quartz.expression"));
				trigger.setJobName(job.getName());
				trigger.setJobGroup(job.getGroup());
				// 从现在开始
				trigger.setStartTime(new Date());
				// 添加任务
				scheduler.scheduleJob(trigger);
				jobs++;
			}
			logger.info("当前共添加 "+ jobs +" 同步任务");
			// 启动任务
			scheduler.start();
		} catch (Exception e) {
			logger.severe("数据导出任务启动失败!" + e.getMessage());
		}
	}
	
	/** 获取配置文件服务器定义
	 * @param config
	 * @return
	 */
	public static List<String[]> getFtpServers(Configuration config) {
		List<String[]> servers = new ArrayList<String[]>();
		
		// 读取文件设置列表
		File file = new File(config.getString("export.ftp.server.list"));
		if(file.exists()) {
			Reader reader = null;
			BufferedReader buffer = null;
			try {
				reader = new FileReader(file);
				buffer = new BufferedReader(reader);
				// 读取服务器列表文件
				String line = null;
				while((line = buffer.readLine()) != null) {
					if(line.startsWith("#"))
						continue;
					// 添加到列表
					// 服务器信息顺序为:地址, 端口, 用户名, 密码, 目录
					servers.add(new String[] { line.trim(),
							config.getString("export.ftp.port").trim(),
							config.getString("export.ftp.username").trim(),
							config.getString("export.ftp.password").trim(),
							config.getString("export.ftp.path").trim() });
				}
			} catch (IOException e) {
				logger.severe("无法读取上传服务器列表!程序可能会出现异常!" + e.getMessage());
			} finally {
				try {
					if(null != buffer)
						buffer.close();
				} catch (Exception e2) {
				}
				try {
					if(null != reader)
						reader.close();
				} catch (Exception e2) {
				}
			}
		}
		
		// 读取特殊定义列表
		List<String[]> special_servers = new LinkedList<String[]>();
		// 获取为数组形式, 每4个值为一个配置, 循环进行分割
		String[] ftp_sersers = config.getStringArray("export.ftp.server");
		if(ftp_sersers.length % 4 != 0) {
			logger.severe("数据更新服务器定义错误, 请检查配置是否正确!");
		} else {
			// 循环, 截取值后添加到列表
			List<String> server_list = Arrays.asList(ftp_sersers);
			for (int i = 1; i <= ftp_sersers.length; i++) {
				if(i % 4 == 0)
					special_servers.add(server_list.subList(i - 4, i).toArray(new String[4]));
			}
			servers.addAll(special_servers);
			logger.info("读取自定义FTP上传服务器配置共 "+ special_servers.size() +" 条");
		}
		return servers;
	}
	
	/** 将任务配置文件转换为Map形式.
	 * @param config 配置
	 * @return
	 */
	public static Map<String, Map<String, String>> toTaskMap(Configuration config) {
		Map<String, Map<String, String>> config_map = new HashMap<String, Map<String,String>>();
		// 任务名称数组
		String[] tasknames = config.getStringArray("export.job.name");
		Map<String, String> value = null;
		for (String name : tasknames) {
			value = new HashMap<String, String>();
			// 获取配置
			value.put("pathname", config.getString(name + ".pathname"));
			// 因配置加载原因, 会将逗号视为分隔符
			String sql = config.getProperty(name + ".sql").toString();
			if(sql.startsWith("[") && sql.endsWith("]"))
				sql = sql.substring(1, sql.length() - 1);
			value.put("sql", sql);
			value.put("transfer", config.getString(name + ".transfer"));
			value.put("records", config.getString(name + ".records"));
			value.put("deploy", config.getString(name + ".deploy.servers"));
			value.put("quartz.expression", config.getString(name + ".quartz.expression"));
			// 添加到Map
			config_map.put(name, value);
		}
		return config_map;
	}
	
	public static void main(String[] args) {
		ConfigUtils.loadJvmProperty(args, "sync.config");
		// 查找参数配置
		String config = null;
		try {
			for (int i = 0; i < args.length; i++) {
				if ("-config".equals(args[i])) {
					config = args[i + 1];
				}
			}
			// 查找JVM属性配置
			config = System.getProperty("sync.config", config);
		} catch (Exception e) {
			System.out.println("Usage:-config xxx.properties");
			System.exit(-1);
		}
		try {
			// 加载配置
			Configuration configuration = new PropertiesConfiguration(config);
			// 建立同步线程
			new Sync(configuration);
		} catch (Exception e) {
			System.err.println("任务配置加载失败!" + e.getMessage());
			e.printStackTrace();
		}
	}
}
