/**
 * FTP 文件发布.
 */
package com.cattsoft.collect.io.net.ftp.client;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.cattsoft.collect.io.net.ftp.FTP;
import com.cattsoft.collect.io.utils.Logger;

/** 通过FTP上传功能向批量服务器发布文件.
 * @author ChenXiaohong
 *
 */
public class FTPFilePublish implements Runnable {
	private Logger logger = Logger.getLogger(FTPFilePublish.class);
	/*** FTP 客户端 */
	private FTP ftp = null;
	/*** 待上传文件列表 */
	private List<File> files;
	/*** 服务器信息(地址,端口,用户名,密码,目录) */
	private List<String[]> servers;
	/*** 上传失败列表(地址, 失败描述) */
	private Map<String, String> failure = new HashMap<String, String>();
	private final SimpleDateFormat date_sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); 
	/*** 换行符 */
	private static final String LINE_SEPARATOR = System.getProperty("line.separator", "\n");

	/** 构造
	 * @param ftp FTP 客户端
	 * @param files 文件列表
	 * @param servers 服务器信息
	 */
	public FTPFilePublish(List<File> files, List<String[]> servers) {
		this.files = files;
		this.servers = servers;
	}

	@Override
	public void run() {
		// 传输失败次数记录(主机地址, 次数)
		Map<String, Integer> frequency = new HashMap<String, Integer>();
		if(servers.isEmpty())
			logger.log(Logger.INFO, "上传服务器列表为空,数据取消上传");
		while(!servers.isEmpty()) {
			String message = "";
			Iterator<String[]> iterator = servers.iterator();
			while(iterator.hasNext()) {
				String[] server = iterator.next();
				if(server.length == 0) {
					iterator.remove();
					continue;
				}
				try {
					int port = Integer.parseInt(server[1]);
					// 自动判断传输通道类型
					if(port == 22) {
						// SFTP传输
						ftp = new SFTPClient(server[0], port, server[2], server[3], server[4]);
					} else {
						// 使用普通FTP传输
						ftp = new NFTPClient(server[0], port, server[2], server[3], server[4]);
					}
					logger.log(Logger.INFO, "正在连接服务器:"+ server[0] +", 端口:" + port);
					ftp.connect(server[0], port);
					ftp.login(server[2], server[3]);
					if(ftp.isLogged()) {
						logger.log(Logger.INFO, "已成功连接到服务器");
						logger.log(Logger.INFO, "正在上传文件...");
						// 循环上传所有文件
						for (final File file : files) {
							if(!file.exists()) {
								logger.log(Logger.INFO, "文件("+ file.getAbsolutePath() +")不存在, 未进行上传");
								continue;
							}
							ftp.upload(server[4], file, new FtpTransferProcessListener() {
								double preProcess = 0;
								public void process(long step) {
									double round_process = step;
									if (((round_process - preProcess) >= 10)
											&& (round_process % 10 == 0)) {
										preProcess = round_process;
										logger.log(Logger.DEBUG, "文件("+ file.getPath() +")上传进度:" + round_process);
									}
								}
								@Override
								public void complete() {
									logger.log(Logger.DEBUG, "文件("+ file.getPath() +")传输完成");
									super.complete();
								}
							});
						}
						logger.log(Logger.INFO, "文件上传完成");
						iterator.remove();
					}
				} catch (Exception e) {
					StringWriter writer = new StringWriter();
					// 打印异常堆栈
					PrintWriter print = new PrintWriter(writer);
					e.printStackTrace(print);
					writer.flush();
					message = writer.toString();
					try {
						print.close();
						writer.close();
					} catch (Exception e2) {
					}

					logger.log(Logger.ERROR, "上传文件时出现异常!" + e.getMessage(), e);
					// 上传错误次数控制, 增加
					// 超出指定次数后抛弃, 并发送邮件告知
				} finally {
					// 断开连接
					ftp.disconnect(true);
				}
				frequency.put(ftp.getHost(), (null == frequency.get(ftp.getHost()) ? 1 : (1+ frequency.get(ftp.getHost()))));
				// 判断错误次数, 是否超出最大次数
				if(frequency.get(ftp.getHost()) > Integer.parseInt(System.getProperty("export.ftp.upload.maxtry", "10"))) {
					iterator.remove();	//移除, 取消上传
					logger.log(Logger.ERROR, "无法上传文件到服务器("+ ftp.getHost() +"), 已取消上传!");
					StringBuffer content =  new StringBuffer();
					content.append("异常时间:").append(date_sdf.format(System.currentTimeMillis())).append(LINE_SEPARATOR);
					content.append("错误描述:").append("无法上传文件到服务器("+ ftp.getHost() +")目录("+ server[4] +"), 请检查网络或服务器连接是否正常!").append(LINE_SEPARATOR);
					content.append("异常堆栈:").append(LINE_SEPARATOR).append(message);
					failure.put(ftp.getHost(), content.toString());
					// 发送通知
					notification("无法上传文件到服务器("+ ftp.getHost() +"), 请检查网络或服务器是否正常!");
				}
			}
		}
	}

	/**
	 * @return the failure
	 */
	public Map<String, String> getFailure() {
		return failure;
	}

	/**
	 * 发送提醒
	 */
	private void notification(String message) {
		// 
	}
	
	
	public static void main(String[] args) {
		List<File> files = new ArrayList<File>();
		files.add(new File("data/export/colud/all_ip.txt"));
		List<String[]> servers = new ArrayList<String[]>();
		servers.add(new String[]{"192.168.1.3", "22", "ngoss", "9ol.0p;/", "/data/collectdata3/phone/"});
		servers.add(new String[]{"192.168.1.15", "22", "ngoss", "9ol.0p;/", "/data/collectdata3/data/"});
		
		FTPFilePublish publish = new FTPFilePublish(files, servers);
		publish.run();
	}
}
