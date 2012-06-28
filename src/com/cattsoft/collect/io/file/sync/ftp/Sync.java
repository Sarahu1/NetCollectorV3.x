/**
 * 
 */
package com.cattsoft.collect.io.file.sync.ftp;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cattsoft.collect.io.file.archive.ZipFileUtils;
import com.cattsoft.collect.io.file.ftp.CommonFtpClient;
import com.cattsoft.collect.io.file.ftp.FtpUploadTask;
import com.cattsoft.collect.io.file.utils.ConfigUtils;

/**
 * 文件网络同步. 将本地文件同步到FTP服务器
 * 
 * @author ChenXiaohong
 * @usage java -Dsync.config=sync.properties -Djava.ext.dirs=lib -classpath .:.
 *             com.cattsoft.collect.io.file.sync.ftp.Sync
 * @since JDK1.6
 */
public class Sync {
	private Logger logger = LoggerFactory.getLogger("sync_ftp");
	/*** 服务器地址 */
	private String host;
	/*** 服务端口 */
	private int port;
	/*** 本地监视目录 */
	private String local;
	/*** 本地备份路径 */
	private String backup;
	/*** 文件选择器 */
	private Selector selector;
	/*** 上传文件选择过滤 */
	private FileFilter filter;
	/*** FTP 上传任务 */
	private FtpUploadTask task;
	/*** 同步间隔时长*/
	private long period = 10000;
	/*** 程序退出监听.*/
	private Thread shutdownHook;
	/*** 扫描控制 */
	private boolean scanning = true;
	/*** FTP 客户端 */
	private CommonFtpClient ftp;
	
	/**
	 * 默认构造.
	 * 配置将由系统属性决定.
	 * <code>
	 * <pre>
	 * System.getProperty(name)
	 * 
	 * Ftp 服务器地址(host) = sync.host
	 * Ftp 上传目录(path) = sync.path
	 * Ftp 用户名称(user) = sync.user
	 * Ftp 用户密码(pwd) = sync.pwd
	 * Ftp 服务端口(port) = sync.port
	 * 本地监视目录(local) = sync.local
	 * 本地数据备份(backup) = sync.backup
	 * 
	 * </pre>
	 * </code>
	 */
	public Sync() {
		// 由JVM属性获取参数配置
		this(System.getProperty("sync.host"), System.getProperty("sync.user"),
				System.getProperty("sync.pwd"),
				System.getProperty("sync.path"), System
						.getProperty("sync.local"), System
						.getProperty("sync.backup"), Integer.parseInt(System
						.getProperty("sync.port", "21")));
	}
	
	/**
	 * @param host FTP 主机地址
	 * @param user FTP 用户名
	 * @param pwd FTP 用户密码
	 * @param path FTP 服务器目录
	 * @param local 本地监视目录(为<code>null</code>时不进行备份)
	 */
	public Sync(String host, String user, String pwd, String path, String local) {
		this(host, user, pwd, path, local, null, 0);
	}
	
	/**
	 * @param host FTP主机地址
	 * @param user FTP 用户名
	 * @param pwd FTP 用户密码
	 * @param path FTP服务器目录
	 * @param local 本地监视目录
	 * @param backup 本地备份目录(为<code>null</code>时不进行备份)
	 */
	public Sync(String host, String user, String pwd, String path, String local, String backup, int port) {
		// 参数检查
		if (null == host || host.isEmpty() || null == user || user.isEmpty() || null == path || path.isEmpty()
				|| null == local) {
			throw new IllegalArgumentException("需要配置正确的服务参数");
		}
		this.host = host;
		this.local = local;
		this.backup = backup;
		// 配置日志打印
		logger.info("FTP 服务器地址:" + host);
		logger.info("FTP 上传目录:" + path);
		logger.info("FTP 用户名称:" + user);
		// 密码进行掩盖
		logger.info("FTP 用户密码:" + pwd.replaceAll(".", "*"));
		logger.info("本地监视目录:" + local);
		logger.info("本地备份目录:" + (null == backup ? "无" : backup));
		
		// FTP 客户端
		ftp = new CommonFtpClient(this.host, this.port);
		ftp.setUsername(user);
		ftp.setPassword(pwd);
		ftp.setWorkdir(path);
		// 创建FTP任务类
		task = new FtpUploadTask(ftp);
		// 任务未完成时保存路径
		task.setTaskpath("sync_task.ftp");
		// 文件上传完成后备份路径
		task.setBackdir(backup);
		
		// 文件选择器
		selector = new Selector(this.local);
		// 文件过滤
		filter = new FileFilter() {
			public boolean accept(File file) {
				return file.getName().matches(".*\\.zip");
			}
		};
		// 启用线程扫描上传
		new Thread(new SyncRunnalbe(period), "sync_ftp").start();
		// 程序退出处理
		shutdownHook = new Thread(new Runnable() {
			public void run() {
				scanning = false;
				logger.info("FTP文件同步服务被挂掉");
				Runtime.getRuntime().removeShutdownHook(shutdownHook);
			}
		}, "sync_hook");
		Runtime.getRuntime().addShutdownHook(shutdownHook);
		logger.info("文件同步服务启动已完成");
	}
	
	/**
	 * 同步入口.
	 * 选择并上传文件.
	 */
	private void sync() {
		// 上传任务完成时添加任务
		if(task.isComplete()) {
			// 选取文件
			String[] files = selector.select(filter);
			// 添加到上传任务
			for (String file : files) {
				try {
					// 测试ZIP文件
					if(file.endsWith(".zip")) {
						ZipFileUtils.test(file);
					}
					// 添加
					task.push(file, (null != backup));
				} catch (FileNotFoundException e) {
					// 文件不存在
					logger.error("需要进行同步的文件已同步或不存在!" + e.getMessage());
				} catch (Exception e) {
					// 文件错误
					logger.error("压缩文件测试错误!" + e.getMessage());
				}
			}
		}
	}
	
	public void setScanning(boolean scanning) {
		this.scanning = scanning;
	}
	
	/**
	 * @param port the port to set
	 */
	public void setPort(int port) {
		this.port = port;
	}
	
	/**
	 * @param backup the backup to set
	 */
	public void setBackup(String backup) {
		this.backup = backup;
	}
	
	/**
	 * @param filter the filter to set
	 */
	public void setFilter(FileFilter filter) {
		this.filter = filter;
	}
	
	/**
	 * @return the port
	 */
	public int getPort() {
		return port;
	}
	
	/**
	 * @param period the period to set
	 */
	public void setPeriod(long period) {
		this.period = period;
	}
	
	/**
	 * 文件选取
	 * @date 2012-6-14
	 */
	class SyncRunnalbe implements Runnable {
		/*** 同步间隔 */
		private long period;
		
		/**
		 * @param period 文件同步间隔
		 */
		public SyncRunnalbe(long period) {
			this.period = period;
		}
		
		/*
		 * @see java.lang.Runnable#run()
		 */
		public void run() {
			logger.info("同步文件扫描线程已启动");
			while(scanning) {
				try {
					// 同步
					sync();
				} catch (Exception e) {
					logger.error("同步失败!" + e.getMessage());
				}
				try {
					// 睡眠
					Thread.sleep(period);
				} catch (InterruptedException e) {
				}
			}
		}
	}
	
	/** 启动同步入口
	 * @param args
	 */
	public static void main(String[] args) {
		// 加载系统属性
		ConfigUtils.loadJvmProperty(args, "sync.config");
		// 建立同步线程
		new Sync();
	}
}
