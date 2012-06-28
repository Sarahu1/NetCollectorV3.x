/**
 * 
 */
package com.cattsoft.collect.manage.data;

import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.swing.JLabel;

import com.cattsoft.collect.manage.logging.LogManager;
import com.cattsoft.collect.manage.main.ManagerBoard;
import com.cattsoft.collect.manage.ui.MonitorModel;

/** 从服务器获取状态数据.
 * 向服务端注册,并监听服务端数据.
 * @author ChenXiaohong
 *
 */
public class StatusGrab {
	private Logger logger = LogManager.getLogger(getClass().getSimpleName());
	/*** 本地网关地址 */
	private String host = "127.0.0.1";
	/*** 数据监听端口 */
	private int port = 8089;
	/*** 数据展示面板 */
	private ManagerBoard board = null;

	private Selector selector;

	private SocketChannel channel;

	private StatusGrabRunnable runnable;
	/*** Socket 数据最大长度*/
	private long remaining = 1460;

	private MouseAdapter adapter;
	/*** 是否打印连接数据 */
	private boolean enablePrint = true;

	private String endTag = "---END---";

	public StatusGrab(String host, int port) {
		this(host, port, null);
	}

	/**
	 * @param host
	 * @param port
	 * @param board
	 */
	public StatusGrab(String host, int port, ManagerBoard board) {
		this.host = host;
		this.port = port;
		this.board = board;
		try {
			// 尝试获取缓冲区数据长度设置
			this.remaining = Long.parseLong(System.getProperty(
					"socket.remaining", String.valueOf(remaining)));
		} catch (Exception e) {
		}
		enablePrint = "true".equalsIgnoreCase(System.getProperty("enablePrint", "false"));
		// 连接
		connect();
	}

	public void setEndTag(String endTag) {
		this.endTag = endTag;
	}

	public String getEndTag() {
		return endTag;
	}

	/**
	 * 连接到数据服务中心服务器
	 */
	public void connect() {
		logger.info("连接服务器(" + host + ")..");
		showMsg("连接服务器(" + host + ")..");
		try {
			if(null == selector)
				selector = Selector.open();
			if(null == channel)
				channel = SocketChannel.open();

			channel.configureBlocking(false);
			channel.register(selector, SelectionKey.OP_CONNECT
					| SelectionKey.OP_READ);
			// 连接到服务器
			channel.socket().setSoTimeout(5000);
			channel.socket().setOOBInline(true);
			channel.connect(new InetSocketAddress(host, port));
			runnable = new StatusGrabRunnable(this, channel,
					selector);
			runnable.setRemaining(remaining);
			// 启动线程,更新数据
			new Thread(runnable, "status_grab").start();
		} catch (Exception e) {
			showMsg("服务异常");
		}
	}

	/** 数据处理
	 * @param data
	 */
	public synchronized void handler(String data) {
		if(enablePrint) {
			logger.info(data.trim());
		}
		List<String> lines = Arrays.asList(data.trim().split("\n"));
		boolean founderror = false;
		boolean updated = false;
		MonitorModel monitorModel = (MonitorModel) board.getTable().getModel();
		// 上次更新出先数据错误时,全部重新添加
		if(monitorModel.isPreError()) {
			monitorModel.clear();
		}

		// 统计监测点数量
		Set<Integer> monitors = new HashSet<Integer>();
		// 统计忙时采集数量
		int dailys = 0;
		// 统计常规采集数量
		int permanents = 0;
		// 其它类型进程数据
		int reports = 0;
		// 统计过程是否出现异常
		boolean foundStatisErr = false;
		for (int i = 1; i < lines.size(); i++) {
			String[] datas = lines.get(i).split("\t");
			// 固定长度数据
			if(datas.length == 18) {
				MonitorStatus status = new MonitorStatus(datas);
				try {
					updated = true;
					try {
						// 监测点统计
						if(status.getMonitor().matches("\\d+")) {
							monitors.add(Integer.parseInt(status.getMonitor()));
							// 类型
							if("daily".equalsIgnoreCase(status.getType())) {
								dailys++;
							} else if("permanent".equalsIgnoreCase(status.getType())){
								permanents++;
							} else if("report".equalsIgnoreCase(status.getType())){
								reports ++;
							}
						}
					} catch (Exception e) {
						foundStatisErr = true;
					}
					monitorModel.addRow(status);
				} catch (Exception e) {
					founderror = true;
					logger.severe("更新数据时出现异常!" + e.toString());
				}
			}
		}
		if(!updated) {
			monitorModel.setRowCount(0);
			showMsg("暂无数据");
		} else {
			Set<Integer> updateRow = monitorModel.getUpdateRow();
			for (int i = 0; i < monitorModel.getRowCount(); i++) {
				if(!updateRow.contains(i))
					monitorModel.removeRow(i);
			}
		}
		if(founderror) {
			showMsg("数据异常!", true);
		}
		// 设置管理界面提示信息
		try {
			if(null != board) {
				((JLabel) board.getToolBar().getComponentAtIndex(6)).setToolTipText("");
				((JLabel) board.getToolBar().getComponentAtIndex(6)).removeMouseListener(adapter);
				if(!foundStatisErr) {
					if(null != board) {
						((JLabel) board.getToolBar().getComponentAtIndex(6))
						.setText("共" + monitors.size() + "监测点数据(常规:" + permanents
								+ "/忙时:" + dailys + (reports > 0 ? ("/其它:" + reports) : "") + ")");
						StringBuffer sb = new StringBuffer();
						sb.append("监测点:").append(Arrays.toString(monitors.toArray())).append("\n");
						sb.append("常规:" + permanents).append("\n");
						sb.append("忙时:" + dailys);
						sb.append((reports > 0 ? ("\n其它:" + reports) : ""));
						final StringSelection string = new StringSelection(sb.toString());
						adapter = new MouseAdapter() {
							public void mouseClicked(MouseEvent e) {
								if(e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
									board.getToolkit().getSystemClipboard().setContents(string, string);
								}
								super.mouseClicked(e);
							}
						};
						((JLabel) board.getToolBar().getComponentAtIndex(6)).addMouseListener(adapter);
						((JLabel) board.getToolBar().getComponentAtIndex(6)).setToolTipText("双击复制统计数据");
					}
				} else {
					((JLabel) board.getToolBar().getComponentAtIndex(6)).setText("无法统计");
				}
			}
		} catch (Exception e) {
		}
		if(null != monitors) {
			monitors.clear();
		}
		permanents = 0;
		dailys = 0;
		reports = 0;
	}

	public ManagerBoard getBoard() {
		return board;
	}

	public boolean isEnablePrint() {
		return enablePrint;
	}
	
	/**
	 * 停止数据获取线程
	 */
	public void stop() {
		if(null != runnable)
			runnable.setRunning(false);
		try {
			selector.close();
		} catch (IOException e) {
		}
		try {
			channel.close();
		} catch (Exception e) {
		}
		((MonitorModel) board.getTable().getModel()).clear();
	}

	/**
	 * @param msg 向状态栏显示信息
	 */
	public void showMsg(String msg, boolean error) {
		if(null != board) {
			board.showTip(msg, error);
		}
	}

	public void showMsg(String msg) {
		showMsg(msg, false);
	}

	/**
	 * 清除表格数据
	 */
	public void clear() {
		((MonitorModel) board.getTable().getModel()).clear();
	}
}

/** 数据获取线程
 * @author ChenXiaohong
 *
 */
class StatusGrabRunnable implements Runnable {
	private Logger logger = LogManager.getLogger(getClass().getSimpleName());
	/*** 是否运行 */
	private boolean running = true;
	private StatusGrab grab;
	/*** 事件选择器 */
	private Selector selector;
	/*** 连接通道 */
	private SocketChannel channel;
	/*** 最大数据长度 */
//	private long remaining;
	private CharsetDecoder decoder;
	/*** 数据匹配正则 */
	Pattern lastPattern = Pattern.compile("^.*?[\t|\n]$", Pattern.DOTALL);

	public void setRunning(boolean running) {
		this.running = running;
		try {
			if(this.running)
				channel.write(ByteBuffer.wrap("disconnect".getBytes()));
		} catch (IOException e) {
		}
	}

	public boolean isRunning() {
		return running;
	}

	public void setRemaining(long remaining) {
//		this.remaining = remaining;
	}

	/**
	 * @param grab
	 * @param channel
	 * @param selector
	 * @param buffer
	 */
	public StatusGrabRunnable(StatusGrab grab, SocketChannel channel, Selector selector) {
		this.grab = grab;
		this.channel = channel;
		this.selector = selector;
		this.decoder = Charset.forName("UTF-8").newDecoder();
	}

	public void run() {
		// 接收的数据,由于数据量太大导致数据分段时
		// 对接收数据进行拼接
		StringBuffer status = new StringBuffer();
		long remaining = -1;
		while (running) {
			try {
				// 等待服务器激活事件
				selector.select();
				Iterator<SelectionKey> keyIterator = selector.selectedKeys()
						.iterator();
				while (keyIterator.hasNext()) {
					SelectionKey key = keyIterator.next();
					keyIterator.remove();
					// 连接事件
					if (key.isConnectable()) {
						SocketChannel socketChannel = (SocketChannel) key
								.channel();
						if (socketChannel.isConnectionPending())
							socketChannel.finishConnect();
						// 向服务器发送标识,进行注册
						socketChannel.write(ByteBuffer.wrap("register".getBytes()));
						socketChannel.register(selector, SelectionKey.OP_READ);
						grab.showMsg("已连接");
						logger.info("数据服务已连接.");
					}
					// 接收数据
					if (key.isReadable()) {
						SocketChannel channel = (SocketChannel) key
								.channel();
						/*** 数据缓冲 */
						if (!channel.isConnected()) {
							channel.close();
							grab.showMsg("连接已丢失");
							setRunning(false);
						}
						ByteBuffer buffer = ByteBuffer.allocate(8192);
						// 准备接受数据
						buffer.clear();
						// 读取
						int numRead = channel.read(buffer);
						buffer.flip();
						// 如果读取完毕,则关掉socketChannel
						if (numRead == -1) {
							channel.close();
							key.cancel();
						}
						remaining = buffer.remaining();
						if(grab.isEnablePrint())
							logger.info("elements between the current position and the limit:" + remaining);
						String pair = "";
						try {
							pair = new String(decoder.decode(buffer).array());
						} catch (Exception e) {
							try {
								pair = new String(buffer.array(), Charset.forName("UTF-8"));
							} catch (Exception e2) {
								// 直接添加,可能出现乱码问题
								pair = new String(buffer.array());
							}
						}
						// 处理最后字符
						if(!lastPattern.matcher(pair).matches()) {
							pair = pair.trim();
						}
						// 添加
						status.append(pair);
					}
				}
				// 判断数据是否已到达结束标记
				if(status.toString().trim().endsWith(grab.getEndTag())) {
					// 删除数据结束标记
					status.setLength(status.toString().trim().length() - grab.getEndTag().length());
					// 处理数据
					grab.handler(status.toString());
					status.setLength(0);
				}
				// 处理数据,长度:1460 部分网络长度为: 1440
				// 目前的处理方式是数据长度小于1460是表示为数据读取完成
				// 后期如果有更好的方法,需要对此处进行修复
				// 避免接收的所有数据正好长度是1460的问题
				// 目前未考虑该情况
				// 结合判断数据最后一行是否通过\t进行分割后长度是否符合
				// if(remaining < this.remaining) {
				//	grab.handler(status.toString());
				//	status.setLength(0);
				// }
			} catch (ConnectException e) {
				grab.showMsg("无服务");
				logger.severe("远程数据服务无法连接!" + e.toString());
				setRunning(false);
				try {
					// 提示设置服务
					grab.getBoard().showSettingDialog();
				} catch (Exception e2) {
				}
			} catch (Exception e) {
				grab.showMsg("服务断开");
				if(running) {
					logger.severe("远程数据服务出现异常!" + e.toString());
				}
				setRunning(false);
			}
		}
		logger.info("数据获取线程已退出");
	}
}
