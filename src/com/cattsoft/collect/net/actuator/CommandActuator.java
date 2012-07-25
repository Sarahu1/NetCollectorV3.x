/**
 * 
 */
package com.cattsoft.collect.net.actuator;

import java.io.File;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cattsoft.collect.net.process.ActuatorProcess;
import com.cattsoft.collect.net.process.ProcessResult;
import com.cattsoft.collect.net.reader.CommandFileReader;

/**
 * 命令集运行器
 * 
 * @author ChenXiaohong
 */
public class CommandActuator extends Actuator {
	private final Logger logger = LoggerFactory.getLogger(getClass());
	/*** 同步锁 */
	public Lock lock;
	/*** 定时运行器 */
	protected SimpleTimeLimiter limiter;
	/*** 当前进程重新{@link #begin()}次数记录 */
	protected int frequency = 0;
	/*** 程序运行运行开始日期 */
	private Date start;
	/*** 程序运行周期结束结束 */
	private Date end;

	/**
	 * @param service
	 *            线程池服务
	 * @param readers
	 *            初始文件读取器列表
	 */
	public CommandActuator(ExecutorService service,
			List<CommandFileReader> readers) {
		this.service = service;
		this.readers = readers;
		// 缓存列表
		this.cache = new LinkedBlockingQueue<Future<ProcessResult>>();
		// 线程同步锁
		this.lock = new ReentrantLock(false);
		// 创建运行过程定时器
		this.limiter = new SimpleTimeLimiter(this.service);
	}

	public void lanuch() {
		super.lanuch();
		// 判断执行器启用状态
		if(!isEnabled()) {
			// 直接返回,不进行任何操作
			return;
		}
		// 正在准备启动处理任务,当前命令工作目录
		logger.info(
				"Are ready to start processing tasks, the current command working directory:{}",
				(null == getDirectory() || "".equals(getDirectory())) ? "[DEFAULT]"
						: getDirectory());
		// 开始处理
		execute(readers);
	}

	private void execute(List<CommandFileReader> readers) {
		try {
			// 广播任务启动事件通知,由该事件通知监听器准备更新数据
			fireEvent(EventType.BEGIN);
			// 启动时间
			start = new Date(System.currentTimeMillis());
			// 处理数据
			process();
			for (CommandFileReader reader : readers) {
				// 当前断点行
				if (reader.getSkip() > 0) {
					// 已找到数据文件读取断点记录
					logger.info(
							"Have found that the data file to read the breakpoint record:{}",
							reader.getSkip());
				}
				// 正在读取数据文件
				logger.info("Reading the data file({})..", reader.getFilePath());
				// 创建命令集
				this.command = reader.getCommand();
				// 执行命令集
				executeCommand(this.command);
			}
		} catch (Exception e) {
			// 命令任务发布失败
			logger.error("Command task release failure!{}", e.getMessage());
		} finally {
			lock.lock();
			//
			lock.unlock();
		}
	}

	/**
	 * @param command
	 *            命令集
	 */
	protected void executeCommand(Command command) {
		int size = command.size();
		logger.info("The current instance:{}", this.toString());
		// 正在发布运行任务,当前命令集模板:{},共{}条
		logger.info(
				"Is released to run the task, the current command set template {}, a total of {}",
				Arrays.toString(command.getTemplate()), size);
		ActuatorProcess process = null;
		// 将任务添加到服务进程
		for (int i = 0; i < size; i++) {
			process = new ActuatorProcess(getDirectory(), command, i);
			cache.add(service.submit(process));
		}
		// 命令集发布完成后添加完成通知
		cache.add(service.submit(new Callable<ProcessResult>() {
			public ProcessResult call() throws Exception {
				return new ProcessResult(DONE_TAG, CommandActuator.this.command, null, null);
			}
		}));
	}

	public void begin() {
		frequency++;
		super.begin();
	}
	
	public void complete() {
		super.complete();
		// 重置定向次数
		if(isDaily()) {
			if(frequency >= 24) {
				frequency = 0;
			}
		} else {
			frequency = 0;
		}
		//:~
	}

	public void afterFinish() {
		super.afterFinish();
		// 临时数据列表
		boolean exists_tmp = false;
		if(null != readerstmp && readerstmp.size() > 0) {
			for (CommandFileReader reader : readerstmp) {
				File file = new File(reader.getFilePath());
				if(file.exists()) {
					exists_tmp = file.exists();
					break;
				}
			}
		}
		// 判断是否存在临时数据需要处理
		if (exists_tmp) {
			// 已找到临时数据文件,准备处理
			logger.info("Find temporary data files, and be prepared to handle..");
			// 正在启动递归采集任务
			logger.debug("Is starting the recursive acquisition task..");
			// 启动递归任务
			execute(readerstmp);
		} else {
			// 周期完成事件通知
			fireEvent(EventType.COMPLETE);
			// 临时存储处理
			if (!this.service.isShutdown()) {
				// 当前命令集模板
				String command_template = Arrays.toString(this.command.getTemplate());
				
				end = new Date(System.currentTimeMillis());
				// 所有工作都已完成
				System.out
				.println("========================All tasks("+command_template+") are processed, is to turn off data services========================");
				// 所有任务都已处理完成,正在关闭数据服务
				logger.info("All tasks({}) are processed, is to turn off data services..", command_template);
				try {
					/******************************
					 * 服务不关闭,以免定时任务超时*
					 ******************************/
					// service.shutdown();
					// 数据处理服务已成功关闭
					logger.info("Data processing services have been successfully closed.");
				} catch (Exception e) {
					// 关闭线程池服务失败
					logger.error("Failed to close the thread pool!{}",
							e.getMessage());
				} finally {
					logger.debug("A little bit of cleaning work..");
					//清空命令行参数列表
					this.command.clean();
					//发送GC请求
					System.gc();
				}
				try {
					// 打印耗时
					Calendar startCal = Calendar.getInstance();
					startCal.setTime(start);
					Calendar endCal = Calendar.getInstance();
					endCal.setTime(end);
					
					long temp = endCal.getTimeInMillis()
							- startCal.getTimeInMillis();
					String time_str = temp / (60 * 60 * 1000l * 24) + " day "
							+ (temp % (60 * 60 * 1000l * 24)) / (60 * 60 * 1000l)
							+ " hour "
							+ ((temp % (60 * 60 * 1000l * 24)) % (60 * 60 * 1000l))
							/ (60 * 1000l) + " minute";
					System.out
					.println("========================All tasks("+command_template+") total time:"
							+ time_str + "========================");
					logger.info("All tasks({}) total time:{}", command_template, time_str);
				} catch (Exception e) {
				}
			}
		}
	}

	/**
	 * 数据处理进程
	 */
	protected void process() {
		new Thread(new Runnable() {
			public void run() {
				lock.lock();
				Future<ProcessResult> result = null;
				try {
					// result_process
					// 进程数据处理线程已启动
					logger.info("Process data processing thread has been started");
					int index = 0;
					int dataIndex = 0;
					while ((result = cache.take()) != null) {
						index++;
						dataIndex = command.getSkip() + index;
						fireEvent(EventType.BEFORE_EXECUTE, command.getSkip() + index);
						ProcessResult process = null;
						try {
							process = limiter.callWithTimeout(result, timeout,
									timeunit);
							// 判断是否到达完成标记
							if (DONE_TAG.equals(process.getResult()[0])) {
								break;
							}
							// 返回结果集,发送通知事件
							fireEvent(EventType.AFTER_EXECUTE, process,
									dataIndex);
							// 已处理第{}/{}条运行任务数据
							logger.debug("Handle {}/{}/" + frequency
									+ " running task("+Arrays.toString(command.deal(process.getParams()))+") data", index,
									command.size());
						} catch (TimeoutException e) {
							// 命令进程运行超时,数据未处理.当前数据行
							logger.debug(
									"Command process{} is running overtime, data unhandled. Current data row:{}",
									Arrays.toString(command.deal(index < command.size() ? index : index - 1)),
									dataIndex);
						}
					}
				} catch (InterruptedException e) {
					// 线程中断
					// 恢复中断状态,以免剥夺中断请求的调用者的权利
					Thread.currentThread().interrupt();
				} catch (Exception e) {
					// 数据处理线程启动失败,将无法处理进程数据
					logger.error(
							"Data processing thread failed to start, will not be able to deal with the process of data!{}",
							e.getMessage());
				} finally {
					// 进程数据处理线程已退出
					logger.info("Process data processing thread has exited");
					lock.unlock();
					fireEvent(EventType.FINISH);
					fireEvent(EventType.AFTER_FINISH);
				}
			}
		}, "result_process").start();
	}
}
