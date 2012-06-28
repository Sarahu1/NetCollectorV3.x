/**
 * 
 */
package com.cattsoft.collect.io.file.merge;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cattsoft.collect.io.file.archive.ZipFileUtils;
import com.cattsoft.collect.io.file.icv.IntegrityCheckValue;
import com.cattsoft.collect.io.file.utils.CommandType;
import com.cattsoft.collect.io.file.utils.ConfigUtils;
import com.cattsoft.collect.io.file.utils.FileUtils;
import com.cattsoft.collect.io.file.utils.StringUtils;

/** 数据文件合并任务线程.
 * 线程定时扫描指定文件夹,合并数据文件
 * @author ChenXiaohong
 * @usage java -Dmerge.config=conf/merge.properties -Djava.ext.dirs=lib 
 * -classpath .:. com.cattsoft.collect.io.file.merge.FileMergeTask
 * @since JDK1.6
 */
public class FileMergeTask {
	private Logger logger = LoggerFactory.getLogger(getClass());
	/*** 监视目录.*/
	private File listenFolder = null;
	/*** 合并文件输出目录 */
	private File outputFolder = null;
	/*** 备份目录 */
	private File backupFolder = null;
	/*** 目录扫描间隔时长(秒)*/
	private long scanSecond = 10l;
	/*** 程序退出监听.*/
	private Thread shutdownHook;
	/*** 运行状态.*/
	private boolean running = true;
	/*** 当前监视目录文件列表.*/
	private Queue<File> scanList = new LinkedBlockingQueue<File>();
	/*** 数据完整性检查服务 */
	private IntegrityCheckValue icv = null;
	
	/**
	 * 默认构造.
	 * 参数由JVM属性获取
	 * <code>
	 * <pre>
	 * System.getProperty(name)
	 * 
	 * 监视目录(listen) = merge.listen
	 * 合并输出目录(output) = merge.output
	 * 扫描间隔(scan) = merge.scan - 默认10秒
	 * 
	 * java -jar -Dmerge.listen=E:\CATTSOFT\Ftp\data -Dmerge.output=E:\CATTSOFT\Ftp\data\output -Dmerge.scan=10000 collector_merge_3.0.0.jar
	 * </pre>
	 * </code>
	 */
	public FileMergeTask() {
		this(System.getProperty("merge.listen"), System
				.getProperty("merge.output"), Long.parseLong(System
				.getProperty("merge.scan", "10000")));
	}
	
	/**
	 * 使用监视线程扫描监控目录,发现文件后进行合并.
	 * 实例化后将自动启动扫描线程
	 * @param listenfolder 监视目录
	 * @param outputFolder 数据输出目录
	 * @param backupFolder 备份输出目录(为<code>null</code>不进行备份)
	 * @param scanMillisecond 目录扫描间隔时长(毫秒)
	 */
	public FileMergeTask(String listenfolder, String outputFolder, String backupFolder, long scanMillisecond) {
		// 参数检查
		if(null == listenfolder || listenfolder.isEmpty() || null == outputFolder || outputFolder.isEmpty()) {
			throw new IllegalArgumentException("需要配置正确的服务参数");
		}
		logger.info("正在初始化数据合并服务..");
		// 完整性服务初始化
		// 完整性检查目录
		String icv_dir = System.getProperty("icv.directory", outputFolder);
		logger.info("数据完整性较验主目录:{}", icv_dir);
		// 通知服务
		String icv_service = System.getProperty("icv.service");
		// 监测点列表
		String[] icv_monitors = System.getProperty("icv.monitors", "").split(",");
		logger.info("完整性较验监测点列表:{}", Arrays.toString(icv_monitors));
		// 命令列表
		String[] icv_cmds = System.getProperty("icv.cmds", "").split(",");
		logger.info("完整性较验监测点命令列表:{}", Arrays.toString(icv_cmds));
		if(null == icv_service || "".equals(icv_service)) {
			logger.error("完整性检查通知服务未设置,将无法发送通知服务!");
		} else {
			logger.info("完整性较验通知服务:{}", icv_service);
			logger.info("较验完成是否自动通知服务:" + System.getProperty("icv.autoservice", "false"));
		}
		icv = new IntegrityCheckValue(icv_dir, icv_service, icv_monitors, icv_cmds);
		// 通知服务延迟时长,默认为10分钟
		long icv_delay = Long.valueOf(System.getProperty("icv.service.delay", String.valueOf(1000 * 60 * 10l)));
		logger.info("完整性较验通知服务延时时长:{}", icv_delay);
		icv.setDelay(icv_delay);
		
		// 监视目录
		this.listenFolder = new File(listenfolder);
		if(!this.listenFolder.isDirectory()) {
			logger.error("Data Monitoring directory({}) does not exist", listenFolder.getAbsolutePath());
			throw new IllegalArgumentException("[" + listenFolder
					+ "] must be a directory");
		}
		logger.info("数据文件扫描目录:{}", this.listenFolder.getAbsolutePath());
		this.outputFolder = new File(outputFolder);
		// 不存在目录
		if(!this.outputFolder.exists()) {
			// 创建
			this.outputFolder.mkdirs();
		}
		if(!this.outputFolder.isDirectory()) {
			throw new IllegalArgumentException("[" + outputFolder
					+ "] must be a directory");
		}
		logger.info("数据合并输出主目录:{}", this.outputFolder.getAbsolutePath());
		// 备份目录
		if(null != backupFolder && !backupFolder.isEmpty()) {
			this.backupFolder = new File(backupFolder);
			this.backupFolder.mkdirs();
			logger.info("数据文件备份目录:{}", this.backupFolder.getAbsolutePath());
		}
		this.scanSecond = scanMillisecond;
		// 启动扫描线程
		new Thread(new FolderScanRunnable(), "scan_thread").start();
		// 程序退出处理
		shutdownHook = new Thread(new Runnable() {
			public void run() {
				running = false;
				logger.info("数据文件合并服务已挂掉");
				Runtime.getRuntime().removeShutdownHook(shutdownHook);
			}
		});
		Runtime.getRuntime().addShutdownHook(shutdownHook);
		
		logger.info("文件合并服务已启动");
	}
	
	/**
	 * 使用监视线程扫描监控目录,发现文件后进行合并.
	 * 实例化后将自动启动扫描线程
	 * @param listenfolder 监视目录
	 * @param outputFolder 数据输出目录
	 * @param scanMillisecond 目录扫描间隔时长(毫秒)
	 */
	public FileMergeTask(String listenfolder, String outputFolder, long scanMillisecond) {
		this(listenfolder, outputFolder, null, scanMillisecond);
	}

	/**
	 * 合并
	 */
	private void merge() {
		//添加监控目录下的所有文件
		Collections.addAll(scanList, FileUtils.listFiles(listenFolder));
		// 处理合并目录各数据文件
		// 循环,直到处理完成
		while(!scanList.isEmpty()) {
			// 挑选文件
			File[] fileblocks = FileBlockFilter.select(scanList);
			// 临时目录 监视目录下创建文件夹 /tmp$Random Int
			File tmpfolder = new File(String.format(listenFolder.getAbsolutePath() + "%stmp$%d", File.separator, java.lang.Math.abs(new Random().nextInt())));
			try {
				List<String> unzipFiles = new LinkedList<String>();
				if(fileblocks.length > 0) {
					// 设置输出目录
					String outputFolder = extractOutput(fileblocks[0].getAbsolutePath());
					// 设置输出文件名称
					String outputPath = this.outputFolder.getPath()
							+ File.separator + outputFolder + File.separator
							+ extracctOutFile(fileblocks[0].getName());
					// 解压到临时目录
					// 检查是否为压缩文件,压缩文件进行解压后合并
					try {
						for (File block : fileblocks) {
							String suffix = block.getName().substring(block.getName().lastIndexOf(".") + 1);
							if("zip".equalsIgnoreCase(suffix)) {
								// 文件解压到临时目录
								String[] unzips = new String[]{};
								try {
									// 解压到临时目录
									unzips = ZipFileUtils.unZip(block.getPath(), tmpfolder.getPath());
								} catch (Exception e) {
									logger.error("数据文件({})无法进行解压缩!{}", block.getAbsolutePath() , e.getMessage());
									throw e;
								}
								Collections.addAll(unzipFiles, unzips);
								// 删除压缩文件
								// block.delete();
							} else {
								unzipFiles.add(block.getPath());
							}
							logger.info("the file("+block.getAbsolutePath()+") has ben processed.");
						}
						//: 压缩检查完成
					} catch (Exception e) {
						logger.error("数据文件解压失败!文件可能正在使用或已移除!");
						// 文件传输未完成或解压失败,取消当前任务
						// 等待下次任务进行处理
						continue;
					}
					//: 解压完成
					// 合并列表文件
					FileMerge.merge(outputPath, unzipFiles.toArray(new String[unzipFiles.size()]));
					for (File block : fileblocks) {
						if(null == backupFolder) {
							// 合并完成后删除文件块
							block.delete();
						} else {
							// 备份
							try {
								// 备份路径
								File backFile = new File(
										backupFolder.getAbsolutePath()
												+ File.separator + outputFolder
												+ File.separator
												+ block.getName());
								if(null != backFile.getParentFile()) {
									backFile.getParentFile().mkdirs();
								}
								if(backFile.exists()) {
									// 删除
									backFile.delete();
								}
								// 重命名
								block.renameTo(backFile);
							} catch (Exception e) {
								block.delete();
								logger.error("无法对文件("+block.getAbsolutePath()+")进行备份!");
							}
						}
					}
				}
			} catch(IOException e) {
				logger.error("数据文件无法合并,文件可能已经损坏,请检查文件!{}", e.getMessage());
			} catch (Exception e) {
				// 合并文件时出现异常!文件无法合并
				logger.error("合并文件时出现异常!文件无法合并!{}", e.getMessage());
				continue;
			} finally {
				// 临时目录删除
				tmpfolder.delete();
			}
		}
		try {
			// 数据完整性较验与通知
			boolean autoservice = Boolean.parseBoolean(System.getProperty("icv.autoservice", "false"));
			// 检查
			try {
				boolean checked = icv.check(autoservice);
				if(!autoservice && checked) {
					logger.info("数据完整性较验完成,未进行其它操作");
					// 检查通过,但未设置通知服务,其它操作
				}
			} catch (Exception e) {
				logger.error("数据完整性较验已完成!通知服务出现异常!{}", e.getMessage());
			}
		} catch (Exception e) {
			logger.error("较验数据完整性时出现异常!无法进行通知服务!{}", e.getMessage());
		}
	}

	/** 拼写输出文件名称
	 * @param blockname
	 * @return
	 */
	private String extracctOutFile(String blockname) {
		// 通过枚举类型获取命令类型
		return String.format("all_%s.data", CommandType.decide(FileBlockFilter.afterLastSlash(blockname)).type);
	}

	/** 抽取合并输出目录.
	 * 日期\命令\监测点
	 * @param blockname
	 * @throws Exception 
	 */
	private String extractOutput(String blockname) throws Exception {
		// 是否为压缩文件
		boolean iszip = blockname.matches(".*?\\.zip$");
		// 输出目录
		StringBuffer output = new StringBuffer();
		// 日期 //命令 //监测点
		String datemonth,command,monitor = "";
		// 命令类型
		CommandType commandType = CommandType.UNKNOW;
		if(iszip) {
			// 输出目录由注释信息合并
			// 文件注释信息
			String comment = ZipFileUtils.extractZipComment(blockname);
			// 日期
			String datetime = StringUtils.getCommentValue(comment, "DateTime:");
			try {
				java.util.Date date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(datetime);
				datemonth = new SimpleDateFormat("yyyyMM").format(date);
			} catch (ParseException e) {
				datemonth = new SimpleDateFormat("yyyyMM").format(System.currentTimeMillis());
			}
			// 命令行
			command = StringUtils.getCommentValue(comment, "CommandLine:");
			if("".equals(command)) {
				command = "unknow";
			} else {
				// 设置命令行
			}
			// 判断命令类型
			commandType = CommandType.decide(command);
			// 可能出现信息不完整,无法判断命令类型的问题
			if(CommandType.UNKNOW == commandType) {
				// 命令文件名称判定类型
				commandType = CommandType.decide(blockname);
			}
			// 监测点
			monitor = StringUtils.getCommentValue(comment, "MonitorNumber:");
			if("".equals(monitor)) {
				monitor = "unknow";
			}
		} else {
			// 输出目录由文件名称信息决定
			// 日期
			if(blockname.matches("^\\d{6,}")) {
				datemonth = blockname.substring(0,6);
			} else {
				// 系统获取
				datemonth = new SimpleDateFormat("yyyyMM").format(System.currentTimeMillis());
			}
			// 判断命令类型
			commandType = CommandType.decide(blockname);
			// 采集点
			java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("_m(\\d+)_").matcher(blockname);
			if(matcher.find()) {
				monitor = matcher.group(1);
			} else {
				monitor = "unknow";
			}
		}
		// 命令类型
		command = commandType.type;
		// 日期年月
		output.append(datemonth);
		// 命令类型
		output.append(File.separator).append(command);
		// 监测点
		output.append(File.separator).append(monitor);
		// 处理DIG+TRACE命令
		// DIG+TRACE 需要处理递归采集分层问题
		if(commandType == CommandType.DIG_TRACE) {
			// 判断层次
			int floor = 0;
			try {
				Matcher matcher = Pattern.compile("^.*result_(\\d+)(_p3\\.part\\d+)?.zip$").matcher(blockname);
				if(matcher.find()) {
					floor = Integer.parseInt(matcher.group(1));
				}
			} catch (Exception e) {
			}
			output.append(File.separator).append(floor);
		}
		return output.toString();
	}
	
	/**
	 * @param icv the icv to set
	 */
	public void setIcv(IntegrityCheckValue icv) {
		this.icv = icv;
	}
	
	/** 文件夹定时扫描线程.
	 * @author ChenXiaohong
	 */
	class FolderScanRunnable implements Runnable {
		public void run() {
			logger.info("数据文件扫描线程已启动");
			while(running) {
				// 检查
				try {
					if (FileUtils.listFiles(listenFolder).length > 0) {
						// 执行合并
						merge();
					}
				} catch (Exception e) {
				}
				// 睡眠
				try {
					Thread.sleep(scanSecond);
				} catch (Exception e) {
				}
			}
			logger.info("数据文件扫描线程已停止");
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// 加载系统属性
		ConfigUtils.loadJvmProperty(args, "merge.config");
		// 创建文件合并线程
		new FileMergeTask();
	}
}
