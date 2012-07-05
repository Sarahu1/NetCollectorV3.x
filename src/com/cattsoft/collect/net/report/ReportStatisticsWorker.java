package com.cattsoft.collect.net.report;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 状态上报数据处理.
 * 将数据处理为可识别的数据信息,并写入到文件.
 * @author ChenXiaohong
 *
 */
public class ReportStatisticsWorker implements Runnable {
	private Logger logger = LoggerFactory.getLogger(getClass());
	private List<ServerDataEvent> queue = new LinkedList<ServerDataEvent>();
	// 缓存数据
	private Map<String, Map<String ,String>> cache = new HashMap<String, Map<String ,String>>();  
	/*** 忙时数据缓存,与常规数据区分 */
	private Map<String, Map<String ,String>> cache_daily = new HashMap<String, Map<String ,String>>();
	/*** 监测点列表 */
	private Set<String> monitors = new HashSet<String>();
	// 换行符
	private final static String LINE_SEPARATOR = System.getProperty("line.separator", "\n");
	private final static SimpleDateFormat SDF_DATE = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	/*** 数据超时时长(分钟) */
	private long timeout = 10;
	/*** 已进行注册的客户端列表 */
	private Set<SelectionKey> channels = new LinkedHashSet<SelectionKey>();
	/*** 当前状态数据 */
	private StringBuffer statusBuffer = new StringBuffer();
	/*** 监测点数据 */
	private Properties monitors_map;
	/*** 监测点数据补全文件路径 */
	private static String monitorsPath = System.getProperty("report.monitormap", "monitor_map.cf");
	/*** 数据传输格式 */
	private CharsetEncoder encoder;
	/*** 数据流结束标识字符串 */
	private String endTag = "---END---";
	/*** 线程退出监听钩子线程 */
	private Thread hook;
	
	/**
	 * @param path 监测点名称配置文件
	 */
	public ReportStatisticsWorker(String path) {
		// 读取状态文件数据
		statusBuffer = new StringBuffer("MONI\tADDRESS\tTYPE\tUSER\tPID\t%CPU\t%MEM\tVSZ\tRSS\tSTAT\tSTART\tTIME\tVERSION\tUPDATE\tCOMMAND\tDATE\tCATALOG");
		monitors_map = new Properties();

		BufferedReader bf = null;
		FileInputStream fis = null;
		try {
			File monitors_file = new File(monitorsPath);
			if(monitors_file.exists()) {
				fis = new FileInputStream(monitors_file);
				bf = new BufferedReader(new InputStreamReader(fis));
				monitors_map.load(bf);
			}
		} catch (IOException e) {
			logger.error("读取本地状态文件出现异常!{}", e.toString());
		} finally {
			try {
				if(null != bf)
					bf.close();
				if(null != fis)
					fis.close();
			} catch (Exception e2) {
			}
		}
		try {
			encoder = Charset.forName("UTF-8").newEncoder();
		} catch (Exception e) {
			encoder = Charset.defaultCharset().newEncoder();
			logger.info("报表数据编码使用:" + Charset.defaultCharset().name());
		}
		try {
			hook = new Thread(new Runnable() {
				public void run() {
					Iterator<SelectionKey> iterators =  channels.iterator();
					// 关闭已连接的客户端
					while(iterators.hasNext()) {
						try {
							// 关闭
							iterators.next().channel().close();
							// 移除
							iterators.remove();
						} catch (IOException e) {
						}
					}
				}
			});
			Runtime.getRuntime().addShutdownHook(hook);
		} catch (Exception e) {
			logger.error("添加统计服务退出监听钩子失败!"+e.getMessage());
		}
	}
	
	public ReportStatisticsWorker() {
		this(monitorsPath);
	}
	
	public void setEndTag(String endTag) {
		this.endTag = endTag;
	}
	
	/** 接受处理数据
	 * @param server 服务
	 * @param selector 选择器
	 * @param key 客户端连接
	 * @param data 数据
	 * @param count 长度
	 */
	public void process(CollectorReportStatistics server, Selector selector, SelectionKey key, byte[] data, int count) {
		byte[] dataCopy = new byte[count];
		System.arraycopy(data, 0, dataCopy, 0, count);
		synchronized(queue) {
			queue.add(new ServerDataEvent(server, selector, key, dataCopy));
			queue.notify();
		}
	}
	
	/** 数据处理
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		ServerDataEvent dataEvent = null;
		while(true) {
			// Wait for data to become available
			synchronized(queue) {
				while(queue.isEmpty()) {
					try {
						queue.wait();
					} catch (InterruptedException e) {
					}
				}
				dataEvent = (ServerDataEvent) queue.remove(0);
				try {
					String data = new String(dataEvent.data);
					// 是否为注册
					if("register".equals(data)) {
						// 添加到列表,数据推送更新
						channels.add(dataEvent.key);
						try {
							// 写出文件缓存数据至当前客户端
							writeGather(dataEvent);
						} catch (Exception e) {
							logger.error("向客户端发送数据时出现异常!{}", e.toString());
						}
						continue; // 结束流程
					} else if("disconnect".equals(data)) {
						// 断开
						try {
							((SocketChannel)dataEvent.key.channel()).close();
						} catch (Exception e) {
						}
						synchronized (channels) {
							channels.remove(dataEvent.key);
						}
					} else {
						// 处理数据
						handle(dataEvent);
					}
					/********************************/
					/**写入文件,推送数据至监测客户端*/
					/********************************/
					flush();
				} catch (Exception e) {
					logger.error("处理状态数据时出现异常!{}", e.toString());
				}
			}
		}
	}

	/** 写出数据
	 * @param dataEvent
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private void writeGather(ServerDataEvent dataEvent) throws IOException {
		StringBuffer gather = new StringBuffer();
		FileReader fr = null;
		BufferedReader br = null;
		try {
			fr = new FileReader("gather");
			br = new BufferedReader(fr);
			String line;
			while(null != (line = br.readLine())) {
				gather.append(line).append(LINE_SEPARATOR);
			}
		} catch (Exception e) {
			gather = statusBuffer;
		} finally {
			try {
				if(null != br)
					br.close();
				if(null != fr)
					fr.close();
			} catch (Exception e2) {
			}
		}
		// 向客户端写出数据
		((SocketChannel) dataEvent.key.channel()).write(encoder
				.encode(CharBuffer.wrap(appendEndTag(statusBuffer))));
//		dataEvent.socket.write(ByteBuffer.wrap(gather.toString().getBytes()));
	}
	
	/** 处理数据.
	 * @param data 数据
	 */
	private void handle(ServerDataEvent event) {
		String data = new String(event.data);
		// 删除 \r 符号
		data = data.replaceAll("\r", "");
		// 使用回车符对数据进行分割
		String[] datas = data.split("\n");
		Map<String ,String> map = new HashMap<String, String>();
		for (String line : datas) {
			String key = line.substring(0, line.indexOf(":"));
			String value = line.substring(line.indexOf(":") + 1);
			map.put(key, value);
			// 添加更新时间6
			map.put("update", SDF_DATE.format(System.currentTimeMillis()));
		}
		String monitor = map.get("monitor");
		monitor = (null != monitor) ? monitor : "unknow";
		// 判断应用程序状态是否正在停止
		if ("stop".equalsIgnoreCase(map.get("app_status"))
				|| "kill".equalsIgnoreCase(map.get("app_status"))) {
			// 监测点已停止,删除缓存数据
			if ("daily".equals(map.get("type")))
				cache_daily.remove(monitor);
			else
				cache.remove(monitor);
			if(null == cache.get(monitor) && null == cache_daily.get(monitor)) {
				monitors.remove(monitor);
			}
			// logger.info("监测点({})采集进程已停止", monitor);
			return;
		}
		// 去除应用程序状态标识
		map.remove("app_status");
		String ip = ""; // 客户端IP地址
		try {
			ip  = ((SocketChannel)event.key.channel()).socket().getInetAddress().getHostAddress();
		} catch (Exception e) {
		}
		// 配置名称
		String name = monitors_map.getProperty(monitor, "unknow");
		if(null != name) {
			String[] adds = name.split("@");
			name = adds[0];
			String oriIP = ip;
			ip = (adds.length > 1) ? adds[1] : ip;
			if(!oriIP.isEmpty() && !oriIP.equals(ip)) {
				ip = ip + "/" + oriIP;
			}
		}
		map.put("name", name);
		map.put("ip", ip);
		// 区分常规/忙时数据
		if ("daily".equals(map.get("type")))
			cache_daily.put(monitor, map);
		else
			cache.put(monitor, map);
		monitors.add(monitor);
	}
	
	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}
	
	public long getTimeout() {
		return timeout;
	}
	
	/**
	 * 将缓存区数据写入到文件. 
	 * 使用 Synchronized 同步该方法
	 */
	private synchronized void flush() {
		// 根据监测点编号排序
		String[] monis = monitors.toArray(new String[]{});
		Arrays.sort(monis);
		statusBuffer = new StringBuffer("MONI\tNAME\tADDRESS\tTYPE\tUSER\tPID\t%CPU\t%MEM\tVSZ\tRSS\tSTAT\tSTART\tTIME\tVERSION\tUPDATE\tCOMMAND\tDATE\tCATALOG").append(LINE_SEPARATOR);
		for (String monitor : monis) {
			Map<String, String> values_map = cache.get(monitor);
			// 常规数据
			if(null != values_map) {
				appendComboStatus(monitor, values_map, cache);
			}
			// 忙时数据
			if(null != cache_daily.get(monitor)) {
				values_map = cache_daily.get(monitor);
				appendComboStatus(monitor, values_map, cache_daily);
			}
			// 未找到数据,表明该监测点已没有数据
			try {
				if(null == values_map)
					monitors.remove(monitor);
			} catch (Exception e) {
				logger.error("移除已停止的采集点状态数据失败,部分数据已失效!");
			}
		}
		
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream("gather", false);
			FileChannel channel = fos.getChannel();
			// 写入到文件
			channel.write(encoder.encode(CharBuffer.wrap(statusBuffer)));
			channel.close();
		} catch (IOException e) {
			logger.error("保存状态数据到文件时出现异常!{}", e.toString());
		} finally {
			try {
				if(null != fos)
					fos.close();
			} catch (Exception e2) {
			}
		}
		// 向已注册的客户端写入数据
		Iterator<SelectionKey> iterators =  channels.iterator();
		SelectionKey key;
		SocketChannel channel;
		while(iterators.hasNext()) {
			key = iterators.next();
			channel = (SocketChannel)key.channel();
			try {
				// 判断连接状态
				if(channel.isConnected()) {
					// 设置数据发送缓存区大小
					channel.socket().setSendBufferSize(8192);
					// 数据进行编码后通过Socket写出到数据通道
					channel.write(encoder.encode(CharBuffer.wrap(appendEndTag(statusBuffer))));
					try {
						// 刷新
						channel.socket().getOutputStream().flush();
					} catch (Exception e) {
					}
					key.interestOps(SelectionKey.OP_READ);
				} else {
					// 从列表中删除已断开的连接
					iterators.remove();
				}
			} catch (IOException e) {
				iterators.remove();
			}
		}
	}
	
	private void appendComboStatus(String monitor,
			Map<String, String> values_map,
			Map<String, Map<String, String>> cache) {
		long sec = 0l;
		try {
			Date update = SDF_DATE.parse(values_map.get("update"));
			// 计算数据更新时间与现在时间秒差
			sec = (System.currentTimeMillis() - update.getTime()) / (1000l);
			// 删除超过指定时长未更新的数据
			if ((sec / 60l) >= timeout) {
				cache.remove(monitor);
				monitors.remove(monitor);
			}
		} catch (Exception e) {
			logger.error("状态({})超时计算时出现异常!{}", values_map, e.toString());
		}
		try {
			// 使用空格对数据进行分割 
			String[] status = values_map.get("status").split("\\s+");
			// 按照标题顺序拼接数据
			statusBuffer.append(monitor).append("\t").append(values_map.get("name"))
			.append("\t").append(values_map.get("ip"))
			.append("\t").append(status[15].split("=")[1])
			.append("\t").append(status[0]).append("\t").append(status[1])
			.append("\t").append(status[2]).append("\t").append(status[3])
			.append("\t").append(status[4]).append("\t").append(status[5])
			.append("\t").append(status[7]).append("\t").append(status[8])
			.append("\t").append(status[9]).append("\t")
			.append(status[16].split("=")[1]).append("\t")
			.append((sec >= 60 ? sec / 60l + "m" : sec + "s"))
			.append("\t").append(values_map.get("command"))
			.append("\t").append(values_map.get("time"))
			.append("\t").append(values_map.get("catalog"))
			.append(LINE_SEPARATOR);
		} catch (Exception e) {
			// logger.error("无法处理监测点({})状态报文数据:{}", monitor, values_map);
		}
	}
	
	/**
	 * 添加数据结束标识
	 */
	private String appendEndTag(CharSequence cs) {
		if(null == cs) {
			return endTag;
		} else {
			return cs.toString() + endTag;
		}
	}
}

class ServerDataEvent {
	CollectorReportStatistics server;
	Selector selector;
	SelectionKey key;
	byte[] data;
	
	public ServerDataEvent(CollectorReportStatistics server, Selector selector, SelectionKey key, byte[] data) {
		this.server = server;
		this.selector = selector;
		this.key = key;
		this.data = data;
	}
}
