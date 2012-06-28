/**
 * 
 */
package com.cattsoft.collect.net.report;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;

import org.slf4j.LoggerFactory;

/**
 * 采集程序状态汇总统计服务.
 * 
 * @author ChenXiaohong
 */
public class CollectorReportStatistics  implements Runnable {
	private static final org.slf4j.Logger logger = LoggerFactory.getLogger("report_statistics");
	/*** 地址*/
	private String host;
	/*** 服务端口 */
	private int port = 8089;
	/*** 运行状态 */
	private boolean running = false;
	// The channel on which we'll accept connections
	private ServerSocketChannel serverChannel;
	// The selector we'll be monitoring
	private Selector selector;
	// The buffer into which we'll read data when it's available
	private ByteBuffer buffer = ByteBuffer.allocate(8192);
	private ReportStatisticsWorker worker;
	
	/**
	 * @param port 监听端口
	 * @throws IOException 
	 */
	public CollectorReportStatistics(ReportStatisticsWorker worker, String host, int port) throws IOException {
		this.host = host;
		this.port = port;
		this.worker = worker;
		this.running = true;
		logger.info("正在启动服务..");
		
		// Create a new selector
		selector = SelectorProvider.provider().openSelector();

		// Create a new non-blocking server socket channel
		this.serverChannel = ServerSocketChannel.open();
		serverChannel.configureBlocking(false);

		// Bind the server socket to the specified address and port
		InetSocketAddress isa = new InetSocketAddress(this.host, this.port);
		serverChannel.socket().bind(isa);
		
		logger.info("当前服务地址:" + serverChannel.socket().getInetAddress().getHostAddress() + 
				",端口:" + serverChannel.socket().getLocalPort());

		// Register the server socket channel, indicating an interest in 
		// accepting new connections
		serverChannel.register(selector, SelectionKey.OP_ACCEPT);
	}

	/** 服务器处理状态.
	 * @param running 状态
	 */
	public void setRunning(boolean running) {
		this.running = running;
	}
	
	public void setHost(String host) {
		this.host = host;
	}

	/**
	 * @param port 监听端口
	 */
	public void setPort(int port) {
		this.port = port;
	}
	
	/**
	 * 服务启动入口.
	 * 
	 * @throws UnknownHostException
	 */
	public void run() {
		try {
			while (running) {
				try {
					// Wait for an event one of the registered channels
					selector.select();

					// Iterate over the set of keys for which events are available
					Iterator<SelectionKey> selectedKeys = this.selector.selectedKeys().iterator();
					while (selectedKeys.hasNext()) {
						SelectionKey key = selectedKeys.next();
						selectedKeys.remove();
						if (!key.isValid()) {
							continue;
						}
						// Check what event is available and deal with it
						if (key.isAcceptable()) {
							this.accept(key);
						} else if (key.isReadable()) {
							this.read(key);
						} else if (key.isWritable()) {
							this.write(key);
						}
					}
				} catch (Exception e) {
					//
				}
			}
			logger.info("服务已退出.");
		} catch (Exception e) {
			logger.error("服务启动失败!{}", e.getMessage());
		}
	}
	
	/** 向客户端写入数据.
	 * @param key 客户端连接
	 * @throws IOException
	 */
	private void write(SelectionKey key) throws IOException {
		// 写事件
	}
	
	/** 读取客户端数据.
	 * @param key 客户端连接
	 * @throws IOException
	 */
	private void read(SelectionKey key) throws IOException {
		SocketChannel socketChannel = (SocketChannel) key.channel();
		// Clear out our read buffer so it's ready for new data
		buffer.clear();
		// 尝试读取通道数据
		int numRead;
		try {
			numRead = socketChannel.read(buffer);
		} catch (IOException e) {
			// 远程强行关闭了连接，取消键并关闭通道。
			key.cancel();
			socketChannel.close();
			return;
		}
		if (numRead == -1) {
			// 远程连接关闭时,取消通道
			key.channel().close();
			key.cancel();
			return;
		}
		buffer.flip();
	    // 工作线程处理数据
	     this.worker.process(this, this.selector, key, this.buffer.array(), numRead);
	}

	/** 接收处理客户端请求.
	 * @param key
	 * @throws IOException 
	 */
	private void accept(SelectionKey key) throws IOException {
//		logger.debug("accpet connection..");
		// 对于接受被挂起的通道必须有一个服务器套接字通道。
		ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
		// 接受连接，并设置为无阻塞
		SocketChannel socketChannel = serverSocketChannel.accept();
		socketChannel.configureBlocking(false);
		// 注册新的SocketChannel
		// 得到通知时，等待读取数据
		socketChannel.register(this.selector, SelectionKey.OP_READ);
	}

	/**
	 * main 方法
	 * 
	 * @param args
	 * @throws UnknownHostException
	 */
	public static void main(String[] args) {
		try {
			String host = System.getProperty("server.host", InetAddress.getLocalHost().getHostAddress());
			int port = Integer.parseInt(System.getProperty("server.port", "8089"));
			for (int i = 0; i < args.length; i++) {
				if(args[i].equals("-host"))
					host = args[i+1];
				if(args[i].equals("-port"))
					port = Integer.parseInt(args[i+1]);
			}
			ReportStatisticsWorker worker = new ReportStatisticsWorker();
			new Thread(worker, "report_data_worker").start();
			new Thread(new CollectorReportStatistics(worker, host, port), "socket_server").start();
		} catch (Exception e) {
			logger.error("数据处理线程启动失败!请检查参数设置.{}", e.toString());
			System.exit(-1);
		}
	}
}