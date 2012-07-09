/**
 * 
 */
package com.cattsoft.collect.io.net.ftp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import com.cattsoft.collect.io.net.ftp.client.FtpTransferProcessListener;
import com.cattsoft.collect.io.net.ftp.client.Task;
import com.cattsoft.collect.io.net.ftp.exception.FTPException;
import com.cattsoft.collect.io.net.ssh.jsch.Logger;

/** FTP 上传任务管理.
 * @author 陈小鸿
 * @author chenxiaohong@mail.com
 *
 */
public class FTPTask {
	private Logger logger = null;
	/**
	 * FTP 客户端
	 */
	private FTP ftp = null;
	/**
	 * 钩子线程,程序退出时保存上传任务
	 */
	private Thread hook;
	/**
	 * 上传任务列表
	 */
	private Queue<Task> tasks = null;
	/**
	 * 当前上传任务
	 */
	private Task task = null;
	/**
	 * 上传列表保存路径
	 */
	private String taskpath = "conf/ftp_task.tsk";
	/**
	 * 文件备份目录
	 */
	private String backup = null;
	/**
	 * 连接最大重试次数
	 */
	private int maxRetries = -1;
	/**
	 * 当前连接重试次数
	 */
	private int retries = 0;
	
	/**
	 * 
	 */
	public FTPTask(FTP ftpclient, String backup) {
		this.ftp = ftpclient;
		this.backup = backup;
		// 日志输出
		logger = this.ftp.getLogger(getClass());
		// 加载上传任务
		loadTask();
		// 钩子线程
		hook = new Thread(new Runnable() {
			public void run() {
				// 断开FTP连接
				try {
					ftp.disconnect();
				} catch (Exception e) {
				}
				// 当前任务
				if(null != task && !task.isComplete()) {
					tasks.add(task);
				}
				if(!tasks.isEmpty()) {
					// 保存任务
					saveTask();
				}
				try {
					Runtime.getRuntime().removeShutdownHook(hook);
				} catch (Exception e) {
				}
			}
		});
		Runtime.getRuntime().addShutdownHook(hook);
		
		// 创建上传线程
		new Thread(new Runnable() {
			public void run() {
				logger.log(Logger.INFO, "FTP上传线程已成功启动");
				try {
					upload();
				} catch (Exception e) {
					logger.log(Logger.ERROR, "FTP上传出现异常!上传线程已退出");
				}
				logger.log(Logger.INFO, "FTP上传线程已退出");
			}
		}, "upload_thread").start();
	}
	
	/**
	 * @param ftp FTP客户端
	 */
	public FTPTask(FTP ftpclient) {
		this(ftpclient, null);
	}
	
	/**
	 * @param filepath 文件路径
	 */
	public void push(String...filepath) {
		push(null, false, filepath);
	}
	
	/**
	 * @param backup 是否备份,不备份将删除文件
	 * @param filepath 文件路径
	 */
	public void push(boolean backup, String...filepath) {
		push(null, backup, filepath);
	}
	
	/**
	 * @param uploadfolder 上传目录
	 * @param filepath 文件路径
	 */
	public void push(String uploadfolder, String...filepath) {
		push(uploadfolder, false, filepath);
	}
	
	/** 添加上传任务
	 * @param uploadfolder 上传目录
	 * @param backup 是否备份,不备份将删除文件
	 * @param filepath 文件路径
	 */
	public void push(String uploadfolder, boolean backup, String... filepath) {
		for (String path : filepath) {
			File file = new File(path);
			if(file.exists()) {
				if(null == uploadfolder || uploadfolder.trim().isEmpty()) {
					uploadfolder = ftp.getHome();
				}
				synchronized (tasks) {
					// 添加到列表
					tasks.add(new Task(uploadfolder, path, (getBackup() != null) ? backup : false));
				}
			} else {
				logger.log(Logger.ERROR, "上传任务(" + path + ")文件不存在, 任务已丢弃!");
			}
		}
		try {
			// 唤醒任务
			synchronized (tasks) {
				tasks.notifyAll();
			}
		} catch (Exception e) {
		}
	}
	
	/**
	 * 连接到服务器
	 * @throws FTPException 连接次数超出最大尝试次数,将抛出该异常
	 */
	private void connect() throws FTPException {
		boolean connected = false;
		logger.log(Logger.INFO, "正在连接FTP服务器..");
		// 连接尝试间隔时长
		long connWaitSec = 10;	//默认尝试等待时间(秒)
		try {
			// 断开
			ftp.logout();
			ftp.disconnect();
		} catch (Exception e) {
		}
		// 直到连接成功
		while(!connected) {
			try {
				connected = ftp.connect();
				// 连接成功后退出循环
				if(ftp.isConnected()) {
					logger.log(Logger.INFO, "已连接到FTP服务器");
					// 登录
					boolean logged = ftp.isLogged();
					int login_retries = 0;
					while(!logged) {
						try {
							login_retries++;
							logged = ftp.login();
						} catch (Exception e) {
							logger.log(Logger.ERROR, "登录服务器失败!" + e.getMessage() + 
									(null != e.getCause() ? ", " + e.getCause().getMessage() : ""));
						}
						if(!logged) {
							if(login_retries > 10) {
								// 登录失败次数指定次数
								throw new FTPException("登录服务器失败!");
							}
							try {
								// 睡眠3秒后继续
								Thread.sleep(3000);
							} catch (Exception e) {
							}
						}
					}
					logger.log(Logger.INFO, "登录FTP服务器成功");
					break;
				}
			} catch (Exception e) {
				logger.log(Logger.ERROR, "连接服务器失败!" + e.getMessage() + (e.getCause() != null ? ", " + e.getCause().getMessage() : ""));
			}
			// 连接失败, 重试间隔睡眠
			if(!connected) {
				retries++;
				// 超时最大尝试次数
				if(maxRetries > 0 && retries > maxRetries) {
					throw new FTPException("无法连接FTP服务器!请检查网络连接");
				}
				connWaitSec *= retries;
				logger.log(Logger.INFO, "Delaying for "+connWaitSec+" seconds before reconnect attempt #" + retries);
				try {
					// 睡眠
					Thread.sleep(connWaitSec * 1000l);
				} catch (Exception e) {
					;
				}
			}
		}
	}
	
	/**
	 * 上传方法
	 */
	public void upload() {
		try {
			ftp.connect();
		} catch (Exception e) {
			logger.log(Logger.ERROR, "无法连接FTP服务器" + e.getMessage());
		}
		while(true) {
			synchronized(tasks) {
				// 等待添加任务
				while(tasks.isEmpty()) {
					try {
						// 判断任务是否完成,断开连接并等待任务
						try {
							// 当前任务为完成状态时断开连接
							if(task != null && tasks.isEmpty()) {
								if(task.isComplete()) {
									logger.log(Logger.INFO, "正在从FTP服务器断开连接..");
									ftp.disconnect();
								}
							}
						} catch (Exception e) {
							logger.log(Logger.ERROR, "从服务器断开连接时出现异常!" + e.getMessage());
						}
						logger.log(Logger.INFO, "等待上传任务..");
						// 等待任务
						tasks.wait();
					} catch (InterruptedException e) {
					}
				}
			}
			// 上传任务列表
			while ((null != (task = tasks.poll()))) {
				// 检查连接
				try {
					// 未连接或未登录时,连接服务器
					if(!ftp.isConnected() || !ftp.isLogged()) {
						connect();
					}
				} catch (Exception e) {
					logger.log(Logger.ERROR, e.getMessage());
					// 退出上传
					logger.log(Logger.ERROR, "目前无法连接FTP服务器,将在5分钟后继续进行上传!");
					try {
						Thread.sleep(1000 * 60l * 5);
					} catch (Exception e2) {
						;
					}
					// 重新添加到列表
					tasks.add(task);
					continue;
				}
				// 上传文件
				boolean uploaded = false;
				final File taskFile = new File(task.getFilepath());
				// 判断文件是否存在
				if(!taskFile.exists()) {
					logger.log(Logger.INFO, "上传任务文件(" + taskFile.getAbsolutePath() + ")不存在!文件取消上传");
					continue;
				}
				try {
					uploaded = ftp.upload(task.getUploadpath(), taskFile, new FtpTransferProcessListener() {
						public void process(long step) {
							logger.log(Logger.INFO, "任务上传("+ taskFile.getPath() +")上传进度:" + step + "%");
						}
					});
				} catch (Exception e) {
					logger.log(Logger.INFO, "任务上传失败!文件(" + taskFile.getPath() + ")将重新上传!" 
							+ e.getMessage() + (null != e.getCause() ? ", " + e.getCause().getMessage() : ""));
				}
				task.setComplete(uploaded);
				if(task.isComplete()) {
					// 是否需要备份
					if(task.isBackup() && null != getBackup()) {
						// 备份文件
						logger.log(Logger.INFO, "正在备份上传任务文件("+ taskFile.getPath() +")..");
						String backpath = ftp.backup(taskFile, getBackup());
						if(null != backpath)
							logger.log(Logger.INFO, "文件备份成功,备份路径:" + backpath);
						else
							logger.log(Logger.ERROR, "文件备份失败!");
					}
					// 删除已上传的文件
					taskFile.delete();
				} else {
					// 任务错误次数超过10次
					if(task.getRetries() > task.getMaxRetries()) {
						try {
							Thread.sleep(2000);
						} catch (Exception e) {
						}
						try {
							// 重新进行连接
							connect();
						} catch (Exception e) {
						}
					}
					// 文件上传失败,重新连接到列表上传
					tasks.add(task);
				}
			}
		}
	}
	
	/**
	 * 加载任务
	 */
	private void loadTask() {
		tasks = new LinkedBlockingQueue<Task>();
		File taskFile = new File(taskpath);
		// 文件存在, 加载任务
		if(taskFile.exists()) {
			logger.log(Logger.INFO, "正在加载上传任务..");
			FileInputStream fis = null;
			ObjectInputStream ois = null;
			try {
				fis = new FileInputStream(taskFile);
				ois = new ObjectInputStream(fis);
				// 读取
				Queue<?> fileTasks = (Queue<?>) ois.readObject();
				// 添加到列表
				Collections.addAll(tasks, fileTasks.toArray(new Task[fileTasks.size()]));
				logger.log(Logger.INFO, "上传任务加载成功,当前任务共:" + tasks.size());
			} catch (Exception e) {
				logger.log(Logger.ERROR, "加载上传任务失败!上传任务将丢失!" + e.getMessage());
			} finally {
				try {
					if(null != ois)
						ois.close();
					if(null != fis)
						fis.close();
				} catch (Exception e2) {
				}
			}
			// 加载完成, 删除文件
			taskFile.delete();
		} else {
			logger.log(Logger.INFO, "未找到上传任务文件");
		}
	}
	
	/**
	 * 保存上传任务到文件
	 */
	private void saveTask() {
		// 保存任务
		FileOutputStream fos = null;
		ObjectOutputStream oos = null;
		try {
			File file = new File(taskpath);
			try {
				// 创建父级目录
				if(file.getParentFile() != null) {
					file.getParentFile().mkdirs();
				}
			} catch (Exception e) {
			}
			logger.log(Logger.INFO, "正在保存上传任务..");
			fos = new FileOutputStream(file, false);
			oos = new ObjectOutputStream(fos);
			oos.writeObject(tasks);
			oos.flush();
			logger.log(Logger.INFO, "上传任务已保存到文件("+ file.getAbsolutePath() +")");
		} catch (Exception e) {
			logger.log(Logger.ERROR, "上传任务列表保存失败!任务列表将丢失!"
					+ e.getMessage());
		} finally {
			try {
				if (null != fos)
					fos.close();
				if (null != oos)
					oos.close();
			} catch (Exception e2) {
			}
		}
	}
	
	/**
	 * @param taskpath the taskpath to set
	 */
	public void setTaskpath(String taskpath) {
		this.taskpath = taskpath;
	}
	
	/**
	 * @return the taskpath
	 */
	public String getTaskpath() {
		return taskpath;
	}
	
	/**
	 * @param backup the backup to set
	 */
	public void setBackup(String backup) {
		this.backup = backup;
	}
	
	/**
	 * @return the backup
	 */
	public String getBackup() {
		return backup;
	}
}
