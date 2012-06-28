/**
 * 
 */
package com.cattsoft.collect.manage.data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 监测点状态.
 * 
 * @author ChenXiaohong
 * 
 */
public class MonitorStatus implements Serializable {
	private static final long serialVersionUID = 1L;
	// MONI TYPE USER PID %CPU %MEM VSZ RSS STAT VERSION UPDATE DATE
	/*** 监测点 */
	private String monitor;
	/*** 名称 */
	private String name;
	/*** 监测点IP地址 */
	private String address;
	/*** 客户端程序主目录 */
	private String catalog;
	/*** 运行类型 */
	private String type;
	/*** 运行账户 */
	private String user;
	/*** 进程号 */
	private String pid;
	/*** 进程cpu占用百分比 */
	private String cpu;
	/*** 占用内在的百分比 */
	private String mem;
	/*** 进程虚拟大小 */
	private String vsz;
	/*** 驻留中页的数量 */
	private String rss;
	/*** 进程状态 */
	private String stat;
	/*** 启动进程的时间 */
	private String start;
	/*** 进程消耗CPU时间 */
	private String time;
	/*** 程序版本 */
	private String version;
	/*** 状态更新时间(分/秒) */
	private String update;
	/*** 命令状态 */
	private Map<String, String> command;
	/*** 状态上传时间 */
	private String date;

	public MonitorStatus() {
		//
	}

	/**
	 * @param values
	 *            MONI ADDRESS TYPE USER PID %CPU %MEM VSZ RSS STAT START TIME
	 *            VERSION UPDATE DATE CATALOG
	 */
	public MonitorStatus(String[] values) {
		this.monitor = trim(values[0]);
		this.name = trim(values[1]);
		this.address = trim(values[2]);
		this.type = trim(values[3]);
		this.user = trim(values[4]);
		this.pid = trim(values[5]);
		this.cpu = trim(values[6]);
		this.mem = trim(values[7]);
		this.vsz = trim(values[8]);
		this.rss = trim(values[9]);
		this.stat = trim(values[10]);
		this.start = trim(values[11]);
		this.time = trim(values[12]);
		this.version = trim(values[13]);
		this.update = trim(values[14]);
		this.command = converCommand(values[15]);
		this.date = trim(values[16]);
		this.catalog = trim(values[17]);
	}
	
	private String trim(String value) {
		return (null == value) ? "" : value.trim();
	}

	public String getMonitor() {
		return monitor;
	}

	/**
	 * @param monitor 监测点
	 */
	public void setMonitor(String monitor) {
		this.monitor = monitor;
	}

	/**
	 * @return 类型
	 */
	public String getType() {
		return type;
	}

	/**
	 * @param type 类型
	 */
	public void setType(String type) {
		this.type = type;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getPid() {
		return pid;
	}

	public void setPid(String pid) {
		this.pid = pid;
	}

	public String getCpu() {
		return cpu;
	}

	public void setCpu(String cpu) {
		this.cpu = cpu;
	}

	public String getMem() {
		return mem;
	}

	public void setMem(String mem) {
		this.mem = mem;
	}

	public String getVsz() {
		return vsz;
	}

	public void setVsz(String vsz) {
		this.vsz = vsz;
	}

	public String getRss() {
		return rss;
	}

	public void setRss(String rss) {
		this.rss = rss;
	}

	public String getStat() {
		return stat;
	}

	public void setStat(String stat) {
		this.stat = stat;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getUpdate() {
		return update;
	}

	public void setUpdate(String update) {
		this.update = update;
	}

	public String getDate() {
		return date;
	}

	public void setDate(String date) {
		this.date = date;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getCatalog() {
		return catalog;
	}

	public void setCatalog(String catalog) {
		this.catalog = catalog;
	}

	public String getStart() {
		return start;
	}

	public void setStart(String start) {
		this.start = start;
	}

	public String getTime() {
		return time;
	}

	public void setTime(String time) {
		this.time = time;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getCommand() {
		try {
			StringBuffer sb = new StringBuffer();
			for (Map.Entry<String, String> entry : command.entrySet()) {
				sb.append(entry.getKey()).append("=[");
				String[] values = entry.getValue().split(",");
				for (String value : values) {
					String[] v = value.split(":");
					sb.append(v[0].replace("command_", "")).append(":")
							.append(v[1]).append(",");
				}
				sb.setLength(sb.length() - 1);
				sb.append("]").append("\n");
			}
			return sb.toString();
		} catch (Exception e) {
		}
		return "无法获取";
	}

	/**
	 * @return 命令状态Map数据
	 */
	public Map<String, Map<String, String>> getCommandMap() {
		Map<String, Map<String, String>> command_map = new HashMap<String, Map<String, String>>();
		try {
			for (Map.Entry<String, String> entry : command.entrySet()) {
				String cmd = entry.getKey();
				Map<String, String> value_map = new HashMap<String, String>();

				String[] values = entry.getValue().split(",");
				for (String value : values) {
					String[] v = value.split(":");
					value_map.put(v[0].replace("command_", ""), v[1]);
				}
				command_map.put(cmd, value_map);
			}
		} catch (Exception e) {
			e.printStackTrace();
			// 出现异常
		}
		return command_map;
	}

	public void setCommand(String command) {
		this.command = converCommand(command);
	}

	/**
	 * 字符串转换为Map
	 * 
	 * @param command
	 *            命令执行状态
	 * @return Map
	 */
	private Map<String, String> converCommand(String command) {
		Map<String, String> map = new HashMap<String, String>();
		if (null != command && !"".equals(command.trim())) {
			String[] nameValuePairs = command.split("&");
			for (String nameValuePair : nameValuePairs) {
				String[] nameValue = nameValuePair.split("=");
				map.put(nameValue[0], nameValue.length > 1 ? nameValue[1] : "");
			}
		}
		return map;
	}
}
