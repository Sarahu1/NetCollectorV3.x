/**
 * 
 */
package test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Iterator;

/**
 * @author 陈小鸿
 * @author chenxiaohong@mail.com
 *
 */
public class TestMonitorManager {
	private CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
	private Selector selector = null;
	private SocketChannel channel = null;

	/**
	 * 
	 */
	private void start() {
		try {
			selector = Selector.open();
			channel = SocketChannel.open();

			channel.configureBlocking(false);
			channel.register(selector, SelectionKey.OP_CONNECT
					| SelectionKey.OP_READ | SelectionKey.OP_WRITE);
			// 连接到服务器
			channel.socket().setSoTimeout(5000);
			channel.socket().setOOBInline(true);
			System.out.println("正在连接服务器");
			channel.connect(new InetSocketAddress("202.108.49.59", 8089));
		} catch (IOException e) {
			System.err.println("连接失败");
			e.printStackTrace();
			return;
		}
		String response = null;
		boolean canceled = false;
		while(!canceled) {
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
						String app_key = "ad:c3:f2:75:44:3c:3a:32:50:5d:b2:bd:ea:65:af:be";
						socketChannel.write(ByteBuffer.wrap(("monitor_mgr,"+app_key+",125.46.63.12,collect,Bb$4.b,GBK,DTTngoss9ol.0p;|,cd /collect/NetCollectorV3.0,sh shell.sh status").getBytes()));
						socketChannel.register(selector, SelectionKey.OP_READ);
						
						System.out.println("正在执行命令..");
					} else if(key.isReadable()) {
						SocketChannel channel = (SocketChannel) key
								.channel();
						if (!channel.isConnected()) {
							channel.close();
							System.err.println("断开");
							canceled = true;
							break;
						}
						/*** 数据缓冲 */
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
						response = new String(decoder.decode(buffer).array()).replace("\0", "").trim();
						canceled = true;
						break;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				canceled = true;
				break;
			}
		}
		try {
			selector.close();
			channel.close();
		} catch (Exception e) {
		}
		
		System.out.print(response);
	}

	public static void main(String[] args) {
		TestMonitorManager tmm = new TestMonitorManager();
		tmm.start();
	}
}





