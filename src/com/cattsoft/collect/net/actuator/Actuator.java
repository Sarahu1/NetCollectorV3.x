/**
 * 
 */
package com.cattsoft.collect.net.actuator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cattsoft.collect.io.file.archive.ZipFileUtils;
import com.cattsoft.collect.io.file.ftp.FtpUploadTask;
import com.cattsoft.collect.io.file.utils.CommandType;
import com.cattsoft.collect.net.adapter.BaseAdaptor;
import com.cattsoft.collect.net.listener.BaseListener;
import com.cattsoft.collect.net.process.ProcessResult;
import com.cattsoft.collect.net.reader.CommandFileReader;
import com.cattsoft.collect.net.report.SystemReportGather;
import com.cattsoft.collect.net.writer.BufferedWriterListener;

/**
 * 命令执行器
 * 
 * @author ChenXiaohong
 */
public abstract class Actuator extends BaseListener {
	private final Logger logger = LoggerFactory.getLogger(getClass());
	/** 是否启用命令执行器,默认为<code>true</code>.
	 * 该值为<code>false</code>时,将不进行任何操作
	 */
	private boolean enabled = true;
	/*** 缓存 */
	protected BlockingQueue<Future<ProcessResult>> cache;
	/*** 执行器服务 */
	protected ExecutorService service;
	/*** 适配器列表 */
	protected List<BaseAdaptor> adapters = Collections.synchronizedList(new LinkedList<BaseAdaptor>());
	/*** 监听器列表 */
	protected List<BaseListener> listeners = Collections.synchronizedList(new LinkedList<BaseListener>());
	/*** 命令读取器 */
	protected List<CommandFileReader> readers = Collections.synchronizedList(new LinkedList<CommandFileReader>());
	/*** 备份读取器,读取临时任务时会将读取器列表进行临时存储*/
	protected List<CommandFileReader> readerstmp = Collections.synchronizedList(new LinkedList<CommandFileReader>());
	/*** 当前命令集 */
	protected Command command;
	/*** 完成标记 */
	protected static final String DONE_TAG = "DONE";
	/*** 进程超时时长(默认为45) */
	protected long timeout = 45;
	/*** 进程超时时长单位(默认秒) */
	protected TimeUnit timeunit = TimeUnit.SECONDS;
	/*** 命令工作目录 */
	protected String directory = null;
	/*** FTP文件传输任务队列 */
	private FtpUploadTask ftp = null;
	/** 是否为忙时采集.
	 *  为{@code true}时将格式化文件名称
	 */
	private boolean daily = false;
	/*** 系统状态报告*/
	private SystemReportGather report = null;

	public void fireEvent(EventType event, Object... args) {
		super.fireEvent(event, args);
		// 数据适配器事件通知
		if (null != adapters) {
			for (BaseAdaptor adapter : adapters) {
				adapter.fireEvent(event, args);
			}
		}
		// 数据读取器事件通知
		if (null != readers) {
			for (CommandFileReader reader : readers) {
				reader.fireEvent(event, args);
			}
		}
		// 临时数据读取器事件通知
		if(null != readerstmp) {
			for (CommandFileReader reader : readerstmp) {
				reader.fireEvent(event, args);
			}
		}
		// 发送监听器事件通知
		if (null != listeners) {
			for (BaseListener listener : listeners) {
				listener.fireEvent(event, args);
			}
		}
		// 状态报告
		if(null != report) {
			report.fireEvent(event, args);
		}
	}

	public void afterExec(ProcessResult result, int index) {
		super.afterExec(result, index);
		if (null != adapters) {
			// 适配处理
			for (BaseAdaptor adapter : adapters) {
				result = adapter.process(result);
			}
		}
		handler(result, index);
	}

	public void afterFinish() {
		super.afterFinish();
		// 断开FTP连接
		// ftp.disconnect();
	}

	/**
	 * 启动入口
	 */
	public void lanuch() {
		//
	}

	/**
	 * 记录返回时会调用此方法. 子类若需要对结果集进行处理,可以重写此方法
	 * 
	 * @param result
	 *            结果集
	 * @param index
	 *            下标
	 */
	protected void handler(ProcessResult result, int index) {
		String[] results = result.getResult();
		for (String content : results) {
			logger.debug(content);
		}
	}

	public void transport(final String path, final boolean backup) {
		super.transport(path, backup);
		new Thread(new Runnable() {
			public void run() {
				try {
					// 对文件进行压缩后返回路径名
					String zipath = zip(path, true);
					// 添加到上传任务列表
					try {
						// 添加到上传队列,完成后删除压缩文件, 并进行备份
						ftp.push(zipath, backup);
					} catch (FileNotFoundException e) {
						logger.error("the data file upload failed!path:{},error:{}",
								path, e.toString());
					} catch (Exception e) {
						logger.error("Add upload task when abnormal!{}", e.getMessage());
					}
				} catch (IOException e) {
					logger.error("Can not compress data file({})!{}", path, e.getMessage());
				} catch (Exception e) {
					logger.error("Compress the file({}) abnormal!{}", path, e.getMessage());
				}
			}
		}, "upload_thread").start();
	}

	/**
	 * 添加监听器.
	 * 
	 * @param listener
	 *            监听器
	 * @return 是否添加成功
	 */
	public boolean addListener(BaseListener listener) {
		if (null == listener)
			throw new IllegalArgumentException("the listener can not be null");
		boolean added = listeners.add(listener);
		setActuator();
		return added;
	}

	/**
	 * 添加适配器.
	 * 
	 * @param adapter
	 *            适配器
	 * @return 是否添加成功
	 */
	public boolean addAdapter(BaseAdaptor adapter) {
		return adapters.add(adapter);
	}

	/**
	 * 添加命令读取器.
	 * 
	 * @param reader
	 *            读取器
	 * @return 是否添加成功
	 */
	public boolean addReader(CommandFileReader reader) {
		return readers.add(reader);
	}

	/**
	 * 设置适配器列表.
	 * 
	 * @param adapters
	 *            适配器
	 */
	public void setAdapters(List<BaseAdaptor> adapters) {
		this.adapters = adapters;
	}

	/**
	 * @return 已添加的适配器列表
	 */
	public List<BaseAdaptor> getAdapters() {
		return adapters;
	}

	/**
	 * 设置监听器列表.
	 * 
	 * @param listeners
	 *            监听器
	 */
	public void setListeners(List<BaseListener> listeners) {
		this.listeners = listeners;
		setActuator();
	}

	/**
	 * 循环监听器列表.设置{@link BufferedWriterListener#setActuator(Actuator)}
	 */
	private void setActuator() {
		for (BaseListener listener : this.listeners) {
			if (listener instanceof BufferedWriterListener) {
				// 设置
				BufferedWriterListener bwListener = (BufferedWriterListener) listener;
				if (bwListener.isTransfer()) {
					bwListener.setActuator(this);
				}
			}
		}
	}

	/**
	 * @return 已注册的监听器列表
	 */
	public List<BaseListener> getListeners() {
		return listeners;
	}

	/**
	 * 设置命令读取器.
	 * 
	 * @param readers
	 *            读取器
	 */
	public void setReaders(List<CommandFileReader> readers) {
		this.readers = readers;
	}

	/**
	 * @return 已添加的命令读取器
	 */
	public List<CommandFileReader> getReaders() {
		return readers;
	}
	
	public List<CommandFileReader> getReaderstmp() {
		return readerstmp;
	}

	/**
	 * @param readerstmp 临时数据读取器
	 */
	public void setReaderstmp(List<CommandFileReader> readerstmp) {
		this.readerstmp = readerstmp;
	}

	/**
	 * 清空命令读取器
	 */
	public void cleanReaders() {
		// 临时存储
		readerstmp = readers;
		readerstmp.addAll(readers);
		readers.clear();
	}

	/**
	 * 清空监听器
	 */
	public void clearListener() {
		listeners.clear();
	}

	/**
	 * 清空内容适配器
	 */
	public void clearAdapter() {
		adapters.clear();
	}

	/**
	 * 设置超时时长
	 * 
	 * @param timeout
	 *            单位默认为秒(s)
	 */
	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}

	/**
	 * 设置进程超时时长单位
	 * 
	 * @param timeunit
	 *            单位
	 */
	public void setTimeunit(TimeUnit timeunit) {
		this.timeunit = timeunit;
	}

	/**
	 * @return 当前超时时长
	 */
	public long getTimeout() {
		return timeout;
	}

	/**
	 * @param directory
	 *            当前命令工作目录
	 */
	public void setDirectory(String directory) {
		this.directory = directory;
	}

	/**
	 * @return 设置命令工作目录
	 */
	public String getDirectory() {
		return directory;
	}

	/**
	 * @param ftp
	 *            FTP 文件传输
	 */
	public void setFtp(FtpUploadTask ftp) {
		this.ftp = ftp;
	}

	/**
	 * @return 当前Ftp传输
	 */
	public FtpUploadTask getFtp() {
		return ftp;
	}
	
	/**
	 * @param daily 忙时采集
	 */
	public void setDaily(boolean daily) {
		this.daily = daily;
	}
	
	public boolean isDaily() {
		return daily;
	}
	
	public void setReport(SystemReportGather report) {
		this.report = report;
	}
	
	/**
	 * @param enabled 是否启用
	 */
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
	
	/**
	 * @return 当前启用状态
	 */
	public boolean isEnabled() {
		return enabled;
	}
	
	/** 获取本机网卡配置IP地址
	 * @return IP地址
	 */
	public String getLocalHost() {
		String host = "";
		try {
			// 根据网卡取本机配置的IP
			Enumeration<?> en = NetworkInterface.getNetworkInterfaces(); 
		    while (en.hasMoreElements()) { 
		        NetworkInterface i = (NetworkInterface) en.nextElement(); 
		        for (Enumeration<?> en2 = i.getInetAddresses(); en2.hasMoreElements();) { 
		            InetAddress addr = (InetAddress) en2.nextElement(); 
		            if (!addr.isLoopbackAddress()) { 
		                if (addr instanceof Inet4Address) {
		                	// 返回
		                    return addr.getHostAddress(); 
		                } 
		            } 
		        } 
		    }
			// Windows 采用以下方法
			if("".equals(host)) {
				host = InetAddress.getLocalHost().getHostAddress();
			}
		} catch (SocketException e) {
			logger.error("get the IP of the NIC configuration fails.{}", e.getMessage());
		} catch (Exception e) {
			//
		}
		return host;
	}

	/** 对文件进行压缩.
	 * @param filepath 源文件
	 * @param delete 删除源文件
	 * @return 已压缩的文件路径
	 * @throws IOException
	 */
	private String zip(String filepath, boolean delete) throws IOException {
		File file = new File(filepath);
		if(!file.exists())
			return null;
		// 压缩文件注释日期时间
		SimpleDateFormat timeSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		// 上级目录
		String parentFix = ((file.getParentFile() != null) ? file.getParentFile().getPath() + File.separator : "");
		// 名称(去后缀)
		String suffix = file.getName().substring(0,
				file.getName().lastIndexOf("."));
		// 忙时采集时格式化文件名称
		if(isDaily()) {
			SimpleDateFormat namesdf = new SimpleDateFormat("yyyyMMddHHmmss");
			// 是否重命名源文件名
			// TRACE_1P_20120417120525_1-1-18-9.log
			// 新文件名称
			File newfile = new File(
					file.getParentFile().getPath() + File.separator + CommandType.decide(suffix).type.toUpperCase() + "_"
							+ System.getProperty("monitor", "0") + "P_"
							+ namesdf.format(System.currentTimeMillis()) + ".log");
			// 压缩文件名称
			// 201204171206-1P-permanent.zip
			// {time}-{monitor}P-permanent
			suffix = namesdf.format(System.currentTimeMillis()) + "-"
					+ System.getProperty("monitor", "0") + "P-permanent";
			// 重命名
			if(file.renameTo(newfile)) {
				file = newfile;
				filepath = file.getPath();
			}
		}
		// 压缩文件后缀为.zip
		String zipFile = parentFix + suffix + ".zip";
		// 添加文件注释
		// 以下注释内容关系到文件合并时的数据分类,请不要随意改动名称
		StringBuffer comment = new StringBuffer();
		// 监测点
		comment.append("MonitorNumber:").append(System.getProperty("monitor", "")).append(line_separator);
		// 命令行
		comment.append("CommandLine:").append(Arrays.toString(command.getTemplate())).append(line_separator);
		// 命令数据长度
		comment.append("CommandSize:").append(command.size()).append(line_separator);
		// 网络地址
		comment.append("HostAddress:").append(getLocalHost()).append(line_separator);
		// 数据文件长度
		comment.append("DataLength:").append(file.length()).append(line_separator);
		// 日期时间戳
		comment.append("DateTime:").append(timeSdf.format(System.currentTimeMillis())).append(line_separator);
		// 压缩数据文件
		ZipFileUtils.zip(filepath, zipFile, comment.toString());
		if(delete) {
			// 删除源数据文件
			file.delete();
		}
		return zipFile;
	}
}