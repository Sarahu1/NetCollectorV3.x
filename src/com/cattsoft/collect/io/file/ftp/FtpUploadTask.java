/**
 * 
 */
package com.cattsoft.collect.io.file.ftp;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cattsoft.collect.io.file.archive.ZipFileUtils;
import com.cattsoft.collect.io.file.utils.StringUtils;

/**
 * 文件上传任务. For usage:
 * <p>
 * <blockquote>
 *
 * <pre>
 * FtpUploadTask task = new FtpUploadTask(CommonFtpClient client); //实例化并添加任务后自动连接ftp服务器并上传
 * task.push("local path"); 添加本地文件路径到上传列表
 * task.push(".."); //添加更多
 * task.disconnect(boolean force); //上传完成后调用该方法断开连接,调用{@link #reconnect()}方法进行重连
 * </pre>
 *
 * </blockquote>
 * </p>
 * <p>
 * <blockquote>
 *
 * <pre>
 * 调用{@link #getFtpclient()}方法获取更多操作方法
 * </pre>
 * 
 * </blockquote>
 * </p>
 * 
 * @author ChenXiaohong
 */
public class FtpUploadTask implements Runnable {
	private Logger logger = LoggerFactory.getLogger(getClass());
	/*** Ftp封装类.*/
	private CommonFtpClient ftpclient = null;
	/*** 上传任务列表.*/
	private Queue<UploadTask> task = new LinkedBlockingQueue<UploadTask>();
	/*** 上传最大重试次数,-1表示不限制.*/
	private int try_max = -1;
	/*** 进程退出处理进程.当程序被结束时将保存当前上传队列到文件. */
	private Thread shutdownHook;
	/*** 上传列表保存路径.*/
	private String taskpath = "conf/ftp_upload.tsk";
	/*** 运行状态.默认为false*/
	private boolean running = false;
	/*** 当前上传任务.*/
	private UploadTask currtask = null;
	/*** Ftp服务器连接最大尝试次数.*/
	private int try_max_conn = -1;
	/*** 当前尝试连接次数.*/
	private long cur_try_conn = 1;
	/*** 任务唤醒定时器执行间隔时长.默认每30分钟唤醒检查一次*/
	private long task_wake_interval = 30 * 1000l * 60l;
	/*** 数据文件备份存放目录.*/
	private String backdir = "data/backup";
	/*** 数据备份最大月份记录*/
	private int maxBackupMonth = 3;
	/*** 当前上传任务是否已完成 */
	private boolean complete = true;
	
	public FtpUploadTask(CommonFtpClient client) {
		this.ftpclient = client;
		// 读取未完成任务
		task.addAll(loadTasks());
		
		// 创建异常退出监听处理线程
		shutdownHook = new Thread(new Runnable() {
			public void run() {
				// 停止上传
				running = false;
				// 断开连接
				ftpclient.disconnect(true);
				if(null != currtask && !currtask.isComplete()) {
					task.add(currtask);
				}
				// 判断任务列表是否为空
				if(!task.isEmpty()) {
					// 保存任务
					saveTasks();
				}
				Runtime.getRuntime().removeShutdownHook(shutdownHook);
			}
		}, "upload_task_hook");
		// 添加进程意外退出监听,用于将未完成的任务写入到文件
		Runtime.getRuntime().addShutdownHook(shutdownHook);
		
		new Thread(this, "upload_tread").start();
		
		// 手动运行
		synchronized(task) {
			task.notify();
		}
	}
	
	/** 添加上传任务.
	 * @param remote 远程文件名称
	 * @param path 文件路径
	 * @throws FileNotFoundException 上传文件不存在时将抛出该错误
	 */
	public void push(String remote, String path) throws FileNotFoundException {
		push(new String[]{remote}, false, path);
	}
	
	/** 添加上传任务.
	 * @param path 文件路径
	 * @throws FileNotFoundException
	 */
	public void push(String path) throws FileNotFoundException {
		push(null, false, path);
	}
	
	/** 添加上传任务.
	 * @param path 文件路径
	 * @param backup 上传后是否备份本地文件
	 * @throws FileNotFoundException
	 */
	public void push(String path, boolean backup) throws FileNotFoundException  {
		push(null, backup, path);
	}
	
	/**
	 * @param backup 是否备份
	 * @param paths 待上传文件列表
	 * @throws FileNotFoundException 
	 */
	public void push(boolean backup, String...paths) throws FileNotFoundException {
		push(null, backup, paths);
	}
	
	/** 添加上传任务.
	 * @param remote 远程文件名称
	 * @param paths 文件路径
	 * @param backup 上传完成后是否备份源数据文件
	 * @throws FileNotFoundException 上传文件不存在时将抛出该错误
	 */
	public void push(String[] remote, boolean backup, String... paths) throws FileNotFoundException {
		if(null == paths || paths.length == 0)
			return;
		// 添加任务
		for (int i = 0; i < paths.length; i++) {
			String path = paths[i];
			if(null == path || "".equals(path.trim()))
				continue;
			File file = new File(path);
			// 远程文件名称为空时默认使用本地文件名称
			String remoteName = file.getName();
			if(file.exists()) {
				if(null != remote && remote.length == paths.length) {
					if(!"".equals(remote[i])) {
						// 设置远程文件名称
						remoteName = remote[i];
					}
				}
				logger.info("add upload task({}) to queue,backup:{},backup path:{}", new Object[]{path, backup, backdir});
				task.add(new UploadTask(remoteName, path, backup));
				// 重置连接次数
				if(try_max_conn > 0 && cur_try_conn > try_max_conn) {
					cur_try_conn = 1;
				}
			} else {
				if(paths.length == 1) {
					throw new FileNotFoundException("上传任务数据文件("+file.getPath()+")不存在!");
				} else {
					logger.error("Upload task file({}) does not exist", file.getPath());
				}
			}
		}
		// 检查运行状态,出现停止运行时自动启动线程
		if(!running) {
			lanuch();
		}
		// 唤醒线程进行上传
		synchronized (task) {
			task.notifyAll();
		}
	}
	
	/** 移除上传任务.
	 * @param path 路径
	 */
	public void remove(String path) {
		task.remove(path);
	}
	
	/**
	 * @return the complete
	 */
	public boolean isComplete() {
		return complete;
	}
	
	/**
	 * 重连Ftp服务器并登录,直到连接成功
	 * @throws ConnectException 
	 */
	private void reconnect() throws ConnectException {
		// 断开
		try {
			ftpclient.logout();
			ftpclient.disconnect(true);
		} catch (Exception e) {
			logger.error("Disconnect the server connection failure!{}", e.getMessage());
		}
		// 连接
		//: 重试连接参数
		long try_wait_sec = 10;	//默认尝试等待时间(秒)
		//:~
		while(running && !ftpclient.isConnected()) {
			try {
				ftpclient.connect();
			} catch (UnknownHostException e) {
				logger.error("Server address error!{}", e.getMessage());
			}
			if(!ftpclient.isConnected()) {
				if (try_max_conn > 0 && cur_try_conn > try_max_conn) {
					// 抛出连接异常
					// Ftp服务器无法连接,请检查服务器设置
					throw new ConnectException("Ftp Server can not connect, check the server settings");
				}
				if(cur_try_conn > 3l) {
					// 考虑更换FTP服务器
					if(ftpclient.getSparehosts().size() > 0) {
						// 设置备用服务器
						Collections.shuffle(ftpclient.getSparehosts(), new java.util.Random());
						ftpclient.setHostname(ftpclient.getSparehosts().get(0));
					}
				}
				if(cur_try_conn > 5l) {
					//超时5时次每次递增到1分钟
					try_wait_sec = 60l;
				}
				try_wait_sec *= cur_try_conn;
				// 控制在15分钟内
				if(try_wait_sec > 900l) {
					try_wait_sec = 900l;
				}
				// 正在延迟 "+()+" 秒，在此之后将尝试第 "+cur_try+" 次重新连接
				logger.info("Delaying for {} seconds before reconnect attempt #{}", try_wait_sec, cur_try_conn);
				try {
					//当前线程睡眠
					Thread.sleep(try_wait_sec * 1000l);
				} catch (InterruptedException e) {
					;
				}
				cur_try_conn++;
			} else {
				cur_try_conn = 1;
				logger.info("Ftp Server connection is successful!");
				// 默认上传至主要Ftp
				ftpclient.setHostname(ftpclient.getPreferencehost());
				try {
					ftpclient.getClient().setSoTimeout(30000);
				} catch (Exception e) {
					//
				}
				// 登录
				login();
			}
		}
	}
	
	/** 登录到Ftp服务器
	 * @throws ConnectException
	 */
	private void login() throws ConnectException {
		// 登录认证
		long cur_try_login = 0;
		/*** 登录重试等等时长(s)*/
		long try_login_sec = 10;
		while(running && !ftpclient.isAuthenticated()) {
			boolean flag = ftpclient.login();
			if(!flag) {
				if(cur_try_login > 10l) {
					// 重连
					reconnect();
				}
				cur_try_login++;
				try_login_sec *= cur_try_login;
				try {
					logger.info("Delaying for {} seconds before login attempt #{}", try_login_sec, cur_try_login);
					//当前线程睡眠
					Thread.sleep(try_login_sec * 1000l);
				} catch (InterruptedException e) {
					;
				}
			} else {
				logger.info("login ftp server success!");
			}
		}
	}
	
	/**
	 * 上传运行.
	 */
	public void run() {
		// 置为true
		running = true;
		// 循环,直到条件变化
		while(running) {
			synchronized(task) {
				// 等待添加任务
				while(task.isEmpty()) {
					try {
						// 判断任务是否完成,断开连接并等待任务
						try {
							// currtask为完成状态时断开连接
							if(null == currtask || currtask.isComplete()) {
								if(task.isEmpty()) {
									complete = true;
									ftpclient.disconnect(false);
								}
							}
						} catch (Exception e) {
							logger.error("Disconnected from the server connection error.{}", e.getMessage());
						}
						// 等待任务
						task.wait();
					} catch (InterruptedException e) {
					}
				}
				// 标记任务正在上传
				complete = false;
				// 上传任务列表
				while (running && (null != (currtask = task.poll()))) {
					if(!ftpclient.isConnected() || !ftpclient.isAuthenticated()) {
						// 连接并登录
						try {
							reconnect();
						} catch (ConnectException e) {
							// Ftp服务器无法连接,上传取消
							logger.error("Ftp Server can not connect, the upload task will be canceled!Error:{}", e.getMessage());
							// 无法连接服务器
							// 退出上传
							break;
						}
					}
					boolean flag = false;
					try {
						File upfile = new File(currtask.getPath());
						// 再次验证文件是否存在
						if(!upfile.exists()) {
							logger.info("ftp upload task data file not found!{}", currtask.getPath());
							// 取消上传
							continue;
						}
						flag = ftpclient.upload(upfile.getName(), upfile, new FtpTransferProcessListener(){
							public void process(double step) {
								logger.debug("Ftp task({}) progress {}", currtask.getPath(), step);
							}
						});
						if(flag) {
							// 是否需要备份文件
							if(currtask.isBackup()) {
								// 备份
								try {
									String backup_path = backup(upfile, new File(upfile.getName()));
									boolean backuped = (!"".equals(backup_path));
									if(backuped) {
										logger.info("data file backup success!path:" + upfile.getPath() + " -> " + backup_path);
									}
								} catch(Exception e) {
									// 备份失败
									logger.error("the local file({}) backup failed!{}", upfile.getPath(), e.getMessage());
								}
							}
							// 删除
							upfile.delete();
							logger.info("Ftp upload task({}) upload success!", currtask.getPath());
						}
					} catch (Exception e) {
						// 避免异常情况
						if(null == currtask) {
							continue;
						}
						logger.error("file({}) upload fail!{}", currtask.getPath(), e.getMessage());
					}
					// 重试次数增加
					currtask.setRetries(currtask.getRetries()+1);
					if(!flag) {
						// 判断不限制上传次数
						// 判断已重试次数
						if(try_max > -1 && currtask.getRetries() > try_max) {
							// 任务不再上传
							// 上传任务已到达重试上传次数限制,文件不再上传
							logger.error("uploading task ({}) has reached the retry to uploading a number of restrictions, files are no longer uploading!", currtask.getPath());
						} else {
							// 重新添加到列表,重试上传
							task.add(currtask);
							task.notifyAll();
						}
						// 上传重试次数到达指定上限时断开重连
						if(currtask.getRetries() > 10) {
							try {
								reconnect();
							} catch (ConnectException e) {
								logger.error("retry connect ftp fail!{}", e.getMessage());
							}
						}
					} else {
						// 标记上传成功
						currtask.setComplete(true);
					}
				}
				complete = true;
				// 上传任务列表完成后,设置运行状态.
				// running = false;
				// 取消设置,线程不退出
			}
		}
	}
	
	/**
	 * @param taskpath 设置上传任务列表路径
	 */
	public void setTaskpath(String taskpath) {
		this.taskpath = taskpath;
	}
	
	/**
	 * @param try_max 最大重试上传次数
	 */
	public void setTry_max(int try_max) {
		this.try_max = try_max;
	}
	
	public void setTask_wake_interval(long task_wake_interval) {
		this.task_wake_interval = task_wake_interval;
	}
	
	/**
	 * @param backdir 备份目录
	 */
	public void setBackdir(String backdir) {
		this.backdir = backdir;
	}
	
	public void setMaxBackupMonth(int maxBackupMonth) {
		this.maxBackupMonth = maxBackupMonth;
	}
	
	/**
	 * 加载未完成任务列表
	 */
	private Queue<UploadTask> loadTasks() {
		Queue<UploadTask> tasklist = new LinkedBlockingQueue<UploadTask>();
		File taskfile = new File(taskpath);
		if(taskfile.exists()) {
			logger.info("load ftp upload task({})..", taskpath);
			// 读取文件
			// 读取上传文件队列
			FileInputStream fis = null;
			ObjectInputStream ois = null;
			try {
				fis = new FileInputStream(taskfile);
				ois = new ObjectInputStream(fis);
				Queue<?> readTaskFiles = (Queue<?>) ois.readObject();
				// 添加到列表
				Collections.addAll(tasklist, 
						readTaskFiles.toArray(new UploadTask[readTaskFiles.size()]));
				logger.info("current upload task queue size:{}", readTaskFiles.size());
			} catch (Exception e) {
				logger.error(e.toString());
				logger.info("Read error in the upload queue!{}", e.getMessage());
			} finally {
				try {
					if (null != ois) {
						ois.close();
					}
					if (null != fis) {
						fis.close();
					}
				} catch (Exception e2) {
					logger.error("close InputStream Error.{}", e2.getMessage());
				}
			}
			// 删除队列数据文件
			taskfile.delete();
		}
		return tasklist;
	}
	
	/**
	 * 保存上传任务
	 */
	private void saveTasks() {
		// 保存任务
		FileOutputStream fos = null;
		ObjectOutputStream oos = null;
		try {
			logger.info("saveing upload task to file({})..", taskpath);
			
			fos = new FileOutputStream(taskpath, false);
			oos = new ObjectOutputStream(fos);
			oos.writeObject(task);
			oos.flush();
		} catch (Exception e) {
			logger.error("Upload task queue holds failed!"
					+ e.getMessage());
		} finally {
			try {
				if (null != fos) {
					fos.close();
				}
				if (null != oos) {
					oos.close();
				}
			} catch (Exception e2) {
				logger.error("close stream error.{}", e2.getMessage());
			}
		}
	}
	
	public CommonFtpClient getFtpclient() {
		return ftpclient;
	}
	
	/** 备份文件.
	 * @param srcFile 源文件
	 * @param toFile 备份文件名称
	 * @return 备份路径
	 * @throws Exception 
	 */
	public String backup(File srcFile, File toFile) throws Exception {
		String backup_path = "";
		// 创建当月目录
		SimpleDateFormat yearmonthSdf = new SimpleDateFormat("yyyyMM");
		File backupfolder = new File(backdir + File.separator + yearmonthSdf.format(System.currentTimeMillis()));
		// 压缩文件
		if(srcFile.getPath().matches(".*?\\.zip$")) {
			// 读取文件注释,获取文件创建日期信息
			String comment = ZipFileUtils.extractZipComment(srcFile.getPath());
			try {
				String datetimeStr = StringUtils.getCommentValue(comment, "DateTime:");
				SimpleDateFormat dateTimeSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				Date datetime = dateTimeSdf.parse(datetimeStr);
				backupfolder = new File(backdir + File.separator + yearmonthSdf.format(datetime));
			} catch (Exception e) {
				logger.info("Read the file annotation information fails, you can not access the file({}) creation date!", srcFile.getAbsoluteFile());
			}
		}
		if(!backupfolder.exists()) {
			backupfolder.mkdirs();
		}
		// 备份文件路径
		File backupFile = new File(backupfolder.getPath(), toFile.getName());
		// 是否已存在该文件
		if(backupFile.exists()) {
			// 删除
			backupFile.delete();
		}
		// 移动文件到目录
		boolean backuped = srcFile.renameTo(backupFile);
		if(!backuped) {
			// 其它方法
			FileInputStream fis = null;
			FileOutputStream fos = null;
			try {
				fis = new FileInputStream(srcFile);
				fos = new FileOutputStream(backupFile);
				byte[] buffer = new byte[1024 * 4];
				int n = 0;
				while (-1 != (n = fis.read(buffer))) {
					fos.write(buffer, 0, n);
				}
				backuped = true;
			} catch (Exception e) {
				logger.error("backup file error!path:{}", srcFile.getPath());
			} finally {
				if(null != fis) {
					try {
						fis.close();
					} catch (IOException e) {
					}
				}
				if(null != fos) {
					try {
						fos.close();
					} catch (IOException e) {
					}
				}
			}
		}
		if(backuped) {
			srcFile.delete();
			backup_path = backupFile.getPath();
		}
		// 检查备份记录深度
		// 检查备份月份
		File[] backFolders = backupfolder.getParentFile().listFiles(new FileFilter() {
			public boolean accept(File file) {
				return file.isDirectory();
			}
		});
		// 对文件夹按名称进行排序
		java.util.Date nowdate = new Date(System.currentTimeMillis());
		for (File backfolder : backFolders) {
			try {
				java.util.Date folderdate = yearmonthSdf.parse(backfolder.getName());
				int month = differMonth(nowdate, folderdate);
				if(month >= maxBackupMonth) {
					backfolder.delete();
				}
			} catch (ParseException e) {
				// 文件夹格式问题出现无法转换
			}
		}
		return backup_path;
	}
	
	/** 计算日期相差月份
	 * @param nowdate
	 * @param folderdate
	 * @return
	 */
	private int differMonth(Date nowdate, Date folderdate) {
		int month = 0;
		int f = 0;
		Calendar nowCanendar = Calendar.getInstance();
		nowCanendar.setTime(nowdate);

		Calendar folderCanendar = Calendar.getInstance();
		folderCanendar.setTime(folderdate);

		if (nowCanendar.equals(folderCanendar))
			return 0;
		if (nowCanendar.after(folderCanendar)) {
			Calendar temp = nowCanendar;
			nowCanendar = folderCanendar;
			folderCanendar = temp;
		}
		if (folderCanendar.get(Calendar.DAY_OF_MONTH) < nowCanendar
				.get(Calendar.DAY_OF_MONTH)) {
			// flag = 1;
		}
		if (folderCanendar.get(Calendar.YEAR) > nowCanendar.get(Calendar.YEAR)) {
			month = ((folderCanendar.get(Calendar.YEAR) - nowCanendar
					.get(Calendar.YEAR))
					* 12
					+ folderCanendar.get(Calendar.MONTH) - f)
					- nowCanendar.get(Calendar.MONTH);
		} else {
			month = folderCanendar.get(Calendar.MONTH)
					- nowCanendar.get(Calendar.MONTH) - f;
		}
		return month;
	}
	
	/**
	 * 线程启动方法
	 */
	public void lanuch() {
		logger.info("start ftp data upload thread..");
		try {
			Thread upload_thread = new Thread(this, "upload_thread");
			upload_thread.setDaemon(true);
			upload_thread.start();
			// 创建任务唤醒定时器
			Timer task_timer = new Timer("wakeup_timer", true);
			task_timer.schedule(new TaskPatrol(this.task), task_wake_interval);
			logger.info("Ftp upload thread startup is complete.");
		} catch (Exception e) {
			logger.error("Data upload thread failed to start!");
		}
	}
}

/**
 * 上传队列任务.
 * 
 * @author ChenXiaohong
 */
class UploadTask implements Serializable, Cloneable {
	private static final long serialVersionUID = 1L;
	/*** 远程文件保存名称 */
	private String remote;
	/*** 上传任务文件路径 */
	private String path;
	/*** 上传完成后是否删除本地文件 */
	private boolean backup;
	/*** 上传过程已重试次数 */
	private int retries;
	/*** 上传是否完成 */
	private boolean complete;

	/**
	 * @param remote 远程文件名称
	 * @param path 本地文件路径
	 */
	public UploadTask(String remote, String path) {
		this(remote, path, false);
	}

	/**
	 * @param remote 
	 *            远程文件名称
	 * 
	 * @param path
	 *            本地文件路径
	 * @param backup
	 *            上传完成后备份本地文件
	 */
	public UploadTask(String remote, String path, boolean backup) {
		this.remote = remote;
		this.path = path;
		this.backup = backup;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public void setBackup(boolean backup) {
		this.backup = backup;
	}
	
	public boolean isBackup() {
		return backup;
	}

	public int getRetries() {
		return retries;
	}

	public void setRetries(int retries) {
		this.retries = retries;
	}

	public boolean isComplete() {
		return complete;
	}

	public void setComplete(boolean complete) {
		this.complete = complete;
	}
	
	public String getRemote() {
		return remote;
	}

	public void setRemote(String remote) {
		this.remote = remote;
	}

	public Object clone() throws CloneNotSupportedException {
		return super.clone();
	}
	
	public String toString() {
		return "[path=" + path + ",delete=" + backup + 
				",retries=" + retries + ",complete=" + complete + "]";
	}
}

/** 任务唤醒进程.
 * @author ChenXiaohong
 */
class TaskPatrol extends TimerTask {
	private Queue<UploadTask> task;
	
	public TaskPatrol(Queue<UploadTask> task) {
		this.task = task;
	}
	
	public void run() {
		try {
			// 唤醒
			task.notifyAll();
		} catch (Exception e) {
			//
		}
	}
}
